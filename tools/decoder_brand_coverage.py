#!/usr/bin/env python3
"""List all bundled decoder brands and candidates; never change IR waveforms."""
import argparse
from pathlib import Path
from tv_brand_coverage import coverage

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    destination = root / "downloads/Decoder-brand-coverage.csv"
    expected = coverage(root, "set_top_box")
    if args.check:
        if not destination.is_file() or destination.read_text() != expected:
            raise SystemExit("Decoder coverage differs; regenerate it.")
        print("Decoder brand coverage matches bundled indexes.")
    else:
        destination.write_text(expected)
        print(f"Wrote {destination.relative_to(root)}")
