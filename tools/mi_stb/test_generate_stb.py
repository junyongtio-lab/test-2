"""Behavioral checks against pinned, attributable Astro service responses."""
import base64
import gzip
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from Crypto.Cipher import AES

import generate_stb as generate


class GenerateStbTest(unittest.TestCase):
    @staticmethod
    def encrypted(value):
        compressed = gzip.compress(json.dumps(value).encode(), mtime=0)
        padded = compressed + b" " * (-len(compressed) % 16)
        return base64.b64encode(AES.new(generate.PATTERN_FORMAT_KEY, AES.MODE_ECB).encrypt(padded)).decode()

    def test_identity_chain_and_exact_source_coverage(self):
        outputs = generate.build()
        index = json.loads(outputs["index.json"])
        self.assertEqual([profile["source"]["modelId"] for profile in index["profiles"]], list(generate.MODEL_IDS))
        self.assertEqual(index["coverage"]["commands"], 216)
        self.assertEqual([profile["commandCount"] for profile in index["profiles"]], [58, 37, 78, 30, 13])
        self.assertEqual(index["source"]["origin"], generate.ORIGIN)
        for profile in index["profiles"]:
            self.assertEqual((profile["brand"], profile["category"]), ("Astro", "set_top_box"))
            self.assertEqual(profile["model"], profile["source"]["modelId"])
            self.assertFalse(profile["hardwareTested"])
            self.assertNotIn("Ultra", profile["model"])

    def test_all_raw_timings_and_carriers_match_source_exactly(self):
        outputs = generate.build()
        index = json.loads(outputs["index.json"])
        carriers = []
        for profile in index["profiles"]:
            source = json.loads((generate.REPOSITORY / profile["source"]["sourceFile"]).read_bytes())["result"]["data"]
            raw = outputs[profile["asset"].removeprefix("ir/mi_stb/")]
            self.assertEqual(generate.sha256(raw), profile["assetSha256"])
            self.assertEqual(generate.sha256((generate.REPOSITORY / profile["source"]["sourceFile"]).read_bytes()),
                             profile["source"]["sourceSha256"])
            commands = json.loads(raw)["commands"]
            self.assertEqual(set(commands), set(source["key"]))
            carriers.append(source["frequency"])
            for key, command in commands.items():
                self.assertEqual(command["sourceKey"], key)
                self.assertEqual(command["carrierHz"], source["frequency"])
                self.assertEqual(command["pattern"], generate.decode_signal(source["key"][key], source["frequency"]))
        self.assertEqual(carriers, [36000, 56000, 36000, 31360, 38000])
        # Real 56-kHz source durations stay in microseconds: no cycle rescaling.
        profile = index["profiles"][1]
        commands = json.loads(outputs[profile["asset"].removeprefix("ir/mi_stb/")])["commands"]
        self.assertEqual(commands["power"]["pattern"][:6], [250, 500, 250, 268, 250, 268])

    def test_normal_and_alternate_waveforms_are_distinct(self):
        outputs = generate.build()
        index = json.loads(outputs["index.json"])
        for profile in (index["profiles"][0], index["profiles"][2]):
            commands = json.loads(outputs[profile["asset"].removeprefix("ir/mi_stb/")])["commands"]
            self.assertIn("power_r", commands)
            self.assertNotEqual(commands["power"]["pattern"], commands["power_r"]["pattern"])
            self.assertEqual(commands["power_r"]["sourceKey"], "power_r")
            self.assertNotIn("poweroff", commands)

    def test_decompression_bound_rejects_oversized_source(self):
        with self.assertRaisesRegex(ValueError, "decompressed byte bound"):
            generate.decode_signal(self.encrypted([1] * 20000), 38000)

    def test_invalid_patterns_and_carriers_rejected(self):
        for values in ([True, 2, 3], [0, 2, 3], [1, 2], [1000000, 1000000, 1], "script"):
            with self.subTest(values=values):
                with self.assertRaises(ValueError):
                    generate.decode_signal(self.encrypted(values), 38000)
        for carrier in (True, 19999, 60001, None):
            with self.assertRaises(ValueError):
                generate.decode_signal(self.encrypted([9000, 4500, 560]), carrier)
        for value in ("not base64!", "", base64.b64encode(b"short").decode()):
            with self.assertRaises(ValueError):
                generate.decode_signal(value, 38000)

    def test_source_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "source"
            shutil.copytree(generate.ROOT / "source", source)
            path = source / "keyset_kk_2_3833_536.json"
            raw = path.read_bytes()
            path.write_bytes(raw[:-1] + bytes([raw[-1] ^ 1]))
            with self.assertRaisesRegex(ValueError, "source hash differs"):
                generate.build(source)

    def test_reproduction_check_detects_changed_and_extra_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            output, manifest = directory / "assets", directory / "manifest.json"
            generate.write_or_check(output, manifest)
            generate.write_or_check(output, manifest, check=True)
            (output / "invented.json").write_text("{}")
            with self.assertRaisesRegex(ValueError, "assets/manifests differ"):
                generate.write_or_check(output, manifest, check=True)


if __name__ == "__main__":
    unittest.main()
