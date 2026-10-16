#!/usr/bin/env python3
"""Build the additional TV catalogue from cited numeric command facts.

The legacy upstream captures do not have a declared applicable licence. This
repository stores numeric control facts and factual button/manufacturer labels,
not their source file text. --verify-upstream checks the pinned source hashes and
fact extraction over HTTPS, in memory. The normal build is completely offline.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
FACTS = Path(__file__).with_name('tv_extra_facts.json')
OUTPUT = ROOT / 'app/src/main/assets/ir/tv_extra/profiles.json'
spec = importlib.util.spec_from_file_location('base_catalog', Path(__file__).with_name('build_catalog.py'))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)

# '+' and '-' must remain distinct: the general normalizer strips punctuation.
ALIASES = {'Vol+': 'volume_up', 'Vol-': 'volume_down',
           'VOL+': 'volume_up', 'VOL-': 'volume_down',
           'CH+': 'channel_up', 'CH-': 'channel_down',
           'AV': 'input', 'DISPLAY': 'info', 'RECALL': 'back'}


def command_key(label):
    return ALIASES.get(label, base.key(label))


def verify_upstream(profiles):
    """Verify sources without redistributing or persisting their original text."""
    for profile in profiles:
        source = profile['source']
        url = source['url'].replace('https://github.com/', 'https://raw.githubusercontent.com/', 1)
        url = url.replace('/blob/', '/', 1)
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != source['sha256']:
            raise ValueError('Upstream hash mismatch: ' + source['path'])
        signals, current = [], None
        for line in data.decode().splitlines():
            if line.startswith('name:'):
                current = {}; signals.append(current)
            if current is not None and ':' in line and not line.startswith('#'):
                key, value = line.split(':', 1); current[key] = value.strip()
        extracted = []
        for item in signals:
            fact = {'label': item['name']}
            if item['type'] == 'parsed':
                fact.update(protocol=item['protocol'], address=base.little_hex(item['address']),
                            command=base.little_hex(item['command']))
            else:
                fact.update(protocol='raw', carrierHz=int(item['frequency']),
                            pattern=[int(v) for v in item['data'].split()])
            extracted.append(fact)
        if extracted != profile['signals']:
            raise ValueError('Numeric extraction differs: ' + source['path'])


def build(verify=False):
    facts = json.loads(FACTS.read_text())['profiles']
    if verify:
        verify_upstream(facts)
    profiles, queue, pending = [], [], []
    for fact in facts:
        commands, omitted = {}, []
        for signal in fact['signals']:
            # Never guess what an unidentified handset button does.
            if signal['label'].lower() == 'unknown':
                omitted.append('Unknown: upstream does not identify its function.')
                continue
            key = command_key(signal['label'])
            if key in commands:
                raise ValueError('Duplicate command key: ' + key)
            command = {'label': signal['label'], 'carrierHz': signal.get('carrierHz', 38000),
                       'protocol': signal['protocol']}
            if signal['protocol'] == 'raw':
                command['pattern'] = signal['pattern']
            else:
                address, code = signal['address'], signal['command']
                command.update(addressHex=hex(address), commandHex=hex(code))
                queue.append(f'{signal["protocol"]}\t{address:x}\t{code:x}')
                pending.append(command)
            commands[key] = command
        profile = {key: fact[key] for key in ('id', 'brand', 'model', 'source')}
        profile.update(category='tv', transport='ir', hardwareTested=False, commands=commands)
        if omitted:
            profile['omittedCommands'] = omitted
        profiles.append(profile)
    with tempfile.TemporaryDirectory(prefix='tv-extra-catalog-') as directory:
        subprocess.run(['javac', '-d', directory,
                        str(ROOT / 'app/src/main/java/com/universalremote/app/ir/IrEncoder.java'),
                        str(ROOT / 'native_ir/tools/EncodeForCatalog.java')], check=True)
        result = subprocess.run(['java', '-cp', directory, 'EncodeForCatalog'],
                                input='\n'.join(queue) + '\n', text=True,
                                capture_output=True, check=True)
    encoded = result.stdout.splitlines()
    if len(encoded) != len(pending):
        raise ValueError('Encoder command count mismatch.')
    for command, line in zip(pending, encoded):
        command['pattern'] = json.loads(line)
    for profile in profiles:
        for command in profile['commands'].values():
            pattern = command['pattern']
            if (not 20000 <= command['carrierHz'] <= 60000
                    or not 2 <= len(pattern) <= 2000
                    or not all(isinstance(value, int) and 0 < value <= 1000000 for value in pattern)
                    or sum(pattern) > 1900000):
                raise ValueError('Unsafe waveform in ' + profile['id'])
    output = {'schemaVersion': 1, 'durationUnit': 'microseconds',
              'patternOrder': 'mark,space,mark,...',
              'notice': 'Factual parameters from cited community captures; physical compatibility untested. An Android IR emitter is required. Model coverage varies; no support for every TV model is promised.',
              'profiles': profiles}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(output, indent=2) + '\n')
    print(f'Wrote {len(profiles)} additional TV profiles, '
          f'{sum(len(p["commands"]) for p in profiles)} commands.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-upstream', action='store_true',
                        help='Verify pinned HTTPS source hashes and numeric facts in memory.')
    build(parser.parse_args().verify_upstream)
