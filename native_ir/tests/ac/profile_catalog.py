#!/usr/bin/env python3
"""Read the actual Java A/C catalogue for native integration tests.

No second copy of protocol/model mappings is maintained here. Use --tsv for
native test drivers or --check to validate identifiers and protocol coverage.
"""

import argparse
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "app/src/main/java/com/universalremote/app/ac/AcProfiles.java"
SUPPORTED = {
    "AIRTON", "AIRWELL", "AMCOR", "ARGO", "BOSCH144", "CARRIER_AC64",
    "COOLIX", "CORONA_AC", "DAIKIN", "DAIKIN128", "DAIKIN152", "DAIKIN160",
    "DAIKIN176", "DAIKIN2", "DAIKIN216", "DAIKIN312", "DAIKIN64", "DELONGHI_AC",
    "ECOCLIM", "ELECTRA_AC", "EUROM", "FUJITSU_AC", "GOODWEATHER", "GREE",
    "HAIER_AC", "HAIER_AC160", "HAIER_AC176", "HAIER_AC_YRW02", "HITACHI_AC",
    "HITACHI_AC1", "HITACHI_AC264", "HITACHI_AC296", "HITACHI_AC344",
    "HITACHI_AC424", "KELON", "KELVINATOR", "LG", "LG2", "MIDEA", "MIRAGE",
    "MITSUBISHI_AC", "MITSUBISHI112", "MITSUBISHI136", "MITSUBISHI_HEAVY_88",
    "MITSUBISHI_HEAVY_152", "NEOCLIMA", "PANASONIC_AC", "PANASONIC_AC32",
    "RHOSS", "SAMSUNG_AC", "SANYO_AC", "SANYO_AC88", "SHARP_AC", "TCL112AC",
    "TECHNIBEL_AC", "TECO", "TEKNOPOINT", "TOSHIBA_AC", "TRANSCOLD", "TROTEC",
    "TROTEC_3550", "TRUMA", "VESTEL_AC", "VOLTAS", "WHIRLPOOL_AC",
}
# Numeric values come from pinned IRsend.h. Toshiba's common API currently
# ignores model, so only the actual default model is exposed.
MODELS = {
    "ARGO": {1, 2}, "FUJITSU_AC": set(range(1, 7)), "GREE": {1, 2, 3},
    "HAIER_AC176": {1, 2}, "HITACHI_AC1": {1, 2}, "LG": {1, 5},
    "LG2": {2, 3, 4}, "MIRAGE": {1, 2}, "PANASONIC_AC": set(range(1, 7)),
    "SHARP_AC": {1, 2, 3}, "TCL112AC": {1, 2}, "TEKNOPOINT": {2},
    "TOSHIBA_AC": {0}, "VOLTAS": {0, 1}, "WHIRLPOOL_AC": {1, 2},
}


def profiles():
    literal = r'("(?:[^"\\]|\\.)*")'
    pattern = re.compile(
        r"add\(profiles,\s*" + literal + r",\s*" + literal + r",\s*"
        + literal + r",\s*" + literal + r",\s*(-?\d+),\s*" + literal + r"\);"
    )
    rows = []
    source = JAVA.read_text()
    for match in pattern.finditer(source):
        fields = [json.loads(match[i]) for i in (1, 2, 3, 4)]
        rows.append(dict(zip(("id", "brand", "name", "protocol"), fields),
                         model=int(match[5]), notes=json.loads(match[6])))
    assert len(rows) == source.count("add(profiles,"), "Unparsed catalogue entry"
    assert len({row["id"] for row in rows}) == len(rows), "Duplicate profile id"
    assert {row["protocol"] for row in rows} == SUPPORTED, "Protocol coverage changed"
    for row in rows:
        assert re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", row["id"]), row["id"]
        assert row["model"] in MODELS.get(row["protocol"], {-1}), row
        assert row["brand"] and row["name"] and row["notes"], row
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tsv", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rows = profiles()
    if args.check:
        print(f"{len(rows)} profiles, {len({r['brand'] for r in rows})} brands, "
              f"{len(SUPPORTED)} implemented protocol families: catalogue checks passed")
    elif args.tsv:
        for row in rows:
            print("\t".join(str(row[key]) for key in ("id", "protocol", "model", "brand", "name")))
    else:
        print(json.dumps(rows, indent=2, ensure_ascii=False))
