#!/usr/bin/env python3
"""Rebuild bundled IR waveforms from the pinned, unmodified community .ir files.

Usage: python3 native_ir/tools/build_catalog.py
Requires javac/java. Does not download files or modify original captures.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/ir'
COMMIT = 'd126fb1b6f1e114c52b4a8c19839ea65e3a9c24d'
BASE = 'https://github.com/Lucaslhm/Flipper-IRDB'
PROFILES = [
    ('samsung_bn59', 'Samsung', 'BN59-01315B', 'TVs/Samsung/Samsung_BN59-01315B.ir'),
    ('lg_akb75375604', 'LG', 'AKB75375604', 'TVs/LG/LG_TV_AKB75375604.ir'),
    ('panasonic_viera', 'Panasonic', 'Viera', 'TVs/Panasonic/Panasonic_Viera.ir'),
    ('panasonic_th43hs550k', 'Panasonic', 'TH-43HS550K / HS550K / GX650K', 'TVs/Panasonic/Panasonic_TH-43HS550K.ir'),
    ('hisense_en3y39h', 'Hisense', 'EN3Y39H', 'TVs/Hisense/Hisense_EN3Y39H.ir'),
    ('hisense_en3ah39h', 'Hisense', 'EN3AH39H', 'TVs/Hisense/Hisense_EN3AH39H.ir'),
    ('hisense_u7g', 'Hisense', 'U7G', 'TVs/Hisense/Hisense_U7G.ir'),
    ('hisense_55u6k', 'Hisense', '55U6K (2023)', 'TVs/Hisense/Hisense_55U6K.ir'),
]
ALIASES = {
    'on_off': 'power', 'powa': 'power', 'source': 'input', 'tv_video': 'input',
    'arw_up':'up','arw_down':'down','arw_left':'left','arw_right':'right',
    'nav_up':'up','nav_dn':'down','nav_lft':'left','nav_rgt':'right','nav_ok':'ok',
    'return':'back','nav_back':'back','nav_exit':'exit','nav_menu':'menu','nav_setting':'settings',
    'vol_up':'volume_up','vol_dn':'volume_down','vol_down':'volume_down',
    'ch_up':'channel_up','ch_down':'channel_down','ch_next':'channel_up','ch_prev':'channel_down',
    'p_up':'channel_up','p_down':'channel_down','chanel_up':'channel_up','chanel_down':'channel_down',
    'r':'red','g':'green','b':'blue','y':'yellow','red_a':'red','green_b':'green','yellow_c':'yellow','blue_d':'blue',
    'fast_fw':'fast_forward','forward':'fast_forward','fast_fo':'fast_forward','ff':'fast_forward',
    'backward':'rewind','fast_ba':'rewind','rwd':'rewind','play_pa':'play_pause',
    'display_info':'info','ch_list':'channel_list','prime':'amazon','primevideo':'amazon',
}

def key(name):
    name = re.sub('[^a-z0-9]+', '_', name.lower()).strip('_')
    if name.isdigit() and 0 <= int(name) <= 9:
        return 'digit_' + name
    return ALIASES.get(name, name)

def parse(file):
    signals, current = [], None
    for line in file.read_text().splitlines():
        if line.startswith('name:'):
            current = {}; signals.append(current)
        if current is not None and ':' in line and not line.startswith('#'):
            name, value = line.split(':', 1); current[name] = value.strip()
    return signals

def little_hex(value):
    return int.from_bytes(bytes.fromhex(value), 'little')

def main():
    profiles, queue, pending = [], [], []
    for id_, brand, model, path in PROFILES:
        file = ASSETS / 'originals' / Path(path).name
        commands = {}
        for item in parse(file):
            name = key(item['name'])
            # Preserve source duplicates without silently replacing an earlier command.
            if name in commands:
                suffix = 2
                while f'{name}_{suffix}' in commands: suffix += 1
                name = f'{name}_{suffix}'
            command = {'label': item['name'], 'carrierHz': 38000}
            if item['type'] == 'parsed':
                protocol, address, code = item['protocol'], little_hex(item['address']), little_hex(item['command'])
                command.update(protocol=protocol, addressHex=hex(address), commandHex=hex(code))
                queue.append(f'{protocol}\t{address:x}\t{code:x}')
                pending.append(command)
            else:
                command.update(protocol='raw', carrierHz=int(item['frequency']), pattern=[int(v) for v in item['data'].split()])
            commands[name] = command
        source = {'url': f'{BASE}/blob/{COMMIT}/{path}', 'commit': COMMIT, 'path': path,
                  'license': 'CC0-1.0', 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
        profiles.append({'id': id_, 'brand': brand, 'model': model, 'transport': 'ir',
                         'hardwareTested': False, 'source': source, 'commands': commands})
    with tempfile.TemporaryDirectory(prefix='ir-catalog-') as directory:
        subprocess.run(['javac', '-d', directory,
            str(ROOT / 'app/src/main/java/com/universalremote/app/ir/IrEncoder.java'),
            str(ROOT / 'native_ir/tools/EncodeForCatalog.java')], check=True)
        result = subprocess.run(['java', '-cp', directory, 'EncodeForCatalog'],
            input='\n'.join(queue)+'\n', text=True, capture_output=True, check=True)
    encoded = result.stdout.splitlines()
    assert len(encoded) == len(pending)
    for command, line in zip(pending, encoded): command['pattern'] = json.loads(line)
    for profile in profiles:
        for command in profile['commands'].values():
            assert 2 <= len(command['pattern']) <= 2000
            assert all(0 < value <= 1000000 for value in command['pattern'])
            assert sum(command['pattern']) <= 2000000
    output = {'schemaVersion': 1, 'durationUnit': 'microseconds', 'patternOrder': 'mark,space,mark,...',
              'notice': 'Community sourced handset profiles. Physical compatibility has not been tested. An Android IR emitter is required. Not a promise of support for every model.',
              'profiles': profiles}
    (ASSETS / 'profiles.json').write_text(json.dumps(output, indent=2) + '\n')
    print(f'Wrote {len(profiles)} sourced profiles, {sum(len(p["commands"]) for p in profiles)} commands.')

if __name__ == '__main__': main()
