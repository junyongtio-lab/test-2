#!/usr/bin/env python3
"""Reproduce packet oracles and strictly decode the exported Java waveforms."""
import argparse,hashlib,json,pathlib,subprocess,tempfile
from check_sources import check
from control_vectors import render,definitions

ROOT=pathlib.Path(__file__).resolve().parent
PIN='1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f'
p=argparse.ArgumentParser();p.add_argument('--reference',type=pathlib.Path,required=True);p.add_argument('--waveforms',type=pathlib.Path);p.add_argument('--control-waveforms',type=pathlib.Path);args=p.parse_args()
reference=args.reference.resolve();manifest=json.loads((ROOT/'reference-files.json').read_text())
assert manifest['commit']==PIN
for name,expected in manifest['files'].items():
    data=(reference/name).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==expected,'Reference source differs: '+name
rows=check()
fixtures,counts=render()
for path,data in fixtures.items():assert path.read_text()==data,'Source-table fixture differs: '+str(path)
with tempfile.TemporaryDirectory(prefix='palm-expanded-reference-') as tmp:
    tmp=pathlib.Path(tmp);input_file=tmp/'input.csv'
    input_file.write_text(''.join(r['model']+','+r['template']+'\n' for r in rows))
    for name in ['oracle','controls_reference']+(['decode'] if args.waveforms or args.control_waveforms else []):
        sources=['ir_Mitsubishi','ir_Tcl','IRsend','IRutils','IRtimer','IRtext']+(['IRrecv'] if name=='decode' else [])
        output=tmp/name
        subprocess.run(['g++','-std=c++11','-DUNIT_TEST','-ffunction-sections','-fdata-sections','-Wl,--gc-sections','-I'+str(reference/'src'),'-I'+str(reference/'test'),str(ROOT/(name+'.cpp'))]+[str(reference/'src'/(s+'.cpp')) for s in sources]+['-o',str(output)],check=True)
        if name=='oracle':assert subprocess.check_output([str(output),str(input_file)])==(ROOT/'oracle.csv').read_bytes(),'Packet oracle differs'
        elif name=='controls_reference':
            controls_input=tmp/'controls-input.csv'
            controls_input.write_text(''.join(r['model']+','+d['1002'][2:]+','+d['1015']+'\n' for r,d in definitions()))
            result=subprocess.check_output([str(output),str(controls_input),str(ROOT/'control-vectors.csv')],text=True)
            expected=sum(not line.split(',')[5] for line in (ROOT/'control-vectors.csv').read_text().splitlines())
            assert result==f'Matched {expected} fan/swing packet combinations to the pinned public TCL encoder.\n';print(result,end='')
        else:
            if args.waveforms:subprocess.run([str(output),str(args.waveforms.resolve()),str(len(rows)*120)],check=True)
            if args.control_waveforms:subprocess.run([str(output),str(args.control_waveforms.resolve()),str(counts['waveformCases'])],check=True)
print('Verified '+str(counts['packetCases'])+' independent source-table control packets; '+str(counts['waveformCases'])+' additional waveforms requested='+str(bool(args.control_waveforms)))
print('Verified 4,200 packets against both public encoders'+(' and 8,400 strict Java waveform decodes.' if args.waveforms else '.'))
