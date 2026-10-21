#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Independently convert raw TV/AC records from the exact uploaded Mi APK.

The APK is read as data. No DEX, native library, SQL script, AC template or Lua
program is executed. Vendor response data retains its own attribution/rights.
"""
from __future__ import annotations

import argparse
from collections import Counter
import io
import json
from pathlib import Path
import re
import sqlite3
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
sys.path.insert(0, str(REPOSITORY / "tools/apk_inspection"))
import inspect_local_ir as local
from extract_mi724_archive import extract_verified_archive

DEFAULT_ARCHIVE = REPOSITORY / "downloads/com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22(arm64-v8a)(nodpi)_apkmirror.com.7z"
DEFAULT_OUTPUT = REPOSITORY / "app/src/main/assets/ir/mi_apk"
SOURCE_REPOSITORY = "https://github.com/junyongtio-lab/test-2"
SOURCE_URL = SOURCE_REPOSITORY + "/blob/f91a98e76015ad3011e3e4a674ffde229ff10bf8/downloads/" + DEFAULT_ARCHIVE.name
TABLES = ("mi_remote_ircode", "kk_remote_ircode", "xm_remote_ircode")
CATEGORIES = {1: "tv", 3: "air_conditioner"}
ID_PATTERN = re.compile(r"[A-Za-z0-9_]{1,100}", re.ASCII)
LABELS = {"power": "Power", "mute": "Mute", "vol+": "Volume +", "vol-": "Volume −",
          "ch+": "Channel +", "ch-": "Channel −", "ok": "OK",
          "temperature_up": "Temperature +", "temperature_down": "Temperature −",
          "fanspeed": "Fan speed", "FF": "Fast forward", "REW": "Rewind"}


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def open_database(apk: Path):
    """Return a read-only in-memory DB after all exact input/schema checks."""
    raw = local.bounded_read(apk, local.APK_BYTES)
    local.verify(raw, local.APK_BYTES, local.APK_SHA256, "APK")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        if len(entries) != 4874 or sum(x.filename == local.RAR_MEMBER for x in entries) != 1:
            raise ValueError("Unexpected APK entries or duplicate local database resource")
        member = archive.getinfo(local.RAR_MEMBER)
        if member.file_size != local.RAR_BYTES or member.flag_bits & 1:
            raise ValueError("Unexpected local database resource")
        rar = archive.read(member)
    local.verify(rar, local.RAR_BYTES, local.RAR_SHA256, "RAR resource")
    database = local.extract_database(rar)
    # Validate every table/column/count and bounded outer JSON decoding first.
    local.inspect_database(database)
    db = sqlite3.connect(":memory:")
    db.deserialize(database)
    db.execute("PRAGMA trusted_schema=OFF")
    db.execute("PRAGMA query_only=ON")
    db.row_factory = sqlite3.Row
    return db


def rows(db, table):
    if table not in local.TABLES:
        raise ValueError("Unexpected source table")
    return [dict(row) for row in db.execute('SELECT * FROM "' + table + '"')]


def brand_name(raw):
    content = local.decode_json(raw["CONTENT"])
    if not isinstance(content, dict) or str(content.get("id")) != str(raw["ID"]):
        raise ValueError("Bundled brand identity does not match its row")
    english = [entry.get("name") for entry in content.get("info", [])
               if isinstance(entry, dict) and entry.get("country") == "EN"]
    selected = next((value for value in english if isinstance(value, str) and value.strip()), content.get("name"))
    checked_text(selected, 160)
    return selected


def checked_text(value, limit):
    if (not isinstance(value, str) or not value.strip() or len(value) > limit
            or any(ord(c) < 32 or ord(c) == 127 for c in value)):
        raise ValueError("Invalid source text")


def checked_signal(carrier, pattern):
    if type(carrier) is not int or not 20000 <= carrier <= 60000:
        raise ValueError("unsupported_carrier")
    if not isinstance(pattern, list) or not 3 <= len(pattern) <= 2000:
        raise ValueError("unsupported_pattern_type_or_length")
    if any(type(value) is not int or not 1 <= value <= 1000000 for value in pattern):
        raise ValueError("invalid_microsecond_duration")
    if sum(pattern) > 1900000:
        raise ValueError("pattern_exceeds_1_9_seconds")
    return pattern


def label(key):
    if key.endswith("_r"):
        return label(key[:-2]) + " [" + key + "]"
    return LABELS.get(key, key.replace("_", " ").strip().capitalize() or key)


def convert_commands(data, carrier, encrypted):
    if not isinstance(data, dict) or not data or len(data) > 10000:
        raise ValueError("Expected a bounded raw command dictionary")
    accepted, excluded = {}, Counter()
    for key, original in sorted(data.items()):
        checked_text(key, 240)
        try:
            # Indexed source arrays are already microseconds. Unindexed Xiaomi
            # records contain the same arrays in the public AES/gzip format.
            pattern = local.decode_json(original, limit=32768) if encrypted else original
            checked_signal(carrier, pattern)
        except (ValueError, TypeError) as error:
            reason = str(error)
            if reason not in {"unsupported_carrier", "unsupported_pattern_type_or_length",
                              "invalid_microsecond_duration", "pattern_exceeds_1_9_seconds"}:
                reason = "unsupported_raw_encoding"
            excluded[reason] += 1
            continue
        accepted[key] = {"sourceKey": key, "label": label(key), "carrierHz": carrier, "pattern": pattern}
    return accepted, excluded


def source_hash(row, waveform_row=None):
    value = {"row": row}
    if waveform_row is not None:
        value["waveformSourceRow"] = waveform_row
    return local.sha256(canonical(value))


def build_database(db):
    brands = {str(row["ID"]): row for row in rows(db, "brand")}
    names = {ident: brand_name(row) for ident, row in brands.items()}
    raw_sources = {str(row["ID"]): row for row in rows(db, "kk_remote_ircode_src")}
    decoded_sources = {}
    outputs, profiles, omitted = {}, [], []
    skipped_commands, skipped_profiles = Counter(), Counter()
    source_counts = Counter()
    for table in TABLES:
        for row in rows(db, table):
            device = int(row["DEVICE_ID"])
            if device not in CATEGORIES:
                continue
            source_counts[CATEGORIES[device]] += 1
            ident, brand_id = str(row["ID"]), str(row["BRAND_ID"])
            if not ID_PATTERN.fullmatch(ident) or not re.fullmatch(r"[0-9]{1,10}", brand_id):
                raise ValueError("Unsafe source model/brand ID")
            reason = None
            if brand_id not in names:
                reason = "no_bundled_brand_name"
            elif table == "kk_remote_ircode" and int(row["EXT_FLAG"]) != 0:
                reason = "stateful_ac_template_requires_encoder"
            if reason:
                skipped_profiles[reason] += 1
                omitted.append({"table": table, "modelId": ident, "brandId": brand_id,
                                "category": CATEGORIES[device], "reason": reason})
                continue
            text_carrier = str(row["FREQUENCY"])
            if not re.fullmatch(r"[0-9]{1,8}", text_carrier):
                raise ValueError("Noninteger source carrier")
            carrier = int(text_carrier)
            referenced = None
            indexed = int(row["INDEXED"]) == 1
            if indexed:
                pointer = str(row["IR_ZIP_KEY"])
                referenced = raw_sources.get(pointer)
                if referenced is None:
                    raise ValueError("Indexed waveform row is missing")
                if pointer not in decoded_sources:
                    decoded_sources[pointer] = local.decode_json(referenced["IR_ZIP_KEY"])
                data = decoded_sources[pointer]
            else:
                data = local.decode_json(row["IR_ZIP_KEY"])
            commands, rejected = convert_commands(data, carrier, encrypted=not indexed)
            skipped_commands.update(rejected)
            if not commands:
                reason = "no_supported_raw_commands"
                skipped_profiles[reason] += 1
                omitted.append({"table": table, "modelId": ident, "brandId": brand_id,
                                "category": CATEGORIES[device], "reason": reason})
                continue
            profile_id = "mi_" + local.sha256(canonical(["mi724-apk", table, ident, brand_id, device]))[:24]
            asset = "profiles/" + profile_id + ".json"
            payload = canonical({"schemaVersion": 2, "sourceModelId": ident, "commands": commands})
            if asset in outputs:
                raise ValueError("Duplicate source profile ID")
            outputs[asset] = payload
            source = {"sourceTable": table, "sourceDeviceId": device, "brandId": brand_id,
                      "modelId": ident, "sourceFile": "irlocaldata.db/" + table + "/" + ident,
                      "sourceSha256": source_hash(row, referenced),
                      "brandFile": "irlocaldata.db/brand/" + brand_id,
                      "brandSha256": local.sha256(canonical(brands[brand_id])),
                      "sourceVersion": int(row["VERSION"]), "url": SOURCE_URL,
                      "format": "microsecond-json-array" if indexed else "aes-gzip-microsecond-json-array",
                      "license": "Vendor source data; no additional rights asserted"}
            if referenced is not None:
                source["waveformSourceRowId"] = str(referenced["ID"])
            profiles.append({"id": profile_id, "brand": names[brand_id], "model": ident,
                             "category": CATEGORIES[device], "transport": "ir", "hardwareTested": False,
                             "commandCount": len(commands), "asset": "ir/mi_apk/" + asset,
                             "assetSha256": local.sha256(payload), "source": source})
    profiles.sort(key=lambda p: (p["category"], p["brand"].casefold(), p["model"], p["id"]))
    categories = {}
    for category in CATEGORIES.values():
        selected = [profile for profile in profiles if profile["category"] == category]
        categories[category] = {"profiles": len(selected), "commands": sum(p["commandCount"] for p in selected),
                                "brands": len({p["brand"] for p in selected})}
    coverage = {"profiles": len(profiles), "commands": sum(p["commandCount"] for p in profiles),
                "brands": len({p["brand"] for p in profiles}), "categories": categories,
                "sourceProfiles": dict(source_counts), "skippedCommands": dict(sorted(skipped_commands.items())),
                "skippedProfiles": dict(sorted(skipped_profiles.items()))}
    index = {"schemaVersion": 2, "source": {"repository": SOURCE_REPOSITORY,
             "kind": "mi-apk-bundled-database", "versionName": "7.2.4M", "versionCode": 6500,
             "apkSha256": local.APK_SHA256, "databaseSha256": local.DB_SHA256,
             "archiveSha256": "72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6",
             "url": SOURCE_URL, "timingUnit": "microseconds", "frequencyUnit": "Hz",
             "physicalHardwareTested": False}, "coverage": coverage, "profiles": profiles}
    outputs["index.json"] = canonical(index)
    report = {"schemaVersion": 1, "apkSha256": local.APK_SHA256, "databaseSha256": local.DB_SHA256,
              "coverage": coverage, "omittedProfiles": omitted,
              "limits": ["Only exact named TV and raw AC associations from the pinned bundled database.",
                         "Stateful AC templates require independently implemented encoders and are not executed.",
                         "Online-only profiles and native proprietary encoders are not recovered by this conversion.",
                         "No physical appliance compatibility is established."]}
    return outputs, report


def manifest(outputs):
    return canonical({"schemaVersion": 1, "generator": "tools/mi_apk_catalogue/generate_apk.py",
                      "outputs": {name: {"bytes": len(raw), "sha256": local.sha256(raw)}
                                  for name, raw in sorted(outputs.items())}})


def write_or_check(outputs, report, output=DEFAULT_OUTPUT, check=False, metadata=ROOT):
    expected = {metadata / "generated-manifest.json": manifest(outputs), metadata / "import-summary.json": canonical(report)}
    expected.update({output / name: raw for name, raw in outputs.items()})
    actual_files = {path for path in output.rglob("*") if path.is_file()} if output.exists() else set()
    output_files = {output / name for name in outputs}
    if check:
        if actual_files != output_files:
            raise ValueError("Generated APK profile files differ from the expected set")
        for path, raw in expected.items():
            if not path.is_file() or path.read_bytes() != raw:
                raise ValueError("Generated APK catalogue differs: " + str(path))
    else:
        # Never clear unknown caller files; only replace this converter's own outputs.
        if actual_files - output_files:
            raise ValueError("Unexpected files in the generated APK output directory")
        for path, raw in expected.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Exact verified APK; otherwise unpack the committed archive temporarily")
    parser.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="palm-mi-apk-") as temporary:
        apk = args.apk or extract_verified_archive(args.archive, Path(temporary) / "source.apk")
        db = open_database(apk)
        try:
            outputs, report = build_database(db)
        finally:
            db.close()
        write_or_check(outputs, report, args.output, args.check)
    print(json.dumps({"mode": "checked" if args.check else "generated", "coverage": report["coverage"]}, indent=2))


if __name__ == "__main__":
    main()
