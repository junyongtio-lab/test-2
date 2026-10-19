#!/usr/bin/env python3
"""Bounded, static APK inventory. Never installs/runs APK or its native libraries.

Usage: python3 inspect_apk.py INPUT.apk OUTPUT_DIRECTORY [--extract]
Original MIT-licensed helper; no third-party application implementation is included.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import stat
import subprocess
import threading
import zipfile
import zlib

MAX_ENTRIES = 50_000
MAX_MEMBER = 128 * 1024 * 1024
MAX_TOTAL = 1024 * 1024 * 1024
MAX_RATIO = 1000
MAX_COMMAND_OUTPUT = 8 * 1024 * 1024
TERMS = re.compile(rb"astro|njoi|ultra[ _-]*box|ulti[ _-]*box", re.I)
UTF16_TERMS = re.compile(rb"a\x00s\x00t\x00r\x00o\x00|n\x00j\x00o\x00i\x00|u\x00l\x00t\x00r\x00a\x00(?:[ _-]\x00)*b\x00o\x00x\x00|u\x00l\x00t\x00i\x00(?:[ _-]\x00)*b\x00o\x00x\x00", re.I)
URLS = re.compile(rb"https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]{4,512}")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dump(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def safe_name(name: str) -> bool:
    p = PurePosixPath(name)
    return (bool(name) and bool(p.parts) and not p.is_absolute()
            and ".." not in p.parts and "\\" not in name
            and ":" not in p.parts[0] and not any(ord(c) < 32 for c in name)
            and p.as_posix() == name.rstrip("/"))


def kind(name: str) -> str:
    low = name.lower()
    if low.endswith(".dex"):
        return "dex"
    if low.startswith("lib/") and low.endswith(".so"):
        return "native"
    if low.endswith((".db", ".sqlite", ".sqlite3")):
        return "database"
    if low.endswith(".json"):
        return "json"
    if low.startswith("assets/"):
        return "asset"
    if low.startswith("res/") or low == "resources.arsc":
        return "resource"
    return "other"


def interesting_strings(data: bytes):
    hits = []
    for encoding, pattern, scale in (("ascii", TERMS, 1), ("utf16le", UTF16_TERMS, 2)):
        # Match UTF-16 on the original bytes, including strings at odd offsets.
        # String candidates still require manual semantic interpretation.
        for match in pattern.finditer(data):
            start, end = max(0, match.start() - 90 * scale), min(len(data), match.end() + 160 * scale)
            if encoding == "utf16le":
                start += (match.start() - start) % 2
            snippet = data[start:end].decode("utf-16le" if scale == 2 else "utf-8", "replace")
            snippet = "".join(c if c.isprintable() else " " for c in snippet)
            hits.append({"encodingCandidate": encoding, "offset": match.start(), "text": snippet})
            if len(hits) >= 1000:
                return hits
    return hits


def command_report(args, destination: Path, timeout=120):
    executable = shutil.which(args[0]) if "/" not in args[0] else args[0]
    if not executable or not Path(executable).exists():
        return {"command": args, "available": False}
    # Drain output concurrently while retaining only a bounded prefix, so even a
    # noisy parser cannot consume unbounded memory or temporary disk space.
    output = bytearray()
    truncated = False
    p = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         start_new_session=os.name == "posix")

    def read_output():
        nonlocal truncated
        while chunk := p.stdout.read(65536):
            remaining = MAX_COMMAND_OUTPUT - len(output)
            output.extend(chunk[:remaining])
            truncated = truncated or len(chunk) > remaining

    reader = threading.Thread(target=read_output, daemon=True)
    reader.start()
    error = None
    try:
        returncode = p.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        error = "timeout"
        # SDK launchers can spawn Java; terminate the whole parser group so its
        # child cannot keep the output pipe open after the timeout.
        if os.name == "posix":
            os.killpg(p.pid, signal.SIGKILL)
        else:
            p.kill()
        p.wait()
        returncode = None
    reader.join()
    p.stdout.close()
    destination.write_bytes(output)
    return {"command": args, "available": True, "returncode": returncode, "error": error, "output": destination.name, "truncated": truncated}


def inspect_apk(apk: Path, output: Path, *, extract=False, external_tools=True,
                build_tools: Path | None = None):
    source = apk.resolve(strict=True)
    out = output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        raise ValueError("Output directory must be empty")
    with source.open("rb") as stream:
        archive_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    report = {"apk": str(source), "sha256": archive_hash, "bytes": source.stat().st_size,
              "staticOnly": True, "limits": {"maxEntries": MAX_ENTRIES, "maxMemberBytes": MAX_MEMBER, "maxTotalUncompressedBytes": MAX_TOTAL, "maxCompressionRatio": MAX_RATIO},
              "entries": [], "interestingStrings": [], "urls": [], "nativeReports": [], "commands": []}
    consumed = 0
    names = set()
    with zipfile.ZipFile(source) as archive:
        infos = archive.infolist()
        if len(infos) > MAX_ENTRIES:
            raise ValueError("ZIP entry count exceeds bound")
        for i in infos:
            k = kind(i.filename)
            entry = {"name": i.filename, "kind": k, "bytes": i.file_size, "compressedBytes": i.compress_size,
                     "compression": i.compress_type, "crc32": f"{i.CRC:08x}"}
            report["entries"].append(entry)
            reason = None
            if not safe_name(i.filename) or i.filename in names:
                reason = "unsafe or duplicate ZIP name"
            elif stat.S_ISLNK(i.external_attr >> 16):
                reason = "symlink"
            elif i.flag_bits & 1:
                reason = "encrypted ZIP entry"
            elif i.file_size > MAX_MEMBER or consumed + i.file_size > MAX_TOTAL:
                reason = "uncompressed size exceeds bound"
            elif i.file_size > max(1, i.compress_size) * MAX_RATIO:
                reason = "compression ratio exceeds bound"
            names.add(i.filename)
            if reason:
                entry["skipped"] = reason
                continue
            if i.is_dir():
                continue
            try:
                with archive.open(i) as stream:
                    data = stream.read(MAX_MEMBER + 1)
                if len(data) != i.file_size or len(data) > MAX_MEMBER:
                    raise ValueError("inflated size mismatch")
            except (ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError, zlib.error, EOFError) as exc:
                entry["skipped"] = type(exc).__name__ + ": " + str(exc)
                continue
            consumed += len(data)
            entry["sha256"] = sha256(data)
            hits = interesting_strings(data)
            if hits:
                report["interestingStrings"].append({"name": i.filename, "hits": hits})
            urls = sorted({m.group(0).decode("ascii") for m in URLS.finditer(data)})[:1000]
            if urls:
                report["urls"].append({"name": i.filename, "values": urls})
            if data.startswith(b"SQLite format 3\x00"):
                entry["sqliteHeader"] = True
            native_path = None
            if extract:
                destination = out / "extracted" / i.filename
                # Reject an existing symlink introduced into the output tree by
                # another process; never follow ZIP-controlled paths out of root.
                if not destination.resolve().is_relative_to((out / "extracted").resolve()):
                    raise ValueError("Extraction path escaped root")
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("xb") as stream:
                    stream.write(data)
                entry["extracted"] = str(destination.relative_to(out))
                native_path = destination if k == "native" else None
            elif k == "native" and external_tools:
                native_path = out / (entry["sha256"] + ".so.analysis-input")
                native_path.write_bytes(data)
            if native_path and external_tools:
                stem = i.filename.replace("/", "_")
                details = {"name": i.filename, "sha256": entry["sha256"]}
                details["readelf"] = command_report(["readelf", "--file-header", "--dynamic", "--dyn-syms", str(native_path)], out / (stem + ".readelf.txt"))
                details["strings"] = command_report(["strings", "-a", "-n", "5", str(native_path)], out / (stem + ".strings.txt"))
                report["nativeReports"].append(details)
                if not extract:
                    native_path.unlink()
    if external_tools:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        sdk_tools = build_tools or (Path(sdk) / "build-tools/35.0.0" if sdk else None)

        def sdk_tool(name):
            return str(sdk_tools / name) if sdk_tools else name

        report["commands"].append(command_report([sdk_tool("aapt"), "dump", "badging", str(source)], out / "aapt-badging.txt"))
        report["commands"].append(command_report([sdk_tool("aapt"), "dump", "xmltree", str(source), "AndroidManifest.xml"], out / "manifest-xmltree.txt"))
        report["commands"].append(command_report([sdk_tool("apksigner"), "verify", "--verbose", "--print-certs", str(source)], out / "apksigner-verification.txt"))
    report["totalReadBytes"] = consumed
    report["counts"] = {k: sum(e["kind"] == k for e in report["entries"]) for k in sorted({e["kind"] for e in report["entries"]})}
    dump(out / "inventory.json", report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--extract", action="store_true", help="Extract bounded verified ZIP entries into OUTPUT/extracted")
    parser.add_argument("--no-external-tools", action="store_true", help="Skip aapt, apksigner, readelf and strings")
    parser.add_argument("--build-tools", type=Path, help="Android SDK build-tools directory; defaults to SDK 35.0.0 in ANDROID_HOME/ANDROID_SDK_ROOT, then PATH")
    options = parser.parse_args()
    try:
        report = inspect_apk(options.apk, options.output, extract=options.extract,
                             external_tools=not options.no_external_tools,
                             build_tools=options.build_tools)
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        parser.error(str(exc))
    print(json.dumps({"sha256": report["sha256"], "counts": report["counts"], "entriesSkipped": sum("skipped" in e for e in report["entries"]), "interestingFiles": len(report["interestingStrings"]), "report": str(options.output.resolve() / "inventory.json")}))


if __name__ == "__main__":
    main()
