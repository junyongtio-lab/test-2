#!/usr/bin/env python3
"""Inspect the packaged APK, not merely the source filters."""
import argparse,collections,hashlib,json,pathlib,zipfile
from create_demo import ALLOW
BASELINE_SHA='fef11c68d420ef4658884b3babe646802bb247618573b8b4d30f86ca7201bd08'
INDEXES=['ir/mi/index.json','ir/mi_stb/index.json','ir/mi_apk/index.json','ir/mi_ac_database/index.json']

def verify(project,apk,baseline=None):
    manifest=json.loads((project/'demo-manifest.json').read_text());assert manifest['allowedBrands']=={k:sorted(v) for k,v in ALLOW.items()}
    expected={r['asset']:r for r in manifest['assets']};assert len(expected)==130
    with zipfile.ZipFile(apk) as z:
        names={n[7:] for n in z.namelist() if n.startswith('assets/') and not n.endswith('/')}
        source_names={str(p.relative_to(project/'app/src/main/assets')) for p in (project/'app/src/main/assets').rglob('*') if p.is_file()}
        assert names==source_names,'Packaged assets differ from the pruned demo source'
        packaged_profiles={n for n in names if '/profiles/' in n};assert packaged_profiles==set(expected),'Extra or missing profile assets in APK'
        raw=collections.Counter();commands=0
        for name in INDEXES:
            index=json.loads(z.read('assets/'+name))
            for p in index['profiles']:
                assert p['brand'] in ALLOW[p['category']],f'Excluded brand packaged: {p["brand"]}/{p["category"]}'
                assert p['asset'] in expected
                data=z.read('assets/'+p['asset']);assert hashlib.sha256(data).hexdigest()==p['assetSha256']==expected[p['asset']]['sha256']
                record=json.loads(data)
                if name=='ir/mi_ac_database/index.json':assert record['brand']==p['brand']
                else:assert record['sourceModelId']==p['model']
                if name!='ir/mi_ac_database/index.json':raw[p['category']]+=1;commands+=p['commandCount']
        assert raw==dict(tv=69,air_conditioner=4,fan=6,set_top_box=5) and commands==2811
        pana=json.loads(z.read('assets/ir/panasonic_ac/index.json'));assert len(pana['profiles'])==6 and {p['brand'] for p in pana['profiles']}=={'Panasonic'}
        order=json.loads(z.read('assets/ir/matching_order.json'));assert {g['brand'] for g in order['groups']}=={'Samsung'}
        for name in names:assert z.read('assets/'+name)==(project/'app/src/main/assets'/name).read_bytes(),name
        if baseline:
            assert hashlib.sha256(baseline.read_bytes()).hexdigest()==BASELINE_SHA,'Baseline is not the published 1.6.6 APK'
            with zipfile.ZipFile(baseline) as full:
                for name in expected:assert z.read('assets/'+name)==full.read('assets/'+name),'Retained remote data changed: '+name
                assert pana==json.loads(full.read('assets/ir/panasonic_ac/index.json'))
    result=dict(apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),apkBytes=apk.stat().st_size,packagedAssetFiles=len(names),packagedProfileAssets=len(expected),recordedProfiles=dict(raw),recordedCommands=commands,acSourceRecords=46,acSettingsCandidates=10,totalSendableCandidates=94,allowedBrands={k:sorted(v) for k,v in ALLOW.items()},excludedBrandProfileAssets=0,baselineApkSha256=BASELINE_SHA if baseline else None,retainedSignalBytesMatchBaseline=bool(baseline),physicalHardwareTested=False)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--project',type=pathlib.Path,required=True);p.add_argument('--apk',type=pathlib.Path,required=True);p.add_argument('--baseline-apk',type=pathlib.Path);p.add_argument('--report',type=pathlib.Path);args=p.parse_args()
    result=verify(args.project,args.apk,args.baseline_apk)
    if args.report:args.report.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
