#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Verify numeric Panasonic parameters directly against both pinned source archives.

This reads the vendor inputs as data. No AC program, native library, DEX or Lua
code is executed, imported or included in the generated Android parameters.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parent.parent
sys.path.insert(0, str(REPOSITORY / 'tools/mi_apk_catalogue'))
import generate_apk as apk_source
import generate

GITHUB_ARCHIVE = REPOSITORY / 'tools/mi_catalogue/source/database_dump-v2.1.tar.xz'
GITHUB_BYTES = 7_892_348
GITHUB_SHA256 = '44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895'
NUMERIC_TAGS = {'1001','1002','1003','1004','1005','1008','1015','1506','300','301','302','303','305','306'}


def verify_parameters(snapshot, actual):
    if not isinstance(snapshot, dict) or not snapshot or not set(snapshot) <= NUMERIC_TAGS:
        raise ValueError('Snapshot must contain only supported numeric parameter tags')
    if not isinstance(actual, dict):
        raise ValueError('Source parameter dictionary is missing')
    for tag, value in snapshot.items():
        if not isinstance(value, str) or actual.get(tag) != value:
            raise ValueError('Numeric parameter differs from its pinned source: ' + tag)


def verify_with_apk(apk, github_archive=GITHUB_ARCHIVE, snapshot=ROOT/'source/parameters.json'):
    # The generator independently checks the snapshot byte hash and model order.
    generate.build(snapshot)
    records=json.loads(snapshot.read_bytes())['records']
    with apk_source.open_database(apk) as db:
        for record in records[:3]:
            source=record['source']
            row=db.execute('SELECT * FROM kk_remote_ircode WHERE ID=?',(record['model'],)).fetchone()
            if row is None: raise ValueError('Panasonic model row is missing')
            row=dict(row)
            if (str(row['BRAND_ID'])!='137' or str(row['DEVICE_ID'])!='3'
                    or int(row['INDEXED'])!=1 or int(row['EXT_FLAG'])==0
                    or int(row['FREQUENCY'])!=record['carrierHz']
                    or int(row['VERSION'])!=source['rowVersion']):
                raise ValueError('Uploaded-APK model identity differs')
            referenced=db.execute('SELECT * FROM kk_remote_ircode_src WHERE ID=?',(row['IR_ZIP_KEY'],)).fetchone()
            if referenced is None: raise ValueError('Referenced Panasonic parameter row is missing')
            referenced=dict(referenced)
            if str(referenced['ID'])!=source['parameterRowId']:
                raise ValueError('Panasonic parameter reference differs')
            if apk_source.source_hash(row,referenced)!=source['sourceSha256']:
                raise ValueError('Uploaded-APK model/source-row hash differs')
            verify_parameters(record['parameters'],apk_source.local.decode_json(referenced['IR_ZIP_KEY']))
    if github_archive.stat().st_size!=GITHUB_BYTES:
        raise ValueError('Pinned GitHub archive size differs')
    with github_archive.open('rb') as stream:
        if hashlib.file_digest(stream,'sha256').hexdigest()!=GITHUB_SHA256:
            raise ValueError('Pinned GitHub archive hash differs')
    wanted={record['source']['sourceFile']:record for record in records[3:]}
    seen=set()
    with tarfile.open(github_archive,'r|xz') as archive:
        for member in archive:
            if member.name not in wanted: continue
            if member.name in seen or not member.isfile() or not 1<=member.size<=2*1024*1024:
                raise ValueError('Unexpected Panasonic model archive entry')
            seen.add(member.name)
            raw=archive.extractfile(member).read();record=wanted[member.name]
            if hashlib.sha256(raw).hexdigest()!=record['source']['sourceSha256']:
                raise ValueError('Pinned GitHub model hash differs')
            data=json.loads(raw)['data']
            if data['_id']!=record['model'] or data['type']!=2 or data['frequency']!=record['carrierHz']:
                raise ValueError('Pinned GitHub model identity differs')
            verify_parameters(record['parameters'],data['key'])
    if seen!=set(wanted): raise ValueError('Pinned GitHub Panasonic records are missing')
    return {'verifiedProfiles':len(records),'uploadedApkProfiles':3,'githubArchiveProfiles':3,
            'numericParametersMatchSource':True,'scriptsExecuted':False,
            'physicalCompatibilityVerified':False}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,help='Optional exact verified APK; otherwise unpack the tracked archive temporarily')
    args=parser.parse_args()
    if args.apk:
        report=verify_with_apk(args.apk)
    else:
        with tempfile.TemporaryDirectory(prefix='palm-panasonic-source-') as directory:
            path=apk_source.extract_verified_archive(apk_source.DEFAULT_ARCHIVE,Path(directory)/'MiRemote.apk')
            report=verify_with_apk(path)
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
