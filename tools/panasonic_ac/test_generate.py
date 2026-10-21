"""Regression checks for pinned, non-executable Panasonic parameter conversion."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

# Use a distinct module name when multiple tool suites run in the same process.
ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('panasonic_parameter_generator',ROOT/'generate.py')
generate=importlib.util.module_from_spec(spec);spec.loader.exec_module(generate)


class PanasonicParametersTest(unittest.TestCase):
    def test_checked_in_asset_matches_the_pinned_parameter_snapshot(self):
        raw=generate.write_or_check(check=True)
        index=json.loads(raw)
        self.assertEqual([p['model'] for p in index['profiles']],list(generate.MODELS))
        self.assertEqual(index['sourceParameterSha256'],generate.SOURCE_SHA256)
        self.assertFalse(index['scriptsExecuted'])

    def test_sources_keep_uploaded_apk_and_github_provenance_separate(self):
        rows=json.loads(generate.build())['profiles']
        for i,row in enumerate(rows):
            self.assertEqual(row['brand'],'Panasonic')
            self.assertFalse(row['hardwareTested'])
            self.assertEqual(row['source']['kind'],'mi724-apk-local-database' if i<3 else 'mi-github-v2.1')
            self.assertEqual(len(row['source']['sourceSha256']),64)
            self.assertNotIn('Ultra',row['model'])
            if i<3:
                self.assertEqual(row['source']['rowId'],row['model'])
                self.assertEqual(row['source']['parameterTable'],'kk_remote_ircode_src')

    def test_temperature_modes_and_control_caps_preserve_exact_numeric_tables(self):
        rows=json.loads(generate.build())['profiles']
        for i,row in enumerate(rows):
            self.assertNotIn('auto',row['modes'])
            self.assertEqual(row['temperaturePatches']['16'],[[14,32]])
            self.assertEqual(row['temperaturePatches']['30'],[[14,60]])
            self.assertEqual(row['supportsFan'],i<3)
            self.assertEqual(row['modePatches']['cool'],[[13,57 if i==3 else 48 if i==5 else 49]])
            self.assertEqual(row['powerToggle'],i==5)
        self.assertEqual(rows[2]['swingPatches'][0],[[132,136,15],[140,144,8]])
        self.assertEqual(rows[2]['swingPatches'][1],[[132,136,1],[140,144,13]])
        self.assertEqual(rows[1]['carrierHz'],37900)

    def test_reproduction_detects_an_altered_output_without_replacing_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'index.json'
            generate.write_or_check(path)
            generate.write_or_check(path,check=True)
            path.write_bytes(path.read_bytes()+b' ')
            with self.assertRaisesRegex(ValueError,'assets differ'):
                generate.write_or_check(path,check=True)
            self.assertTrue(path.read_bytes().endswith(b' '))

    def test_changed_parameter_snapshot_is_rejected_before_conversion(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'parameters.json'
            raw=(ROOT/'source/parameters.json').read_bytes()
            path.write_bytes(raw+b' ')
            with self.assertRaisesRegex(ValueError,'source hash differs'):
                generate.build(path)

    def test_malformed_entry_widths_and_nonpositive_timings_are_rejected(self):
        for raw,width in [('020E',2),('030E0200',2),('0100',2)]:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):generate.entries(raw,width)
        for value in ('0,500','1000001,500','-1,10'):
            with self.assertRaises(ValueError):generate.durations(value)
        self.assertEqual(generate.entries('020E02',2),[[[14,2]]])


if __name__=='__main__':unittest.main()
