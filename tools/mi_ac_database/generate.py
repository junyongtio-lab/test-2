#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Losslessly bundle all 734 AC records from the pinned Mi 7.2.4M APK as inert data.

No vendor program, native library, SQL script or parameter hook is executed.
Data inclusion and implemented Android transmission support are separate counts.
"""
from __future__ import annotations
import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import tempfile

ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
sys.path.insert(0, str(REPOSITORY/'tools/mi_apk_catalogue'))
import generate_apk as source

OUTPUT = REPOSITORY/'app/src/main/assets/ir/mi_ac_database'
PANASONIC = {'137_2587', 'kk_3_137_4646', 'kk_3_137_7406'}


def build(db):
    brands = {str(x['ID']):x for x in source.rows(db, 'brand')}
    refs = {str(x['ID']):x for x in source.rows(db, 'kk_remote_ircode_src')}
    outputs, profiles = {}, []
    for table in source.TABLES:
        for row in source.rows(db, table):
            if int(row['DEVICE_ID']) != 3:
                continue
            model, brand_id = str(row['ID']), str(row['BRAND_ID'])
            if not source.ID_PATTERN.fullmatch(model) or brand_id not in brands:
                raise ValueError('AC model or brand identity is unavailable')
            brand_row = brands[brand_id]
            brand = source.brand_name(brand_row)
            referenced = refs.get(str(row['IR_ZIP_KEY'])) if int(row['INDEXED']) == 1 else None
            if int(row['INDEXED']) == 1 and referenced is None:
                raise ValueError('AC source reference is missing')
            definition = source.local.decode_json((referenced or row)['IR_ZIP_KEY'])
            if not isinstance(definition, dict) or not definition:
                raise ValueError('AC definition is not a complete dictionary')
            stateful = table == 'kk_remote_ircode' and int(row['EXT_FLAG']) != 0
            kind = 'stateful' if stateful else 'recorded'
            identity = source.local.sha256(source.canonical(['mi724-ac-database', table, model, brand_id]))[:24]
            ident = 'ac724_'+identity
            asset = 'profiles/'+ident+'.json'
            raw_id = 'mi_'+source.local.sha256(source.canonical(['mi724-apk', table, model, brand_id, 3]))[:24]
            control = {'kind':'unavailable'} if stateful else {'kind':'recorded', 'profile':raw_id}
            if stateful and model in PANASONIC:
                control = {'kind':'panasonic216', 'profile':'pana_'+model}
            payload = {
                'schemaVersion':1, 'id':ident, 'model':model, 'brand':brand,
                'sourceTable':table, 'sourceRow':row, 'referencedSourceRow':referenced,
                'brandSourceRow':brand_row, 'brandData':source.local.decode_json(brand_row['CONTENT']),
                'definition':definition, 'programsExecuted':False,
            }
            raw = source.canonical(payload)
            if len(raw) > 512*1024 or asset in outputs:
                raise ValueError('Unexpected AC data asset size or duplicate identity')
            outputs[asset] = raw
            profiles.append({
                'id':ident, 'model':model, 'brand':brand, 'brandId':brand_id,
                'category':'air_conditioner', 'carrierHz':int(row['FREQUENCY']),
                'sourceTable':table, 'sourceVersion':int(row['VERSION']),
                'sourceRecordSha256':source.source_hash(row, referenced),
                'brandRecordSha256':source.local.sha256(source.canonical(brand_row)),
                'definitionKind':kind, 'control':control, 'hardwareTested':False,
                'asset':'ir/mi_ac_database/'+asset, 'assetSha256':source.local.sha256(raw),
                'definitionFieldCount':len(definition),
            })
    profiles.sort(key=lambda p:(p['brand'].casefold(),p['model'],p['id']))
    counts = Counter(p['definitionKind'] for p in profiles)
    controls = Counter(p['control']['kind'] for p in profiles)
    if len(profiles)!=734 or counts!={'recorded':33,'stateful':701} or controls!={'recorded':33,'panasonic216':3,'unavailable':698}:
        raise ValueError('Pinned complete AC source coverage differs')
    coverage = {'profiles':734, 'brands':len({p['brand'] for p in profiles}),
                'recorded':33, 'stateful':701, 'controlsImplemented':36,
                'controlsUnavailable':698, 'sourceRecordsOmitted':0}
    index = {'schemaVersion':1, 'kind':'mi724-complete-ac-source-data',
             'source':{'versionName':'7.2.4M','versionCode':6500,
                       'apkSha256':source.local.APK_SHA256,'databaseSha256':source.local.DB_SHA256,
                       'archiveSha256':'72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6'},
             'programsExecuted':False,'coverage':coverage,'profiles':profiles}
    outputs['index.json'] = source.canonical(index)
    return outputs, coverage


def write_or_check(outputs, output=OUTPUT, check=False):
    if check:
        actual={str(p.relative_to(output)) for p in output.rglob('*') if p.is_file()}
        if actual!=set(outputs): raise ValueError('Complete AC database asset membership differs')
        for name, raw in outputs.items():
            if (output/name).read_bytes()!=raw: raise ValueError('Complete AC source data differs: '+name)
    else:
        for name, raw in outputs.items():
            target=output/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    manifest=source.canonical({'schemaVersion':1,'generator':'tools/mi_ac_database/generate.py',
                              'outputs':{n:{'bytes':len(v),'sha256':source.local.sha256(v)} for n,v in sorted(outputs.items())}})
    path=ROOT/'generated-manifest.json'
    if check:
        if path.read_bytes()!=manifest: raise ValueError('Complete AC source manifest differs')
    else:path.write_bytes(manifest)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='palm-complete-ac-') as directory:
        apk=args.apk or source.extract_verified_archive(source.DEFAULT_ARCHIVE,Path(directory)/'MiRemote.apk')
        db=source.open_database(apk)
        try:outputs,coverage=build(db)
        finally:db.close()
        write_or_check(outputs,check=args.check)
    print(json.dumps({'mode':'checked' if args.check else 'generated','coverage':coverage,'vendorProgramsExecuted':False},indent=2))


if __name__=='__main__':main()
