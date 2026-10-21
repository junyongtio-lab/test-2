#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate TV candidate ordering from the pinned source's matching references.

Independently written metadata conversion. No waveform is changed, generated or
transmitted, no source program is executed, and no network request is made.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import io
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "tools/mi_catalogue/source/database_dump-v2.1.tar.xz"
BASE_INDEX = ROOT / "app/src/main/assets/ir/mi/index.json"
OUTPUT = ROOT / "app/src/main/assets/ir/matching_order.json"
MANIFEST = Path(__file__).with_name("generated-manifest.json")
ARCHIVE_SHA256 = "44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895"
ARCHIVE_BYTES = 7892348
BASE_INDEX_SHA256 = "221bfefd1307b846b89093489a1a1676132fe4c4f1f516e39dfafd6bc71bc7ac"
MAX_BRAND_BYTES = 8 * 1024 * 1024


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False,
                      separators=(",", ":")).encode("utf-8")


def model_order(data, country="MY"):
    """Flatten source preference, preserving first occurrence and all references.

    This is a candidate order, not a reconstruction of conditional matching:
    follow child links from the root, then detached nodes in source array order,
    then other captures by the declared regional/general order. Unknown edges
    and graph cycles never prevent traversal or discard references elsewhere.
    """
    result, seen = [], set()

    def remember(model):
        if isinstance(model, str) and model and model not in seen:
            seen.add(model)
            result.append(model)

    tree = data.get("tree") or {}
    nodes = tree.get("nodes") or []
    by_index = {node.get("index"): node for node in nodes
                if isinstance(node, dict) and type(node.get("index")) is int}
    visited = set()

    def visit(node):
        index = node.get("index")
        # Object identity also covers detached nodes without an explicit index.
        marker = ("index", index) if type(index) is int else ("object", id(node))
        if marker in visited:
            return
        visited.add(marker)
        for model in node.get("keysetids") or []:
            remember(model)
        for child in node.get("children_index") or []:
            if type(child) is int and child in by_index:
                visit(by_index[child])

    root_index = tree.get("root_index")
    if type(root_index) is int and root_index in by_index:
        visit(by_index[root_index])
    for node in nodes:
        if isinstance(node, dict):
            visit(node)

    def preference(pair):
        position, row = pair
        regional = (row.get("locales") or {}).get(country)
        declared = regional if type(regional) is int else row.get("order")
        return (declared if type(declared) is int else 10000, position)

    others = [(i, row) for i, row in enumerate(data.get("others") or [])
              if isinstance(row, dict)]
    for _, row in sorted(others, key=preference):
        remember(row.get("_id"))
    return result


def build(archive=ARCHIVE, index_path=BASE_INDEX):
    raw_archive = archive.read_bytes()
    if len(raw_archive) != ARCHIVE_BYTES or sha256(raw_archive) != ARCHIVE_SHA256:
        raise ValueError("Source archive does not match the pinned GitHub release")
    raw_index = index_path.read_bytes()
    if sha256(raw_index) != BASE_INDEX_SHA256:
        raise ValueError("Base index does not match the pinned accepted catalogue")
    index = json.loads(raw_index)
    if index.get("schemaVersion") != 2 or index.get("source", {}).get("archiveSha256") != ARCHIVE_SHA256:
        raise ValueError("Base index is not the pinned GitHub catalogue")
    grouped = defaultdict(list)
    for profile in index["profiles"]:
        if profile["category"] == "tv":
            grouped[profile["source"]["brandFile"]].append(profile)
    outputs, inputs, seen_ids = [], {}, set()
    # Stream once: random seeks in an xz TAR repeatedly decompress the archive.
    # Retain only bounded brand metadata, never full model waveform members.
    source_brands = {}
    with tarfile.open(fileobj=io.BytesIO(raw_archive), mode="r|xz") as source:
        for member in source:
            path = member.name.removeprefix("database_dump/")
            if path not in grouped:
                continue
            if not path.startswith("1_TV/") or ".." in Path(path).parts:
                raise ValueError("Unexpected source brand path")
            if path in source_brands or not member.isfile() or not 0 < member.size <= MAX_BRAND_BYTES:
                raise ValueError("Invalid or duplicate source brand member")
            raw_brand = source.extractfile(member).read(MAX_BRAND_BYTES + 1)
            if len(raw_brand) != member.size:
                raise ValueError("Source brand member length differs")
            source_brands[path] = raw_brand
    if set(source_brands) != set(grouped):
        raise ValueError("Source archive lacks an accepted TV brand")
    for path, accepted in grouped.items():
        raw_brand = source_brands[path]
        digest = sha256(raw_brand)
        if any(p["source"]["brandSha256"] != digest for p in accepted):
            raise ValueError("Source brand hash differs from base index")
        names = {p["brand"] for p in accepted}
        if len(names) != 1:
            raise ValueError("Source brand file has conflicting display labels")
        data = json.loads(raw_brand).get("data")
        if not isinstance(data, dict):
            raise ValueError("Source brand lacks metadata")
        by_model = {p["source"]["modelId"]: p for p in accepted}
        if len(by_model) != len(accepted):
            raise ValueError("Repeated source model in one brand association")
        ordered = [by_model[m] for m in model_order(data) if m in by_model]
        selected = {p["id"] for p in ordered}
        # Retain every accepted profile even if a future source has an
        # unreferenced/detached association not covered by its tree.
        ordered.extend(p for p in accepted if p["id"] not in selected)
        records = []
        for profile in ordered:
            if profile["id"] in seen_ids:
                raise ValueError("Repeated catalogue ID in matching order")
            seen_ids.add(profile["id"])
            records.append({"id": profile["id"], "modelId": profile["source"]["modelId"]})
        outputs.append({"brand": accepted[0]["brand"], "category": "tv",
                        "sourceBrandFile": path, "sourceBrandSha256": digest,
                        "sourceCountry": str((data.get("tree") or {}).get("country") or ""),
                        "profiles": records})
        inputs[path] = {"bytes": len(raw_brand), "sha256": digest}
    if seen_ids != {p["id"] for p in index["profiles"] if p["category"] == "tv"}:
        raise ValueError("TV matching order changed the accepted profile set")
    output = canonical({"schemaVersion": 1,
                        "source": {"repository": "https://github.com/ysard/mi_remote_database",
                                   "release": "v2.1", "archiveSha256": ARCHIVE_SHA256,
                                   "indexSha256": sha256(raw_index), "country": "MY"},
                        "groups": outputs})
    manifest = canonical({"schemaVersion": 1, "generator": "tools/mi_matching/generate_order.py",
                          "sourceArchiveSha256": ARCHIVE_SHA256,
                          "baseIndexSha256": sha256(raw_index), "sourceBrandFiles": inputs,
                          "output": {"asset": "ir/matching_order.json", "bytes": len(output),
                                     "sha256": sha256(output)},
                          "coverage": {"groups": len(outputs), "profiles": len(seen_ids)}})
    return output, manifest


def write_or_check(output=OUTPUT, manifest=MANIFEST, check=False):
    raw, metadata = build()
    for path, content in ((output, raw), (manifest, metadata)):
        if check:
            if not path.is_file() or path.read_bytes() != content:
                raise ValueError("Generated matching metadata differs: " + str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    return json.loads(metadata)["coverage"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    args = parser.parse_args()
    try:
        result = write_or_check(args.output, args.manifest, args.check)
    except (OSError, ValueError, KeyError, tarfile.TarError) as error:
        parser.exit(1, str(error) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
