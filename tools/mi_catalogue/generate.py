#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 Palm Remote contributors
"""Offline conversion of the published ysard/mi_remote_database JSON release.

No upstream Python is imported, no network operation is performed, and no
embedded executable source (including AC Lua/Java snippets) is evaluated.
The format facts are documented by the pinned upstream project's README and
crypt_utils.py. This independent build tool stays outside the Android app.
"""

import argparse
import base64
import binascii
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import tarfile
import tempfile

from Crypto.Cipher import AES

REPOSITORY = "https://github.com/ysard/mi_remote_database"
REVISION = "8a60b419506e8c407f2b756ec8ab4b4bc446678d"
RELEASE = "v2.1"
ARCHIVE_SHA256 = "44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895"
ARCHIVE_BYTES = 7892348
# A public file-format constant published by upstream; its ASCII bytes form
# an AES-256 key. It is not a personal account credential or a network token.
PATTERN_FORMAT_KEY = b"fd7e915003168929c1a9b0ec32a60788"
DEVICE_CATEGORIES = {
    1: "tv", 2: "set_top_box", 3: "air_conditioner", 6: "fan",
    11: "set_top_box", 12: "set_top_box",
}
LABELS = {
    "power": "Power", "mute": "Mute", "vol+": "Volume +", "vol-": "Volume −",
    "ch+": "Channel +", "ch-": "Channel −", "tv_av": "TV / AV",
    "ok": "OK", "sel": "Select", "FF": "Fast forward", "REW": "Rewind",
    "temperature_down": "Temperature −", "temperature_up": "Temperature +",
    "shake_wind": "Oscillation", "wind_speed": "Fan speed",
    "wind_type": "Wind type", "fanspeed": "Fan speed",
    "fanspeed+": "Fan speed +", "fanspeed-": "Fan speed −",
    "ud_wind_mode": "Vertical swing mode", "ud_wind_mode_swing": "Vertical swing",
    "ud_wind_mode_fix": "Fixed vertical direction",
}


class UnsupportedSignal(ValueError):
    """A source record cannot safely be used as an Android raw signal."""


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def canonical(data):
    """Stable UTF-8 JSON, preserving timing values and source button keys."""
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def validate_signal(frequency, pattern):
    if type(frequency) is not int or not 20000 <= frequency <= 60000:
        raise UnsupportedSignal("carrier_out_of_range")
    if not isinstance(pattern, list) or not 3 <= len(pattern) <= 2000:
        raise UnsupportedSignal("pattern_length_or_type")
    if any(type(value) is not int or not 1 <= value <= 1000000 for value in pattern):
        raise UnsupportedSignal("nonpositive_or_noninteger_timing")
    if sum(pattern) > 1900000:
        raise UnsupportedSignal("pattern_over_1_9_seconds")
    return pattern


def decode_signal(encoded, frequency):
    """Decode only the documented AES/gzip JSON raw-timing representation.

    Original model JSON timings are already microseconds. TVKill exports use
    carrier cycles instead and are deliberately not an input to this tool.
    """
    if type(frequency) is not int or not 20000 <= frequency <= 60000:
        raise UnsupportedSignal("carrier_out_of_range")
    if not isinstance(encoded, str):
        raise UnsupportedSignal("unsupported_payload_type")
    if "functionId" in encoded or "bytes[" in encoded or "bit32." in encoded:
        raise UnsupportedSignal("unsupported_executable_ac_definition")
    try:
        ciphertext = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise UnsupportedSignal("unsupported_nonbase64_definition") from None
    if not ciphertext or len(ciphertext) % 16:
        raise UnsupportedSignal("unsupported_ciphertext_length")
    decrypted = AES.new(PATTERN_FORMAT_KEY, AES.MODE_ECB).decrypt(ciphertext).rstrip()
    try:
        decoded = gzip.decompress(decrypted)
    except (OSError, EOFError, ValueError):
        raise UnsupportedSignal("unsupported_non_gzip_definition") from None
    try:
        pattern = json.loads(decoded)
    except (ValueError, UnicodeError):
        raise UnsupportedSignal("unsupported_non_json_definition") from None
    return validate_signal(frequency, pattern)


def model_references(brand_data):
    """Preserve every model referenced by either published brand section."""
    refs = set()
    for node in (brand_data.get("tree") or {}).get("nodes", []):
        refs.update(node.get("keysetids", []))
    for model in brand_data.get("others", []):
        if model.get("_id"):
            refs.add(model["_id"])
    return sorted(refs)


def label(source_key):
    if source_key in LABELS:
        return LABELS[source_key]
    if source_key.endswith("_r"):
        return label(source_key[:-2]) + " [" + source_key + "]"
    return source_key.replace("_", " ").strip().capitalize() or source_key


def convert_model(data):
    """Return keyed signals and skip reasons without cross-key caching."""
    commands = {}
    skipped = Counter()
    for source_key, encoded in sorted(data.get("key", {}).items()):
        try:
            pattern = decode_signal(encoded, data.get("frequency"))
        except UnsupportedSignal as error:
            skipped[str(error)] += 1
            continue
        commands[source_key] = {
            "label": label(source_key), "carrierHz": data["frequency"],
            "pattern": pattern, "sourceKey": source_key,
        }
    return commands, skipped


def verify_archive(archive):
    content = archive.read_bytes()
    if len(content) != ARCHIVE_BYTES or sha256(content) != ARCHIVE_SHA256:
        raise ValueError("The input archive does not match the pinned GitHub v2.1 release.")


def generate(database, output, manifest_path, expected_source_hashes=None):
    profiles = []
    source_files = {}
    by_category = defaultdict(Counter)
    by_device = {}
    unavailable = []
    skipped_commands = Counter()
    skipped_models = Counter()

    def read_source(path):
        raw = path.read_bytes()
        relative = path.relative_to(database).as_posix()
        if expected_source_hashes is not None and expected_source_hashes.get(relative) != sha256(raw):
            raise ValueError("Input JSON differs from the pinned archive: " + relative)
        source_files[relative] = {"sha256": sha256(raw), "bytes": len(raw)}
        return json.loads(raw), relative, sha256(raw)

    output.mkdir(parents=True, exist_ok=True)
    profile_dir = output / "profiles"
    profile_dir.mkdir(exist_ok=True)
    # Only generated JSON under this tool's own output directory is replaced.
    for old in profile_dir.glob("mi_*.json"):
        old.unlink()
    if (database / "devices.json").is_file():
        read_source(database / "devices.json")

    for device_id, category in DEVICE_CATEGORIES.items():
        directories = sorted(path for path in database.glob(str(device_id) + "_*") if path.is_dir())
        if len(directories) != 1:
            raise ValueError("Expected one device directory for " + str(device_id))
        device_dir = directories[0]
        category_file = device_dir.with_suffix(".json")
        if category_file.is_file():
            read_source(category_file)
        device_counts = Counter()
        category_brands = set()
        for brand_file in sorted(device_dir.glob("*.json")):
            brand_root, brand_relative, brand_sha = read_source(brand_file)
            brand = brand_file.stem.rsplit("_", 1)[0]
            refs = model_references(brand_root.get("data") or {})
            device_counts["sourceBrands"] += 1
            device_counts["sourceModels"] += len(refs)
            accepted_brand_models = 0
            brand_skip = Counter()
            for model_id in refs:
                if "/" in model_id or "\\" in model_id or model_id in (".", ".."):
                    raise ValueError("Unsafe source model ID")
                model_file = device_dir / "models" / (model_id + ".json")
                if not model_file.is_file():
                    skipped_models["missing_model_json"] += 1
                    brand_skip["missing_model_json"] += 1
                    continue
                root, model_relative, model_sha = read_source(model_file)
                data = root.get("data")
                if not isinstance(data, dict) or not isinstance(data.get("key"), dict):
                    skipped_models["empty_model_data"] += 1
                    brand_skip["empty_model_data"] += 1
                    continue
                commands, rejected = convert_model(data)
                skipped_commands.update(rejected)
                brand_skip.update(rejected)
                device_counts["skippedCommands"] += sum(rejected.values())
                if not commands:
                    skipped_models["no_supported_raw_commands"] += 1
                    continue
                stable_key = f"{device_id}\0{brand_file.name}\0{model_id}".encode("utf-8")
                profile_id = "mi_" + sha256(stable_key)[:24]
                asset = "ir/mi/profiles/" + profile_id + ".json"
                profile_data = canonical({"schemaVersion": 2, "sourceModelId": model_id, "commands": commands})
                (profile_dir / (profile_id + ".json")).write_bytes(profile_data)
                profiles.append({
                    "id": profile_id, "brand": brand, "model": model_id,
                    "category": category, "transport": "ir", "hardwareTested": False,
                    "commandCount": len(commands), "asset": asset,
                    "assetSha256": sha256(profile_data),
                    "source": {
                        "sourceFile": model_relative, "sourceSha256": model_sha,
                        "modelId": model_id, "brandFile": brand_relative,
                        "brandSha256": brand_sha, "sourceDeviceId": device_id,
                        "url": REPOSITORY + "/releases/tag/" + RELEASE,
                        "license": "AGPL-3.0-or-later (upstream repository)",
                    },
                })
                category_brands.add(brand)
                accepted_brand_models += 1
                device_counts["profiles"] += 1
                device_counts["commands"] += len(commands)
                by_category[category]["profiles"] += 1
                by_category[category]["commands"] += len(commands)
            if accepted_brand_models:
                device_counts["brands"] += 1
            else:
                unavailable.append({
                    "brand": brand, "category": category, "sourceDeviceId": device_id,
                    "sourceModels": len(refs), "brandFile": brand_relative,
                    "reasons": dict(sorted(brand_skip.items())),
                })
        by_device[str(device_id)] = {"directory": device_dir.name, "category": category, **dict(device_counts)}

    for category in sorted(by_category):
        by_category[category]["brands"] = len({p["brand"] for p in profiles if p["category"] == category})
    source = {
        "repository": REPOSITORY, "revision": REVISION, "release": RELEASE,
        "archiveName": "database_dump.tar.xz", "archiveSha256": ARCHIVE_SHA256,
        "archiveBytes": ARCHIVE_BYTES,
        "archiveUrl": REPOSITORY + "/releases/download/" + RELEASE + "/database_dump.tar.xz",
        "license": "AGPL-3.0-or-later (upstream repository)",
        "timingUnit": "microseconds", "frequencyUnit": "Hz",
        "hardwareTested": False,
        "limitations": [
            "Only published GitHub release JSON is used; no Xiaomi API requests.",
            "Original model identifiers are code-set IDs, not verified hardware model numbers.",
            "AC executable/proprietary definitions are omitted; only decoded raw commands are included.",
            "No Astro, NJOI, PRISM+ or PrismPlus brand is present in the selected release categories.",
            "Source coverage does not guarantee compatibility with a particular appliance.",
        ],
    }
    coverage = {
        "profiles": len(profiles), "commands": sum(p["commandCount"] for p in profiles),
        "brands": len({p["brand"] for p in profiles}),
        "categories": {key: dict(value) for key, value in sorted(by_category.items())},
        "sourceDevices": by_device, "skippedCommands": dict(sorted(skipped_commands.items())),
        "skippedModels": dict(sorted(skipped_models.items())),
        "unavailableBrands": sorted(unavailable, key=lambda item: (item["category"], item["brand"].casefold())),
        "requestedBrandsAbsent": ["Astro", "NJOI", "PRISM+", "PrismPlus"],
    }
    index = {"schemaVersion": 2, "source": source, "profiles": profiles, "coverage": coverage}
    index_data = canonical(index)
    (output / "index.json").write_bytes(index_data)
    manifest = {
        "schemaVersion": 1, "source": source,
        "sourceFiles": dict(sorted(source_files.items())),
        "outputIndexSha256": sha256(index_data),
        "profileAssets": {p["asset"]: p["assetSha256"] for p in profiles},
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_bytes(canonical(manifest))
    return index


def read_archive_json(archive, extracted_root=None):
    """Read trusted-hash archive JSON without executing or extracting programs."""
    files = {}
    with tarfile.open(archive, "r:xz") as source:
        for member in source:
            if not member.isfile() or not member.name.endswith(".json"):
                continue
            path = Path(member.name)
            if path.is_absolute() or ".." in path.parts or path.parts[0] != "database_dump":
                raise ValueError("Unsafe archive JSON path")
            relative = Path(*path.parts[1:])
            content = source.extractfile(member).read()
            files[relative.as_posix()] = sha256(content)
            if extracted_root is not None:
                target = extracted_root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
    return files


def check_output(actual, expected):
    actual_paths = {path.relative_to(actual).as_posix() for path in actual.rglob("*.json")}
    expected_paths = {path.relative_to(expected).as_posix() for path in expected.rglob("*.json")}
    if actual_paths != expected_paths:
        raise ValueError("Generated profile file set differs from committed assets")
    for relative in sorted(actual_paths):
        if (actual / relative).read_bytes() != (expected / relative).read_bytes():
            raise ValueError("Generated output differs: " + relative)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=Path("tools/mi_catalogue/source/database_dump-v2.1.tar.xz"), help="Pinned published JSON archive (defaults to committed offline source)")
    parser.add_argument("--database", type=Path, help="Optional pre-extracted database_dump directory, checked against archive hashes")
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/ir/mi"))
    parser.add_argument("--manifest", type=Path, default=Path("tools/mi_catalogue/source-manifest.json"))
    parser.add_argument("--check", action="store_true", help="Rebuild offline in a temporary directory and compare without changing assets")
    args = parser.parse_args()
    verify_archive(args.archive)
    with tempfile.TemporaryDirectory(prefix="palm-mi-json-") as temporary:
        temporary = Path(temporary)
        database = args.database or temporary / "database_dump"
        hashes = read_archive_json(args.archive, database if args.database is None else None)
        output = temporary / "generated" if args.check else args.output
        manifest = temporary / "source-manifest.json" if args.check else args.manifest
        index = generate(database, output, manifest, hashes)
        if args.check:
            check_output(output, args.output)
            if manifest.read_bytes() != args.manifest.read_bytes():
                raise ValueError("Generated source manifest differs from committed manifest")
            print("Offline archive, source manifest and all generated assets match.")
    print(json.dumps({key: index["coverage"][key] for key in ("profiles", "commands", "brands", "categories", "skippedCommands", "skippedModels")}, indent=2))


if __name__ == "__main__":
    main()
