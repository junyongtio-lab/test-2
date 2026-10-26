#!/usr/bin/env python3
"""Check the explicit reviewed source allowlist. Does not auto-approve new profiles."""
import csv,hashlib,io,json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

def check():
    rows=json.loads((ROOT/'tools/ac_expanded/approved.json').read_text())
    assert len(rows)==70 and len({r['model'] for r in rows})==70
    index=json.loads((ROOT/'app/src/main/assets/ir/mi_ac_database/index.json').read_text())
    metadata={p['model']:p for p in index['profiles']}
    lines=[]
    for row in rows:
        p=metadata[row['model']]
        data=(ROOT/'app/src/main/assets'/p['asset']).read_bytes()
        assert hashlib.sha256(data).hexdigest()==p['assetSha256']==row['assetSha256']
        definition=json.loads(data)['definition']
        assert definition['1002'][2:].lower()==row['template']
        assert definition['1008']=='0401000D0D' and '303' not in definition
        assert len(bytes.fromhex(row['template']))==14
        lines.append('        approve("'+row['model']+'","'+row['assetSha256']+'");')
    java=(ROOT/'app/src/main/java/com/universalremote/app/ExpandedAcEncoder.java').read_text()
    approved=java.split('// BEGIN APPROVED SOURCES\n')[1].split('        // END APPROVED SOURCES')[0]
    assert approved=='\n'.join(lines)+'\n','Runtime source allowlist differs'
    output=io.StringIO(newline='')
    writer=csv.writer(output,lineterminator='\n')
    writer.writerow(['brand','source_profile_id','controls_implemented','timing_variants','not_implemented','hardware_tested','source_asset_sha256'])
    for row in sorted(rows,key=lambda r:(r['brand'].lower(),r['model'])):
        writer.writerow([row['brand'],row['model'],'Power on/off; Cool/Heat; 16–30 °C',2,'Fan speed; swing; timers; special functions','false',row['assetSha256']])
    report=ROOT/'downloads/AC-expanded-covered-profiles.csv'
    report.write_text(output.getvalue())
    print('Checked 70 explicitly approved, unchanged source definitions. No hook executed.')
    return rows

if __name__=='__main__':check()
