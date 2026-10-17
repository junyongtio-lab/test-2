# SPDX-License-Identifier: AGPL-3.0-or-later
"""Tests of waveform units, associations, provenance and shipped safety."""

import base64
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from Crypto.Cipher import AES

import generate


class DecoderTests(unittest.TestCase):
    def encrypted(self, values):
        compressed = gzip.compress(json.dumps(values).encode(), mtime=0)
        compressed += b" " * (-len(compressed) % 16)
        return base64.b64encode(AES.new(generate.PATTERN_FORMAT_KEY, AES.MODE_ECB).encrypt(compressed)).decode()

    def test_published_golden_raw_microseconds(self):
        # Public input facts from upstream tests/test_crypt_utils.py. Independent
        # OpenSSL decoding confirms the 72 timings and the complete-array hash.
        encrypted = "QJPmll3+SCgpSE73bTO9hni9upbSpKrS73cugR4FZSMT2VGtMTkEIsegm1kjFy3bCLQJsJZKAXxjDF7hGaYIolNzR+qo5f2H3C/PqsSK2Q8kaQaJAycytxhqhVgnwnOUZ6gj0xXscdkPK3MBzr6HH5yEOGDtocCXKP8qEXZdvctnCmFZaZwubXf1Cscf/rlVkAz53JacxfUkCiDqw8M27g=="
        raw = generate.decode_signal(encrypted, 37990)
        self.assertEqual(len(raw), 72)
        self.assertEqual(sum(raw), 217000)
        self.assertEqual(raw[:10], [9042,4484,579,552,580,567,579,567,544,554])
        self.assertEqual(raw[-8:], [579,1671,579,40318,9018,2250,579,96733])
        self.assertEqual(hashlib.sha256(generate.canonical(raw)).hexdigest(), "efd663631c943ee494da66c5360bcc87cc4f9053b933d677c6133e81d692f731")

    def test_equal_waveforms_keep_each_source_key_and_label(self):
        encrypted = self.encrypted([9000,4500,560,1690,560])
        commands, skipped = generate.convert_model({"frequency":38000,"key":{"power":encrypted,"right":encrypted}})
        self.assertFalse(skipped)
        self.assertEqual(set(commands), {"power","right"})
        self.assertEqual(commands["power"]["label"], "Power")
        self.assertEqual(commands["right"]["sourceKey"], "right")
        self.assertEqual(commands["power"]["pattern"], [9000,4500,560,1690,560])
        self.assertEqual(commands["right"]["carrierHz"], 38000)
        self.assertIsNot(commands["power"], commands["right"])

    def test_brand_references_keep_both_sections(self):
        self.assertEqual(generate.model_references({"tree":{"nodes":[{"keysetids":["model-b","model-a"]}]},"others":[{"_id":"model-c"},{"_id":"model-a"}]}), ["model-a","model-b","model-c"])

    def test_invalid_frequency_not_substituted(self):
        for frequency in (0, 38, 19999, 60001, 38000.0, True, None):
            with self.subTest(frequency=frequency), self.assertRaisesRegex(generate.UnsupportedSignal,"carrier_out_of_range"):
                generate.decode_signal(self.encrypted([9000,4500,560]), frequency)

    def test_invalid_pattern_not_trimmed_or_rounded(self):
        for pattern in ([9000,0,560], [9000,-4500,560], [9000,4500,560.5], [9000,4500,True], [1000000,900000,1], [9000,4500], "not a pattern"):
            with self.subTest(pattern=pattern), self.assertRaises(generate.UnsupportedSignal):
                generate.decode_signal(self.encrypted(pattern),38000)

    def test_executable_and_proprietary_ac_not_evaluated(self):
        commands, skipped = generate.convert_model({"frequency":38000,"key":{"1518":"if(functionId==1) then bytes[3]=0; end","1001":"000802DE0321040705F8","888888":[]}})
        self.assertFalse(commands)
        self.assertEqual(sum(skipped.values()),3)
        self.assertEqual(skipped["unsupported_executable_ac_definition"],1)

    def test_same_model_in_two_brands_remains_two_profiles(self):
        encrypted = self.encrypted([9000,4500,560])
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); database=base/"database"; database.mkdir()
            for device_id in generate.DEVICE_CATEGORIES:
                device=database/f"{device_id}_fixture"; (device/"models").mkdir(parents=True)
            device=database/"1_fixture"
            for brand in ("Alpha_1","Beta_2"):
                (device/(brand+".json")).write_text(json.dumps({"data":{"tree":{"nodes":[{"keysetids":["shared"]}]}}}))
            (device/"models/shared.json").write_text(json.dumps({"data":{"frequency":38000,"key":{"power":encrypted}}}))
            index=generate.generate(database,base/"output",base/"manifest.json")
            self.assertEqual({p["brand"] for p in index["profiles"]},{"Alpha","Beta"})
            self.assertEqual(len({p["id"] for p in index["profiles"]}),2)
            self.assertEqual({p["model"] for p in index["profiles"]},{"shared"})


class ShippedCatalogueTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo=Path(__file__).resolve().parents[2]
        cls.assets=cls.repo/"app/src/main/assets"
        cls.index=json.loads((cls.assets/"ir/mi/index.json").read_text())
        cls.manifest=json.loads((cls.repo/"tools/mi_catalogue/source-manifest.json").read_text())

    def test_every_shipped_asset_safe_exact_and_accounted(self):
        ids=set(); commands=0
        for profile in self.index["profiles"]:
            self.assertNotIn(profile["id"],ids); ids.add(profile["id"])
            content=(self.assets/profile["asset"]).read_bytes()
            self.assertEqual(generate.sha256(content),profile["assetSha256"])
            self.assertEqual(generate.sha256(content),self.manifest["profileAssets"][profile["asset"]])
            raw=json.loads(content)
            self.assertEqual(raw["sourceModelId"],profile["model"])
            self.assertEqual(len(raw["commands"]),profile["commandCount"])
            self.assertFalse(profile["hardwareTested"])
            source=profile["source"]
            self.assertEqual(self.manifest["sourceFiles"][source["sourceFile"]]["sha256"],source["sourceSha256"])
            self.assertEqual(self.manifest["sourceFiles"][source["brandFile"]]["sha256"],source["brandSha256"])
            for key, signal in raw["commands"].items():
                self.assertEqual(signal["sourceKey"],key)
                generate.validate_signal(signal["carrierHz"],signal["pattern"])
                commands+=1
        self.assertEqual(commands,self.index["coverage"]["commands"])
        self.assertEqual(len(ids),self.index["coverage"]["profiles"])
        self.assertEqual(len(list((self.assets/"ir/mi/profiles").glob("*.json"))),len(ids))

    def test_pinned_source_and_no_fabricated_missing_brands(self):
        self.assertEqual(self.index["source"]["archiveSha256"],generate.ARCHIVE_SHA256)
        self.assertEqual(self.index["source"]["revision"],generate.REVISION)
        brands={p["brand"].casefold() for p in self.index["profiles"]}
        for absent in ("Astro","NJOI","PRISM+","PrismPlus"):
            self.assertNotIn(absent.casefold(),brands)
        self.assertEqual(set(self.index["coverage"]["categories"]),{"tv","set_top_box","air_conditioner","fan"})
        self.assertTrue(self.index["coverage"]["skippedCommands"]["unsupported_executable_ac_definition"])

    def test_index_matches_reproducibility_manifest(self):
        self.assertEqual(generate.sha256((self.assets/"ir/mi/index.json").read_bytes()),self.manifest["outputIndexSha256"])


if __name__ == "__main__":
    unittest.main()
