#!/usr/bin/env python3
"""Inspect the pinned Mi Remote local database as data; emit metadata only.

Original MIT-licensed interoperability research helper. It does not execute APK,
DEX, native libraries, SQL scripts, or embedded Lua. No vendor code is included.
"""
from __future__ import annotations

import argparse
import base64
import ctypes as c
import ctypes.util
import hashlib
import io
import json
from pathlib import Path
import re
import sqlite3
import zipfile
import zlib

from Crypto.Cipher import AES

APK_BYTES = 34_320_753
APK_SHA256 = "4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73"
RAR_MEMBER = "res/Jy4"
RAR_BYTES = 3_438_991
RAR_SHA256 = "b9d940a132ab958065d5d244e0132ef4c2d4c8976383df2651cb247da5c15947"
DB_BYTES = 5_878_784
DB_SHA256 = "727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa"
# Publicly documented Mi JSON format key; this is not an account credential.
FORMAT_KEY = b"fd7e915003168929c1a9b0ec32a60788"
MAX_JSON_RECORD = 20_000_000
MAX_JSON_TOTAL = 150_000_000
IDENTITY_TERMS = re.compile(r"astro|n[\s_-]*j[\s_-]*o[\s_-]*i|ultra[\s_-]*box|ulti[\s_-]*box", re.I)

# Expected schema and row counts from the checksum-pinned, bundled database.
# The decoded contents are deliberately not included in this repository.
TABLES = {
    "devicebrands": (176, "ID BRAND_ID DEVICE_ID CONTENT", "CONTENT"),
    "mi_ir_tree": (430, "ID BRAND_ID DEVICE_ID SP_ID ENTRYS NODES ROOT_INDEX SECRET_KEY VERSION STATUS COUNTRY", "NODES"),
    "mi_remote_ircode": (1923, "ID BRAND_ID DEVICE_ID FREQUENCY SP_ID IR_ZIP_KEY INDEXED VERSION", ""),
    "kk_remote_ircode": (1053, "ID FREQUENCY SP_ID DEVICE_ID BRAND_ID IR_ZIP_KEY INDEXED EXT_FLAG VERSION", ""),
    "kk_remote_ircode_src": (1747, "ID IR_ZIP_KEY", "IR_ZIP_KEY"),
    "device": (14, "ID CONTENT", "CONTENT"),
    "brand": (125, "ID CONTENT", "CONTENT"),
    "sp": (340, "ID CITY_ID SP_ID TYPE NAME", ""),
    "xm_sp": (43, "ID CITY_ID SP_ID TYPE NAME", ""),
    "city": (294, "ID NAME PROVINCE_ID PROVINCE_NAME", ""),
    "xm_city": (1, "ID NAME PROVINCE_ID PROVINCE_NAME", ""),
    "lineup": (856, "ID AREA NAME SP_ID S_TPBRAND STATUS", ""),
    "xm_lineup": (47, "ID AREA NAME SP_ID S_TPBRAND STATUS", ""),
    "kk_match": (1056, "ID FREQUENCY SP_ID DEVICE_ID BRAND_ID KEY EXT_FLAG LOCALES", "KEY"),
    "xm_match": (34, "ID FREQUENCY SP_ID DEVICE_ID BRAND_ID KEY EXT_FLAG LOCALES", "KEY"),
    "xm_remote_ircode": (34, "ID FREQUENCY SP_ID DEVICE_ID BRAND_ID IR_ZIP_KEY INDEXED EXT_FLAG VERSION", "IR_ZIP_KEY"),
    "mi_ir_merge": (477, "ID VENDOR MI_ID BRAND_ID DEVICE_ID SP_ID LOCALES", ""),
    "country": (4, "ID COUNTRY LANGUAGE LOCALE_DEVICE", ""),
    "version": (1, "VERSION", ""),
    "sqlite_sequence": (6, "name seq", ""),
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(data: bytes, length: int, digest: str, label: str) -> None:
    if len(data) != length or sha256(data) != digest:
        raise ValueError(f"{label} length/SHA-256 does not match the pinned research input")


def bounded_read(path: Path, limit: int) -> bytes:
    if path.stat().st_size > limit:
        raise ValueError("APK exceeds the pinned input size")
    with path.open("rb") as stream:
        result = stream.read(limit + 1)
    if len(result) > limit:
        raise ValueError("APK exceeded the read bound")
    return result


def extract_database(rar: bytes) -> bytes:
    """Extract one exact regular file through system libarchive, in memory."""
    library = ctypes.util.find_library("archive")
    if not library:
        raise RuntimeError("System libarchive is required (Debian/Ubuntu: libarchive13 or libarchive13t64)")
    lib = c.CDLL(library)

    def function(name, result_type, arguments):
        result = getattr(lib, name)
        result.restype, result.argtypes = result_type, arguments
        return result

    new = function("archive_read_new", c.c_void_p, [])
    support = function("archive_read_support_format_rar", c.c_int, [c.c_void_p])
    open_memory = function("archive_read_open_memory", c.c_int, [c.c_void_p, c.c_void_p, c.c_size_t])
    header = function("archive_read_next_header", c.c_int, [c.c_void_p, c.POINTER(c.c_void_p)])
    pathname = function("archive_entry_pathname", c.c_char_p, [c.c_void_p])
    entry_size = function("archive_entry_size", c.c_longlong, [c.c_void_p])
    file_type = function("archive_entry_filetype", c.c_uint, [c.c_void_p])
    hardlink = function("archive_entry_hardlink", c.c_char_p, [c.c_void_p])
    symlink = function("archive_entry_symlink", c.c_char_p, [c.c_void_p])
    read = function("archive_read_data", c.c_ssize_t, [c.c_void_p, c.c_void_p, c.c_size_t])
    error = function("archive_error_string", c.c_char_p, [c.c_void_p])
    free = function("archive_read_free", c.c_int, [c.c_void_p])
    archive = new()
    if not archive:
        raise RuntimeError("libarchive allocation failed")
    backing = c.create_string_buffer(rar)
    try:
        if support(archive) != 0 or open_memory(archive, backing, len(rar)) != 0:
            raise ValueError("Could not open pinned RAR: " + repr(error(archive)))
        entry = c.c_void_p()
        if header(archive, c.byref(entry)) != 0:
            raise ValueError("Missing expected RAR entry")
        if (pathname(entry) != b"irlocaldata.db" or entry_size(entry) != DB_BYTES
                or file_type(entry) != 0o100000 or hardlink(entry) or symlink(entry)):
            raise ValueError("Unexpected RAR entry name, type, link, or size")
        result = bytearray()
        chunk = c.create_string_buffer(65536)
        while True:
            length = read(archive, chunk, min(len(chunk), DB_BYTES - len(result) + 1))
            if length < 0:
                raise ValueError("RAR decoding/CRC failed: " + repr(error(archive)))
            if not length:
                break
            result.extend(chunk.raw[:length])
            if len(result) > DB_BYTES:
                raise ValueError("RAR output exceeds the expected database size")
        if header(archive, c.byref(entry)) != 1:
            raise ValueError("RAR contains extra entries or a failed final checksum")
        verify(result, DB_BYTES, DB_SHA256, "SQLite database")
        return bytes(result)
    finally:
        free(archive)


def decode_json(value: str, limit: int = MAX_JSON_RECORD):
    """Decode the observed static JSON format without executing its contents."""
    if len(value) > MAX_JSON_RECORD:
        raise ValueError("Encrypted record exceeds the input bound")
    # Android Base64.DEFAULT accepts ASCII whitespace, not arbitrary junk.
    encoded = "".join(value.split())
    encrypted = base64.b64decode(encoded, validate=True)
    if not encrypted or len(encrypted) % AES.block_size:
        raise ValueError("Encrypted record is not a nonempty AES block sequence")
    compressed = AES.new(FORMAT_KEY, AES.MODE_ECB).decrypt(encrypted)
    decompressor = zlib.decompressobj(16 + zlib.MAX_WBITS)
    raw = decompressor.decompress(compressed, limit + 1)
    if len(raw) > limit or not decompressor.eof:
        raise ValueError("Record is truncated or exceeds the decompression bound")
    # The observed format pads gzip bytes to a block boundary with ASCII spaces.
    if decompressor.unused_data.strip(b" "):
        raise ValueError("Unexpected bytes after the gzip record")
    decoded = json.loads(raw)
    if not isinstance(decoded, (dict, list)):
        raise ValueError("Expected a JSON object or array")
    return decoded


def inspect_database(data: bytes) -> dict:
    verify(data, DB_BYTES, DB_SHA256, "SQLite database")
    db = sqlite3.connect(":memory:")
    try:
        db.deserialize(data)
        db.execute("PRAGMA trusted_schema=OFF")
        db.execute("PRAGMA query_only=ON")
        objects = db.execute("SELECT name,type FROM sqlite_master").fetchall()
        tables = {name for name, kind in objects if kind == "table"}
        if tables != set(TABLES) or any(kind in ("view", "trigger") for _, kind in objects):
            raise ValueError("Unexpected SQLite tables, views, or triggers")
        if db.execute("PRAGMA quick_check").fetchall() != [("ok",)]:
            raise ValueError("SQLite integrity check failed")
        db.row_factory = sqlite3.Row
        report = {}
        total_json = 0
        total_decoded = 0
        total_matches = 0
        known_brands = set()
        associations = {}
        for table, (expected_count, expected_columns, encrypted_column) in TABLES.items():
            columns = [row["name"] for row in db.execute(f'PRAGMA table_info("{table}")')]
            if columns != expected_columns.split():
                raise ValueError(f"Unexpected columns in {table}")
            count = decoded = matches = 0
            brand_ids = set()
            for row in db.execute(f'SELECT * FROM "{table}"'):
                record = dict(row)
                count += 1
                if encrypted_column and record.get(encrypted_column):
                    record[encrypted_column] = decode_json(record[encrypted_column])
                    decoded += 1
                serialized = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
                total_json += len(serialized.encode("utf-8"))
                if total_json > MAX_JSON_TOTAL:
                    raise ValueError("Total decoded JSON exceeds the bound")
                matches += len(IDENTITY_TERMS.findall(serialized))
                if table == "brand":
                    known_brands.add(str(record["ID"]))
                if record.get("BRAND_ID"):
                    brand_ids.add(str(record["BRAND_ID"]))
            if count != expected_count:
                raise ValueError(f"Unexpected row count in {table}")
            total_decoded += decoded
            total_matches += matches
            report[table] = {"rows": count, "columns": columns,
                             "encryptedJsonColumn": encrypted_column or None,
                             "decodedJsonRecords": decoded, "identityTermMatches": matches}
            if brand_ids:
                associations[table] = brand_ids
        return {
            "applicationTableCount": len(TABLES) - 1, "internalTableCount": 1,
            "tables": report, "decodedJsonRecords": total_decoded,
            "decodeErrors": 0,
            "identitySearch": {"terms": ["Astro", "NJOI", "Ultra Box", "Ulti Box"],
                               "matchingOccurrences": total_matches,
                               "scope": "All plain rows and decoded outer JSON columns; nested pulse blobs remain encoded.",
                               "meaning": "No attributable named Astro profile was recovered; this does not exclude unnamed compatible waveforms or online profiles."},
            "brandAssociationCoverage": {
                table: {"distinctNonemptyBrandIds": len(ids),
                        "idsWithoutBundledBrandName": len(ids - known_brands)}
                for table, ids in associations.items()
            },
        }
    finally:
        db.close()


def inspect_apk(apk: Path) -> dict:
    data = bounded_read(apk, APK_BYTES)
    verify(data, APK_BYTES, APK_SHA256, "APK")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        if len(entries) != 4874 or sum(entry.filename == RAR_MEMBER for entry in entries) != 1:
            raise ValueError("Unexpected APK entry count or duplicate database resource")
        entry = archive.getinfo(RAR_MEMBER)
        if entry.file_size != RAR_BYTES or entry.flag_bits & 1:
            raise ValueError("Unexpected database ZIP member size/encryption")
        rar = archive.read(entry)  # ZipFile checks CRC; exact APK digest is already pinned.
    verify(rar, RAR_BYTES, RAR_SHA256, "RAR resource")
    database = extract_database(rar)
    return {
        "schemaVersion": 1, "staticOnly": True, "emitsVendorData": False,
        "input": {"package": "com.duokan.phone.remotecontroller", "versionName": "7.2.4M",
                  "versionCode": 6500, "bytes": APK_BYTES, "sha256": APK_SHA256,
                  "versionEvidence": "Independently inspected Android manifest; this helper verifies its exact APK hash."},
        "archiveResource": {"apkPath": RAR_MEMBER, "recoveredResourceName": "raw/irlocaldata",
                            "format": "RAR4", "bytes": RAR_BYTES, "sha256": RAR_SHA256},
        "database": {"archiveEntry": "irlocaldata.db", "format": "SQLite3",
                     "bytes": DB_BYTES, "sha256": DB_SHA256, **inspect_database(database)},
        "limitations": ["No APK, DEX, native library, embedded script, or SQL script was executed.",
                        "Native DAT formats and server-side data are outside this helper's scope.",
                        "No compatibility with physical appliances was tested.",
                        "Vendor data ownership and redistribution permission are not established; no extracted records are included in the app or report."],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("summary", type=Path, help="New metadata-only JSON report; existing files are not overwritten")
    args = parser.parse_args()
    if args.summary.exists():
        parser.error("Summary path must not already exist")
    summary = inspect_apk(args.apk)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    with args.summary.open("x", encoding="utf-8") as stream:
        json.dump(summary, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(f"Verified pinned APK and local database; decoded {summary['database']['decodedJsonRecords']} JSON records.")
    print(f"Identity term matches: {summary['database']['identitySearch']['matchingOccurrences']}. Metadata saved to {args.summary}.")


if __name__ == "__main__":
    main()
