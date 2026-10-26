#!/usr/bin/env python3
"""Inventory existing AC controls and group missing encoders. Never execute source hooks."""
import argparse
import csv
import hashlib
import io
import json
from collections import defaultdict
from pathlib import Path

FAMILY_MODELS={"22_3577", "kk_3_123_12255", "kk_3_58_4871", "kk_3_125_4745"}

def render(rows):
    output=io.StringIO(newline="")
    csv.writer(output,lineterminator="\n").writerows(rows)
    return output.getvalue()

def reports(root):
    assets=root/"app/src/main/assets"
    brands={}
    def brand(name):
        return brands.setdefault(name.casefold(),{"name":name,"raw":0,"parameter":0,"experimental":0,"source":0,"unavailable":0})
    for source in ("mi","mi_stb","mi_apk"):
        for p in json.loads((assets/f"ir/{source}/index.json").read_text())["profiles"]:
            if p["category"]=="air_conditioner":brand(p["brand"])["raw"]+=1
    for p in json.loads((assets/"ir/panasonic_ac/index.json").read_text())["profiles"]:
        brand(p["brand"])["parameter"]+=1
    expanded={p["model"] for p in json.loads((root/"tools/ac_expanded/approved.json").read_text())}
    groups=defaultdict(list)
    for p in json.loads((assets/"ir/mi_ac_database/index.json").read_text())["profiles"]:
        row=brand(p["brand"]);row["source"]+=1
        if p["model"] in expanded:row["experimental"]+=2
        elif p["model"] in FAMILY_MODELS:row["experimental"]+=1
        elif p["control"]["kind"]=="unavailable":
            row["unavailable"]+=1
            data=(assets/p["asset"]).read_bytes()
            if hashlib.sha256(data).hexdigest()!=p["assetSha256"]:raise ValueError("AC source hash differs")
            definition=json.loads(data)["definition"]
            template=str(definition.get("1002",""))
            try:
                packet=bytes.fromhex(template)
                signature=(len(packet)-1,packet[1:4].hex()) if packet else (-1,"unknown")
            except ValueError:signature=(-1,"non-hex")
            groups[signature].append(p)
    coverage=[["brand","recorded_candidates","parameter_candidates","experimental_candidates","sendable_candidates","source_records","source_definitions_without_encoder","guided_testing_entry","testing_status","physical_compatibility_verified"]]
    for key in sorted(brands):
        b=brands[key];total=b["raw"]+b["parameter"]+b["experimental"]
        coverage.append([b["name"],b["raw"],b["parameter"],b["experimental"],total,b["source"],b["unavailable"],"available","candidates_available" if total else "no_encoder_available","false"])
    grouping=[["template_payload_bytes","first_three_payload_bytes","profiles_without_encoder","brand_count","brands","source_models","status"]]
    for (length,prefix),profiles in sorted(groups.items(),key=lambda x:(-len(x[1]),x[0])):
        names=sorted({p["brand"] for p in profiles})
        grouping.append([length,prefix,len(profiles),len(names),"|".join(names),"|".join(p["model"] for p in profiles),"Unverified grouping only; shared prefix is not proof of protocol compatibility"])
    assert len(brands)==64 and sum(b["unavailable"] for b in brands.values())==624
    return {"AC-brand-coverage.csv":render(coverage),"AC-encoder-groups.csv":render(grouping)}

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--check",action="store_true");args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    for name,expected in reports(root).items():
        destination=root/"downloads"/name
        if args.check:
            if not destination.is_file() or destination.read_text()!=expected:raise SystemExit(f"{name} differs; regenerate it.")
        else:destination.write_text(expected)
    print("AC coverage and unverified encoder groupings "+("checked." if args.check else "generated."))
