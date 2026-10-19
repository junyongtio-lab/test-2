#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Reproduce the bounded, offline Astro supplement from pinned public responses.

This independently written converter performs no network requests and includes
no Xiaomi/Kookong implementation, native binary or executable model script.
The input data is attributed separately; it is not relicensed by this file.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import gzip
import hashlib
import io
import json
from pathlib import Path

from Crypto.Cipher import AES

ORIGIN = "https://sg-urc.io.mi.com"
ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
DEFAULT_OUTPUT = REPOSITORY / "app/src/main/assets/ir/mi_stb"
# Public raw-waveform file-format constant, not an account credential/token.
PATTERN_FORMAT_KEY = b"fd7e915003168929c1a9b0ec32a60788"
MODEL_IDS = tuple("kk_2_3833_" + suffix for suffix in ("536", "1819", "350", "211", "2160"))
PINS = {
    "brands_my.json": (215689, "ad3fcd077c80bcad07c8847d8a763c955da0c59ae79deb8e2e96178fdd8a7ba5"),
    "astro_matches_my.json": (1911, "e1e45e478d5310f45cb17d3d6cc6035d372b8af8f4e8fee39898f928350d268e"),
    "keyset_kk_2_3833_536.json": (6942, "079c3fc37ed7abb074f129b78f75f31e7fe836f6d5fd52f8dc58e6a3bad5c3b5"),
    "keyset_kk_2_3833_1819.json": (4631, "453c28d82c8731fe68c99ad86804652825d96a0b832b3e7f8d5a077b68c01daa"),
    "keyset_kk_2_3833_350.json": (7258, "d47b2fb9b017f841b496b72bb21e84f0ac471b0386a6d5af6e4bd1dbc7252361"),
    "keyset_kk_2_3833_211.json": (3415, "dab3c068578abca2e0c33806e12ddb1e6d418908a3eec3e24e9dd071f5187a92"),
    "keyset_kk_2_3833_2160.json": (1872, "59716adc2a0d56abede4b8fe4bc7e1afbadce8df00ae1d8c805cad10f2c9ead6"),
}
MAX_PLAIN_BYTES = 32768
LABELS = {"power": "Power", "mute": "Mute", "vol+": "Volume +", "vol-": "Volume −",
          "ch+": "Channel +", "ch-": "Channel −", "ok": "OK", "FF": "Fast forward", "REW": "Rewind"}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def strict_json(raw):
    def reject(value):
        raise ValueError("Nonstandard JSON constant")
    return json.loads(raw, object_pairs_hook=unique_object, parse_constant=reject)


def decode_signal(encoded, carrier):
    """Return source microseconds unchanged; never round-trip carrier cycles."""
    if type(carrier) is not int or not 20000 <= carrier <= 60000:
        raise ValueError("Unsupported carrier")
    if not isinstance(encoded, str) or not 1 <= len(encoded) <= 65536:
        raise ValueError("Invalid encrypted waveform")
    try:
        encrypted = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Invalid Base64 waveform") from exc
    if not encrypted or len(encrypted) % 16:
        raise ValueError("Invalid encrypted waveform length")
    clear = AES.new(PATTERN_FORMAT_KEY, AES.MODE_ECB).decrypt(encrypted).rstrip()
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(clear)) as compressed:
            raw = compressed.read(MAX_PLAIN_BYTES + 1)
    except (OSError, EOFError, ValueError) as exc:
        raise ValueError("Invalid compressed waveform") from exc
    if len(raw) > MAX_PLAIN_BYTES:
        raise ValueError("Waveform exceeds decompressed byte bound")
    pattern = strict_json(raw)
    if not isinstance(pattern, list) or not 3 <= len(pattern) <= 2000:
        raise ValueError("Invalid timing array length/type")
    if any(type(duration) is not int or not 1 <= duration <= 1000000 for duration in pattern):
        raise ValueError("Invalid timing duration")
    if sum(pattern) > 1900000:
        raise ValueError("Timing array exceeds 1.9 seconds")
    return pattern


def label(key):
    if key.endswith("_r"):
        return label(key[:-2]) + " [" + key + "]"
    return LABELS.get(key, key.replace("_", " ").capitalize())


def read_source(source, name):
    expected_size, expected_hash = PINS[name]
    path = source / name
    if path.stat().st_size != expected_size:
        raise ValueError("Pinned source size differs: " + name)
    raw = path.read_bytes()
    if sha256(raw) != expected_hash:
        raise ValueError("Pinned source hash differs: " + name)
    document = strict_json(raw)
    if not isinstance(document, dict) or type(document.get("code")) is not int or document["code"] != 0:
        raise ValueError("Source response is not a successful service result")
    if not isinstance(document.get("result"), dict):
        raise ValueError("Source response lacks result")
    return document["result"]


def build(source=ROOT / "source"):
    """Validate the identity chain, then build deterministic schema-2 assets."""
    brands = read_source(source, "brands_my.json").get("data")
    if not isinstance(brands, list):
        raise ValueError("Expected source brand array")
    astro = [brand for brand in brands if brand.get("name") == "Astro" and brand.get("brandid") == 3833]
    if len(astro) != 1 or astro[0].get("deviceid") != 2:
        raise ValueError("Expected attributable Astro STB identity")
    matches = read_source(source, "astro_matches_my.json").get("matchs")
    if not isinstance(matches, list) or tuple(row.get("_id") for row in matches) != MODEL_IDS:
        raise ValueError("Expected pinned Astro match identities and source order")
    outputs, profiles = {}, []
    for rank, match in enumerate(matches, 1):
        model_id = match["_id"]
        if match.get("brand") != "3833" or match.get("device") != 2 or match.get("source") != "kk":
            raise ValueError("Match identity is not the expected Astro STB")
        name = "keyset_" + model_id + ".json"
        data = read_source(source, name).get("data")
        if not isinstance(data, dict) or data.get("_id") != model_id:
            raise ValueError("Keyset identity mismatch")
        carrier = data.get("frequency")
        if carrier != match.get("frequency"):
            raise ValueError("Match/keyset carrier mismatch")
        keyset = data.get("key")
        if not isinstance(keyset, dict) or not keyset or len(keyset) > 256:
            raise ValueError("Expected bounded native-format key dictionary")
        commands = {}
        for key, encrypted in sorted(keyset.items()):
            if not isinstance(key, str) or not key or len(key) > 128:
                raise ValueError("Invalid source command key")
            commands[key] = {"label": label(key), "carrierHz": carrier,
                             "pattern": decode_signal(encrypted, carrier), "sourceKey": key}
        # Namespace and stable ID differ from the GitHub database's original IDs.
        profile_id = "mi_" + sha256((ORIGIN + "\0" + model_id).encode())[:24]
        relative = "profiles/" + profile_id + ".json"
        asset_data = canonical({"schemaVersion": 2, "sourceModelId": model_id, "commands": commands})
        outputs[relative] = asset_data
        profiles.append({"id": profile_id, "brand": "Astro", "model": model_id,
                         "category": "set_top_box", "transport": "ir", "hardwareTested": False,
                         "commandCount": len(commands), "asset": "ir/mi_stb/" + relative,
                         "assetSha256": sha256(asset_data),
                         "source": {"brandId": 3833, "modelId": model_id, "matchRank": rank,
                                    "sourceDeviceId": 2, "sourceFile": "tools/mi_stb/source/" + name,
                                    "sourceSha256": PINS[name][1],
                                    "brandFile": "tools/mi_stb/source/brands_my.json",
                                    "brandSha256": PINS["brands_my.json"][1],
                                    "url": ORIGIN + "/app/public/get_stb_keyset",
                                    "license": "Source-service data; no additional rights asserted"}})
    command_count = sum(profile["commandCount"] for profile in profiles)
    coverage = {"brands": 1, "profiles": len(profiles), "commands": command_count,
                "categories": {"set_top_box": {"brands": 1, "profiles": len(profiles), "commands": command_count}},
                "requestedBrandsAbsent": ["NJOI", "PRISM+", "PrismPlus"]}
    outputs["index.json"] = canonical({"schemaVersion": 2,
        "source": {"repository": ORIGIN, "origin": ORIGIN, "kind": "mi-stb-public-api", "country": "MY",
                   "retrievedAtUtc": "2026-10-19T13:15:10+00:00", "provenance": "tools/mi_stb/provenance.json"},
        "coverage": coverage, "profiles": profiles})
    return outputs


def output_manifest(outputs):
    return canonical({"schemaVersion": 1, "generator": "tools/mi_stb/generate_stb.py",
                      "outputs": {name: {"bytes": len(raw), "sha256": sha256(raw)}
                                  for name, raw in sorted(outputs.items())}})


def write_or_check(output, manifest_path, check=False, source=ROOT / "source"):
    outputs = build(source)
    manifest = output_manifest(outputs)
    if check:
        actual = {str(path.relative_to(output)): path.read_bytes() for path in output.rglob("*.json")}
        if actual != outputs or not manifest_path.exists() or manifest_path.read_bytes() != manifest:
            raise ValueError("Generated Astro assets/manifests differ; regenerate and review")
    else:
        output.mkdir(parents=True, exist_ok=True)
        for name, raw in outputs.items():
            destination = output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(raw)
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_bytes(manifest)
    return strict_json(outputs["index.json"])["coverage"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Compare checked-in assets without changing them")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--manifest", type=Path, default=ROOT / "generated-manifest.json")
    args = parser.parse_args()
    try:
        coverage = write_or_check(args.output, args.manifest, args.check)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(json.dumps({"mode": "checked" if args.check else "generated", "coverage": coverage}, indent=2))


if __name__ == "__main__":
    main()
