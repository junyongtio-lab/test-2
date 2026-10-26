#!/usr/bin/env python3
"""Reproduce packet oracles and strictly decode the exported Java waveforms."""
import argparse,hashlib,json,pathlib,subprocess,tempfile
from check_sources import check

ROOT=pathlib.Path(__file__).resolve().parent
PIN='1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f'
p=argparse.ArgumentParser();p.add_argument('--reference',type=pathlib.Path,required=True);p.add_argument('--waveforms',type=pathlib.Path);args=p.parse_args()
reference=args.reference.resolve();manifest=json.loads((ROOT/'reference-files.json').read_text())
assert manifest['commit']==PIN
for name,expected in manifest['files'].items():
    data=(reference/name).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==expected,'Reference source differs: '+name
rows=check()
with tempfile.TemporaryDirectory(prefix='palm-expanded-reference-') as tmp:
    tmp=pathlib.Path(tmp);input_file=tmp/'input.csv'
    input_file.write_text(''.join(r['model']+','+r['template']+'\n' for r in rows))
    for name in ['oracle']+(['decode'] if args.waveforms else []):
        sources=['ir_Mitsubishi','ir_Tcl','IRsend','IRutils','IRtimer','IRtext']+(['IRrecv'] if name=='decode' else [])
        output=tmp/name
        subprocess.run(['g++','-std=c++11','-DUNIT_TEST','-ffunction-sections','-fdata-sections','-Wl,--gc-sections','-I'+str(reference/'src'),'-I'+str(reference/'test'),str(ROOT/(name+'.cpp'))]+[str(reference/'src'/(s+'.cpp')) for s in sources]+['-o',str(output)],check=True)
        if name=='oracle':assert subprocess.check_output([str(output),str(input_file)])==(ROOT/'oracle.csv').read_bytes(),'Packet oracle differs'
        else:subprocess.run([str(output),str(args.waveforms.resolve()),str(len(rows)*120)],check=True)
print('Verified 4,200 packets against both public encoders'+(' and 8,400 strict Java waveform decodes.' if args.waveforms else '.'))
