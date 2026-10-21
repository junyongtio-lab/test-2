#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Convert pinned non-executable Panasonic216 parameters; never evaluate scripts."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT.parent.parent / 'app/src/main/assets/ir/panasonic_ac/index.json'
SOURCE_SHA256 = '9563b41eb2a52e325caa36022fd830d2c76b964690ea87cddfaf122867fc4892'
MODELS = ('137_2587','kk_3_137_4646','kk_3_137_7406','kk_3_137_7790','kk_3_137_7791','kk_3_137_7793')

def canonical(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()

def entries(encoded, width):
    data = bytes.fromhex(encoded); result=[]; cursor=0
    while cursor < len(data):
        count=data[cursor]; cursor+=1
        if count % width or cursor+count > len(data): raise ValueError('Invalid parameter entry')
        result.append([list(data[i:i+width]) for i in range(cursor,cursor+count,width)])
        cursor+=count
    return result

def durations(value):
    result=[int(x) for x in value.split(',')]
    if not result or any(x < 1 or x > 1000000 for x in result): raise ValueError('Invalid timing')
    return result

def build(source=ROOT/'source/parameters.json'):
    raw=source.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SOURCE_SHA256: raise ValueError('Parameter source hash differs')
    rows=json.loads(raw)['records']
    if tuple(x['model'] for x in rows)!=MODELS: raise ValueError('Source model identities differ')
    profiles=[]
    for row in rows:
        p=row['parameters']; model=row['model']; template=bytes.fromhex(p['1002'])
        if template[0]!=27 or len(template)!=28 or p['306']!='1' or p['1008']!='0401081A1A': raise ValueError('Unsupported Panasonic format')
        mode_entries=entries(p['1004'],2)
        if len(mode_entries)!=5: raise ValueError('Unsupported mode table')
        modes={name:patches for i,name in enumerate(('cool','heat','auto','fan','dry'))
               if name!='auto' and (patches:=mode_entries[i])}
        for patches in modes.values():
            if any(offset not in (13,14) for offset,value in patches): raise ValueError('Unsupported mode patch')
        temps=entries(p['1003'],2)
        if len(temps)==1 and temps[0]==[[14,2]]:
            temperatures={str(t):[[14,t*2]] for t in range(16,31)}
        elif len(temps)==15 and all(len(x)==1 and x[0][0]==14 for x in temps):
            temperatures={str(t):temps[t-16] for t in range(16,31)}
        else: raise ValueError('Unsupported temperature table')
        fans=entries(p['1005'],2) if '1005' in p else []
        if fans and (len(fans)!=4 or any(len(x)!=1 or x[0][0]!=16 for x in fans)): raise ValueError('Unsupported fan table')
        swings=entries(p['1015'],3)
        if len(swings)!=6 or any(not 0<=start<end<=216 or end-start>8 for group in swings for start,end,value in group): raise ValueError('Unsupported swing table')
        delay=p['303'].split('&')
        if delay[0]!='7' or len(delay)!=2: raise ValueError('Unsupported frame boundary')
        between=durations(delay[1])
        if len(between)!=4: raise ValueError('Unsupported frame separator')
        toggle=model=='kk_3_137_7793'
        if toggle != ('1001' not in p): raise ValueError('Unexpected power representation')
        if not toggle and p['1001'] not in ('00020D30','00020D38'): raise ValueError('Unsupported power mapping')
        profiles.append({'id':'pana_'+model,'model':model,'brand':'Panasonic','category':'air_conditioner',
            'carrierHz':row['carrierHz'],'hardwareTested':False,'powerToggle':toggle,
            'supportsFan':bool(fans),'supportsSwing':bool(swings),'modes':list(modes),
            'templateHex':template[1:].hex(),'modePatches':modes,'temperaturePatches':temperatures,
            'fanPatches':fans,'swingPatches':swings,'lead':durations(p['300']),
            'zero':durations(p['301']),'one':durations(p['302']),'between':between,'trailingGap':1000,
            'source':row['source']})
    return canonical({'schemaVersion':1,'kind':'panasonic216-parameters','sourceParameterSha256':SOURCE_SHA256,
        'scriptsExecuted':False,'profiles':profiles})

def write_or_check(output=OUTPUT, source=ROOT/'source/parameters.json', check=False):
    raw=build(source)
    if check:
        if not output.is_file() or output.read_bytes()!=raw:
            raise ValueError('Panasonic assets differ from pinned parameters')
    else:
        output.parent.mkdir(parents=True,exist_ok=True)
        output.write_bytes(raw)
    return raw

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    try: write_or_check(check=args.check)
    except ValueError as error: raise SystemExit(str(error)) from error
    print('Verified six non-executable Panasonic216 parameter profiles' if args.check else 'Generated six Panasonic216 profiles')

if __name__=='__main__': main()
