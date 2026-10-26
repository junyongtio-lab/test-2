#!/usr/bin/env python3
"""Independent source-table packet fixtures. Never evaluates vendor hook text.

The three reviewed timer hooks are translated explicitly. Their 10-minute native
counts differ from the public TCL timer API. Source-table expectations establish
packet conformance, not appliance behavior or exact Mi engine equivalence.
"""
import argparse,csv,hashlib,io,itertools,json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
HOOKS={'543afbc395881cc6ac8e9b1f1dfa89b73fd18376de8875432752d5274670bc4f':(True,True),
       '216038662958f8bc1d58d6c12732a831c9d056b6bc1ceb3a9cb73869b13fffa2':(False,True),
       'a7cb87600865c3018da55ef44be1501c54a5b2afb5d908fc20238fcd8c65bbef':(True,False)}
NAMES={8:'Turbo',12:'Auxiliary heat',13:'Health / filter',18:'Energy saver',21:'Horizontal swing',22:'Sleep',26:'I Feel / body sensor',28:'Eco',34:'Display light'}

def definitions():
    assets=ROOT/'app/src/main/assets';index=json.loads((assets/'ir/mi_ac_database/index.json').read_text());metadata={p['model']:p for p in index['profiles']}
    for row in json.loads((ROOT/'tools/ac_expanded/approved.json').read_text()):
        data=(assets/metadata[row['model']]['asset']).read_bytes();assert hashlib.sha256(data).hexdigest()==row['assetSha256']
        yield row,json.loads(data)['definition']

def describe(d):
    def patches(key):
        b=bytes.fromhex(d[key]);assert len(b)%4==0
        return [tuple(b[i+1:i+4]) for i in range(0,len(b),4)]
    limits={int(f[0]):f for part in d.get('1515','').split('@') if len(f:=part.split('|'))==4}
    extras={}
    for part in d.get('1017','').split('@'):
        if not part:continue
        size,fid,value,start,end,patch=bytes.fromhex(part);assert size==5
        extras.setdefault(fid,{})[value]=(start,end,patch)
    hook=HOOKS.get(hashlib.sha256(d.get('1518','').encode()).hexdigest())
    assert not d.get('1518') or hook
    if hook and hook[1] and 22 not in extras:extras[22]={0:None,1:'night'}
    timer=None
    if hook:
        a,b,step=limits[9][1].replace('-',',').split(',');timer=(int(a),int(b),int(step));assert limits[9][1]==limits[10][1]
    return patches('1013'),patches('1015'),extras,limits,hook,timer

def packet(d,heat,power,fan,swing,extra):
    fans,swings,features,limits,hook,timer=describe(d)
    bits=list(''.join(f'{b:08b}' for b in bytes.fromhex(d['1002'])[1:]))
    def patch(start,end,value):bits[start:end]=list(f'{value:0{end-start}b}')
    patch(45,46,int(power));patch(52,56,1 if heat else 3);patch(60,64,7);patch(98,99,0) # Cool/Heat at 24 C; whole degrees
    if fan>=0:patch(*fans[fan])
    if swing>=0:patch(*swings[swing])
    for fid,value in sorted(extra.items()):
        if fid in (9,10):continue
        p=features[fid][value]
        if p=='night':patch(69,72,1)
        elif p:patch(*p)
        if fid==22 and value==1 and hook and hook[1]:patch(69,72,1)
    p=bytearray(int(''.join(bits[i:i+8]),2) for i in range(0,112,8))
    for fid,minutes in extra.items():
        if fid not in (9,10):continue
        assert hook
        p[5]&=~0x18;p[9]=p[10]=0
        if hook[0]:p[8]&=~0x40
        if minutes:
            p[5]=0x34 if fid==9 else 0x2c;p[10 if fid==9 else 9]=minutes//10
            if hook[0]:p[8]|=0x40
    p[13]=sum(p[:13])&255
    return p.hex()

def render():
    vectors=[];coverage=[['brand','source_profile_id','fan_speed','vertical_swing_options','timer_minutes','special_functions','declared_but_unmapped','hardware_tested']]
    counts=dict(definitions=0,fan=0,swing=0,timers=0,special=0)
    exhaustive_timers=set()
    for row,d in definitions():
        model=row['model'];fans,swings,features,limits,hook,timer=describe(d)
        counts['definitions']+=1;counts['fan']+=1;counts['swing']+=1;counts['timers']+=bool(timer);counts['special']+=bool(features)
        def add(heat,power,fan,swing,extra):
            vectors.append([model,int(heat),int(power),fan,swing,';'.join(f'{k}={v}' for k,v in sorted(extra.items())),packet(d,heat,power,fan,swing,extra)])
        for heat,power,fan,swing in itertools.product(range(2),range(2),range(4),range(len(swings))):add(heat,power,fan,swing,{})
        for fid,options in features.items():
            for heat,power,fan,value in itertools.product(range(2),range(2),range(4),options):
                if value and ('H' if heat else 'C') not in limits[fid][3]:continue
                add(heat,power,fan,len(swings)-1,{fid:value})
        # Exercise simultaneous features, including shared-byte fields.
        for heat in range(2):
            for on in range(2):
                extra={fid:(max(options) if on and ('H' if heat else 'C') in limits[fid][3] else min(options)) for fid,options in features.items()}
                if extra:add(heat,True,3,0,extra)
        if timer:
            minimum,maximum,step=timer;kind=(hook,timer)
            values={0,minimum,maximum,step*((maximum//step)//2)}
            if minimum<=60<=maximum and 60%step==0:values.add(60)
            if kind not in exhaustive_timers:values.update(range(minimum,maximum+1,step));exhaustive_timers.add(kind)
            for fid,value,heat,power in itertools.product((9,10),sorted(values),range(2),range(2)):
                add(heat,power,3,0,{fid:value})
        declared={int(f['fid']) for f in (d.get('888888') or [])}
        missing=declared-set(features)-({9,10} if timer else set())
        coverage.append([row['brand'],model,'Auto; Low; Medium; High',len(swings),f'{timer[0]}–{timer[1]} in steps of {timer[2]}; cancel' if timer else 'Unavailable',
                         '; '.join(NAMES[fid] for fid in sorted(features)) or 'Unavailable','; '.join(NAMES.get(fid,'Timer' if fid in (9,10) else str(fid)) for fid in sorted(missing)),'false'])
    def csv_text(rows):
        out=io.StringIO(newline='');csv.writer(out,lineterminator='\n').writerows(rows);return out.getvalue()
    return {ROOT/'tools/ac_expanded/control-vectors.csv':csv_text(vectors),ROOT/'downloads/AC-additional-controls.csv':csv_text(coverage)},dict(counts,packetCases=len(vectors),waveformCases=len(vectors)*2)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    files,counts=render()
    for path,data in files.items():
        if args.check:assert path.read_text()==data,f'{path} differs; regenerate source-table vectors'
        else:path.write_text(data)
    print(json.dumps(counts,sort_keys=True))
