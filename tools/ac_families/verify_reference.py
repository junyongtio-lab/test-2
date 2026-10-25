#!/usr/bin/env python3
"""Rebuild independent packet expectations and decode exported Android waveforms."""
import argparse,pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parent
PIN='1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=pathlib.Path,required=True);p.add_argument('--waveforms',type=pathlib.Path);a=p.parse_args()
ref=a.reference.resolve()
assert subprocess.check_output(['git','-C',str(ref),'rev-parse','HEAD'],text=True).strip()==PIN,'Unexpected upstream reference'
sources=['ir_LG','ir_Mitsubishi','ir_Daikin','ir_Samsung','IRsend','IRutils','IRtimer','IRtext']
with tempfile.TemporaryDirectory(prefix='palm-ac-reference-') as tmp:
 for name in ['oracle']+(['decode'] if a.waveforms else []):
  output=pathlib.Path(tmp)/name
  units=sources+(['IRrecv'] if name=='decode' else [])
  subprocess.run(['g++','-std=c++11','-DUNIT_TEST','-ffunction-sections','-fdata-sections','-Wl,--gc-sections','-I'+str(ref/'src'),'-I'+str(ref/'test'),str(ROOT/(name+'.cpp'))]+[str(ref/'src'/(unit+'.cpp')) for unit in units]+['-o',str(output)],check=True)
  if name=='oracle':assert subprocess.check_output([str(output)])==(ROOT/'oracle.csv').read_bytes(),'Reference packet fixture differs'
  else:subprocess.run([str(output),str(a.waveforms.resolve())],check=True)
print('Verified 960 reference packets'+(' and 960 strict waveform decodes' if a.waveforms else ''))
