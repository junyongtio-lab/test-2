"""Check sourced control frames and the additional TV asset's integrity."""
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'app/src/main/assets/ir/tv_extra/profiles.json'
FACTS = ROOT / 'native_ir/tools/tv_extra_facts.json'
spec = importlib.util.spec_from_file_location('tv_extra', ROOT / 'native_ir/tools/build_tv_extra.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


def decode_distance(pattern, header, mark, zero, one):
    """Decode a generated waveform independently to its wire bytes, LSB first."""
    if pattern[:2] != header or len(pattern) != 67 or pattern[-1] != mark:
        raise ValueError('Invalid 32-bit frame shape')
    bytes_ = []
    for byte in range(4):
        value = 0
        for bit in range(8):
            index = 2 + (byte * 8 + bit) * 2
            if pattern[index] != mark or pattern[index + 1] not in (zero, one):
                raise ValueError('Invalid pulse-distance bit')
            if pattern[index + 1] == one:
                value |= 1 << bit
        bytes_.append(value)
    return bytes_


class ExtraTvTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profiles = {p['id']: p for p in json.loads(ASSET.read_text())['profiles']}
        cls.facts = {p['id']: p for p in json.loads(FACTS.read_text())['profiles']}

    def test_prism_power_uses_actual_prism_address(self):
        command = self.profiles['prismplus_community']['commands']['power']
        self.assertEqual([0x0e, 0x0e, 0x0c, 0xf3],
                         decode_distance(command['pattern'], [4500, 4500], 550, 550, 1650))

    def test_prism_volume_buttons_remain_distinct(self):
        commands = self.profiles['prismplus_community']['commands']
        self.assertEqual('0x14', commands['volume_up']['commandHex'])
        self.assertEqual('0x15', commands['volume_down']['commandHex'])
        self.assertNotEqual(commands['volume_up']['pattern'], commands['volume_down']['pattern'])

    def test_daewoo_power_nec_address_and_complements(self):
        command = self.profiles['daewoo_community']['commands']['power']
        self.assertEqual([0x80, 0x7f, 0x82, 0x7d],
                         decode_distance(command['pattern'], [9000, 4500], 560, 560, 1690))

    def test_daewoo_raw_capture_is_preserved_without_reencoding(self):
        profile = self.profiles['daewoo_r43a08']
        power = profile['commands']['power']
        self.assertEqual([8106, 3864, 609, 390, 609, 391], power['pattern'][:6])
        self.assertEqual(37, len(power['pattern']))
        self.assertEqual(38000, power['carrierHz'])
        for profile in self.profiles.values():
            signals = {s['label']: s for s in self.facts[profile['id']]['signals']}
            for command in profile['commands'].values():
                if command['protocol'] == 'raw':
                    self.assertEqual(signals[command['label']]['pattern'], command['pattern'])

    def test_unknown_buttons_are_not_guessed(self):
        for id_ in ['daewoo_r40a15', 'daewoo_r43a08']:
            self.assertNotIn('unknown', self.profiles[id_]['commands'])
            self.assertEqual(1, len(self.profiles[id_]['omittedCommands']))
        self.assertNotIn('power', self.profiles['daewoo_r40a15']['commands'])

    def test_manifest_and_source_provenance(self):
        self.assertEqual(4, len(self.profiles))
        self.assertEqual(56, sum(len(p['commands']) for p in self.profiles.values()))
        self.assertEqual({'PRISM+', 'Daewoo'}, {p['brand'] for p in self.profiles.values()})
        for profile in self.profiles.values():
            self.assertEqual('tv', profile['category'])
            self.assertFalse(profile['hardwareTested'])
            self.assertEqual('NOASSERTION', profile['source']['license'])
            self.assertIn('Commits prior to 2319685 are not covered.', profile['source']['sourceLicenseNote'])
            self.assertRegex(profile['source']['sha256'], r'^[a-f0-9]{64}$')
            self.assertIn(profile['source']['commit'], profile['source']['url'])

    def test_waveforms_fit_android_transmit_limits(self):
        for profile in self.profiles.values():
            for command in profile['commands'].values():
                self.assertTrue(20000 <= command['carrierHz'] <= 60000)
                self.assertTrue(2 <= len(command['pattern']) <= 2000)
                self.assertTrue(all(type(v) is int and 0 < v <= 1000000 for v in command['pattern']))
                self.assertLessEqual(sum(command['pattern']), 1900000)

    def test_offline_rebuild_is_reproducible(self):
        before = hashlib.sha256(ASSET.read_bytes()).hexdigest()
        builder.build()
        self.assertEqual(before, hashlib.sha256(ASSET.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
