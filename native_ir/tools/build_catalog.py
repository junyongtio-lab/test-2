#!/usr/bin/env python3
"""Rebuild the original TV catalogue from cited numeric command facts.

Usage: python3 native_ir/tools/build_catalog.py
Requires javac/java. Normal builds are offline. --verify-upstream checks pinned
source hashes and the factual extraction over HTTPS without persisting original
capture text. Legacy source files have no asserted applicable licence grant.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/ir'
FACTS = ROOT / 'native_ir/data/base_tv_facts.json'
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

def parse_text(text):
    signals, current = [], None
    for line in text.splitlines():
        if line.startswith('name:'):
            current = {}; signals.append(current)
        if current is not None and ':' in line and not line.startswith('#'):
            name, value = line.split(':', 1); current[name] = value.strip()
    return signals

def parse(file):
    return parse_text(file.read_text())

def little_hex(value):
    return int.from_bytes(bytes.fromhex(value), 'little')

def verify_upstream(profiles):
    for profile in profiles:
        source = profile['source']
        url = source['url'].replace('https://github.com/', 'https://raw.githubusercontent.com/', 1)
        url = url.replace('/blob/', '/', 1)
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != source['sha256']:
            raise ValueError('Upstream hash mismatch: ' + source['path'])
        extracted, seen = [], set()
        for item in parse_text(data.decode()):
            name = key(item['name'])
            if name in seen:
                suffix = 2
                while f'{name}_{suffix}' in seen: suffix += 1
                name = f'{name}_{suffix}'
            seen.add(name)
            signal = {'key': name, 'label': item['name'], 'carrierHz': 38000}
            if item['type'] == 'parsed':
                signal.update(protocol=item['protocol'], address=little_hex(item['address']),
                              command=little_hex(item['command']))
            else:
                signal.update(protocol='raw', carrierHz=int(item['frequency']),
                              pattern=[int(value) for value in item['data'].split()])
            extracted.append(signal)
        if extracted != profile['signals']:
            raise ValueError('Numeric extraction differs: ' + source['path'])

def main(verify=False):
    facts = json.loads(FACTS.read_text())['profiles']
    if verify:
        verify_upstream(facts)
    profiles, queue, pending = [], [], []
    for fact in facts:
        commands = {}
        for item in fact['signals']:
            name = item['key']
            if name in commands:
                raise ValueError('Duplicate command key: ' + name)
            command = {'label': item['label'], 'carrierHz': item['carrierHz']}
            if item['protocol'] != 'raw':
                protocol, address, code = item['protocol'], item['address'], item['command']
                command.update(protocol=protocol, addressHex=hex(address), commandHex=hex(code))
                queue.append(f'{protocol}\t{address:x}\t{code:x}')
                pending.append(command)
            else:
                command.update(protocol='raw', pattern=item['pattern'])
            commands[name] = command
        profile = {key: fact[key] for key in ('id', 'brand', 'model', 'source')}
        profile.update(category='tv', transport='ir', hardwareTested=False, commands=commands)
        profiles.append(profile)
    with tempfile.TemporaryDirectory(prefix='ir-catalog-') as directory:
        subprocess.run(['javac', '-d', directory,
            str(ROOT / 'app/src/main/java/com/universalremote/app/ir/IrEncoder.java'),
            str(ROOT / 'native_ir/tools/EncodeForCatalog.java')], check=True)
        result = subprocess.run(['java', '-cp', directory, 'EncodeForCatalog'],
            input='\n'.join(queue)+'\n', text=True, capture_output=True, check=True)
    encoded = result.stdout.splitlines()
    if len(encoded) != len(pending):
        raise ValueError('Encoder command count mismatch.')
    for command, line in zip(pending, encoded): command['pattern'] = json.loads(line)
    for profile in profiles:
        for command in profile['commands'].values():
            if (not 20000 <= command['carrierHz'] <= 60000
                    or not 2 <= len(command['pattern']) <= 2000
                    or not all(type(value) is int and 0 < value <= 1000000 for value in command['pattern'])
                    or sum(command['pattern']) > 1900000):
                raise ValueError('Unsafe waveform in ' + profile['id'])
    output = {'schemaVersion': 1, 'durationUnit': 'microseconds', 'patternOrder': 'mark,space,mark,...',
              'notice': 'Community sourced handset profiles. Physical compatibility has not been tested. An Android IR emitter is required. Not a promise of support for every model.',
              'profiles': profiles}
    (ASSETS / 'profiles.json').write_text(json.dumps(output, indent=2) + '\n')
    print(f'Wrote {len(profiles)} sourced profiles, {sum(len(p["commands"]) for p in profiles)} commands.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-upstream', action='store_true',
                        help='Verify pinned HTTPS source hashes and numeric extraction in memory.')
    main(parser.parse_args().verify_upstream)
