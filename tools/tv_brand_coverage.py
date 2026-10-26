#!/usr/bin/env python3
"""List bundled TV candidates without inventing or modifying command signals."""
import csv
import io
import json
from pathlib import Path


def coverage(root):
    brands = {}
    for source in ("mi", "mi_apk"):
        index = json.loads((root / f"app/src/main/assets/ir/{source}/index.json").read_text())
        for profile in index["profiles"]:
            if profile["category"] != "tv":
                continue
            name = profile["brand"]
            row = brands.setdefault(name.casefold(), {"brand": name, "profiles": []})
            row["profiles"].append(profile)
    output = io.StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(["brand", "candidate_remotes", "minimum_recorded_buttons", "maximum_recorded_buttons", "guided_testing", "physical_compatibility_verified"])
    for key in sorted(brands):
        row = brands[key]
        counts = [profile["commandCount"] for profile in row["profiles"]]
        writer.writerow([row["brand"], len(counts), min(counts), max(counts), "available", "false"])
    return output.getvalue()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    destination = root / "downloads/TV-brand-coverage.csv"
    expected = coverage(root)
    if args.check:
        if not destination.is_file() or destination.read_text() != expected:
            raise SystemExit("TV brand coverage differs; regenerate it.")
        print("TV brand coverage matches bundled indexes.")
    else:
        destination.write_text(expected)
        print(f"Wrote {destination.relative_to(root)}")
