#!/usr/bin/env python3
"""Extract factual AC infrared parameters from a pinned external source checkout.

    git clone https://github.com/Lucaslhm/Flipper-IRDB.git /tmp/flipper-irdb
    git -C /tmp/flipper-irdb checkout d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d
    python3 native_ir/tools/build_ac_captures.py --source-dir /tmp/flipper-irdb

The source files remain outside the app/repository. Only functional names and
numeric signal parameters are extracted. The upstream README excludes commits
before its CC0 cutoff; no blanket CC0 claim is made for these legacy captures.
Requires the JDK used by this Android project; no Python packages are required.
"""

import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import quote


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'app/src/main/assets/ir/ac_captures'
COMMIT = 'd126fb1b6f1e114c52b4a8c19839ea65e3a9c24d'
BASE = 'https://github.com/Lucaslhm/Flipper-IRDB'
# Display-name spelling/case corrections only; no inferred protocol/model aliases.
BRAND_NAMES = {'GREE': 'Gree', 'Insigna': 'Insignia', 'MayTag': 'Maytag'}
LICENSE_NOTE = (
    'Upstream README says contributions before commit '
    '2319685f2cbf0cd3f809609622cade14d24fb819 are not covered by its CC0 license. '
    'No individual license is asserted here. This app contains extracted factual '
    'numeric infrared parameters and functional device/button identifiers, '
    'not original capture-file text.'
)


def signals(file):
    """Read named signal fields, deliberately ignoring all source commentary."""
    found, current = [], None
    for line in file.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('name:'):
            current = {}
            found.append(current)
        if current is not None and ':' in line and not line.startswith('#'):
            key, value = line.split(':', 1)
            current[key] = value.strip()
    return found


def slug(value):
    value = value.replace('+', '_plus_').replace('-', '_minus_')
    return re.sub('[^a-z0-9]+', '_', value.lower()).strip('_') or 'signal'


def little_hex(value):
    return int.from_bytes(bytes.fromhex(value), 'little')


def rejection(command):
    pattern = command['pattern']
    if not 20000 <= command['carrierHz'] <= 60000:
        return 'Carrier frequency outside the app-supported 20–60 kHz range.'
    if not 3 <= len(pattern) <= 2000:
        return 'Pattern length outside the app-supported 3–2000 timings.'
    if any(type(value) is not int or not 0 < value <= 1000000 for value in pattern):
        return 'Timing outside the app-supported positive 1,000,000 µs limit.'
    if sum(pattern) > 1900000:
        return 'Complete capture exceeds Android/app maximum of 1.9 seconds; not truncated.'
    return None


def generate(source):
    actual = subprocess.check_output(
        ['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != COMMIT:
        raise ValueError(f'Expected upstream commit {COMMIT}, found {actual}.')
    # Check the actual capture tree too: a matching HEAD alone is insufficient.
    changed = subprocess.check_output(
        ['git', '-C', str(source), 'status', '--porcelain', '--', 'ACs'], text=True)
    if changed.strip():
        raise ValueError('Upstream ACs tree has local changes; use an unmodified checkout.')

    profiles, pending, queue = [], [], []
    for file in sorted((source / 'ACs').rglob('*.ir')):
        relative = file.relative_to(source).as_posix()
        source_brand = file.relative_to(source / 'ACs').parts[0]
        brand = BRAND_NAMES.get(source_brand, source_brand.replace('_', ' '))
        commands = {}
        for item in signals(file):
            name = slug(item['name'])
            if name in commands:
                suffix = 2
                while f'{name}_{suffix}' in commands:
                    suffix += 1
                name = f'{name}_{suffix}'
            command = {'label': item['name'], 'carrierHz': 38000}
            if item['type'] == 'raw':
                command.update(protocol='raw', carrierHz=int(item['frequency']),
                               pattern=[int(value) for value in item['data'].split()])
            elif item['type'] == 'parsed' and item['protocol'] in ('NEC', 'NECext'):
                address, code = little_hex(item['address']), little_hex(item['command'])
                command.update(protocol=item['protocol'], addressHex=hex(address),
                               commandHex=hex(code))
                queue.append(f'{item["protocol"]}\t{address:x}\t{code:x}')
                pending.append(command)
            else:
                raise ValueError(f'Unrecognized signal type/protocol at {relative}: {item["name"]}')
            commands[name] = command
        profiles.append({
            'id': 'ac_capture_' + slug(str(file.relative_to(source / 'ACs').with_suffix(''))),
            'brand': brand,
            'model': file.stem.replace('_', ' '),
            'category': 'air_conditioner',
            'transport': 'ir',
            'fixedState': True,
            'hardwareTested': False,
            'notice': ('Recorded handset signals. Labels describe the original capture; '
                       'a signal may set several AC settings at once. '
                       'Replaying Temp+/Mode does not calculate a new temperature/mode. '
                       'Physical compatibility has not been tested.'),
            'source': {
                'url': f'{BASE}/blob/{COMMIT}/{quote(relative, safe="/")}',
                'commit': COMMIT,
                'path': relative,
                'sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                'license': 'NOASSERTION',
                'licenseNote': LICENSE_NOTE,
            },
            'commands': commands,
        })

    with tempfile.TemporaryDirectory(prefix='ac-catalog-') as directory:
        subprocess.run([
            'javac', '-d', directory,
            str(ROOT / 'app/src/main/java/com/universalremote/app/ir/IrEncoder.java'),
            str(ROOT / 'native_ir/tools/EncodeForCatalog.java'),
        ], check=True)
        encoded = subprocess.run(
            ['java', '-cp', directory, 'EncodeForCatalog'],
            input='\n'.join(queue) + '\n', capture_output=True, text=True, check=True,
        ).stdout.splitlines()
    if len(encoded) != len(pending):
        raise ValueError('Encoder did not return every parsed signal.')
    for command, line in zip(pending, encoded):
        command['pattern'] = json.loads(line)

    excluded = []
    for profile in profiles:
        for key, command in list(profile['commands'].items()):
            reason = rejection(command)
            if reason:
                excluded.append({
                    'sourcePath': profile['source']['path'],
                    'label': command['label'],
                    'durationUs': sum(command['pattern']),
                    'timingCount': len(command['pattern']),
                    'reason': reason,
                })
                del profile['commands'][key]
    profiles = [profile for profile in profiles if profile['commands']]
    assert len({profile['id'] for profile in profiles}) == len(profiles)
    output = {
        'schemaVersion': 1,
        'durationUnit': 'microseconds',
        'patternOrder': 'mark,space,mark,...',
        'notice': ('Factual community AC-category signal parameters. '
                   'Recorded states, not dynamic AC state encoders. '
                   'An Android infrared transmitter is required; model compatibility is untested. '
                   'The upstream category also contains some air coolers/ventilation devices. '
                   'This is not a claim to support every brand or model.'),
        'profiles': profiles,
    }
    OUTPUT.mkdir(parents=True, exist_ok=True)
    # Compact JSON keeps the bundled catalog small without changing numeric timings.
    (OUTPUT / 'profiles.json').write_text(
        json.dumps(output, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    summary = {
        'sourceRepository': BASE,
        'sourceCommit': COMMIT,
        'sourceCaptureCount': len(list((source / 'ACs').rglob('*.ir'))),
        'includedProfileCount': len(profiles),
        'includedCommandCount': sum(len(p['commands']) for p in profiles),
        'includedBrandCount': len({p['brand'] for p in profiles}),
        'brands': sorted({p['brand'] for p in profiles}, key=str.casefold),
        'excludedCommands': excluded,
        'licenseNote': LICENSE_NOTE,
    }
    (OUTPUT / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    with (OUTPUT / 'coverage.csv').open('w', newline='', encoding='utf-8') as coverage:
        writer = csv.writer(coverage)
        writer.writerow(['brand', 'model', 'profile_id', 'command_count',
                         'hardware_tested', 'capture_type', 'source_url', 'source_sha256',
                         'source_license_assertion'])
        for profile in profiles:
            writer.writerow([profile['brand'], profile['model'], profile['id'],
                             len(profile['commands']), 'false', 'recorded handset states',
                             profile['source']['url'], profile['source']['sha256'],
                             profile['source']['license']])
    print(json.dumps({key: value for key, value in summary.items()
                      if key.endswith('Count') or key == 'excludedCommands'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, required=True,
                        help='External unmodified Flipper-IRDB checkout at the pinned commit.')
    generate(parser.parse_args().source_dir.resolve())
