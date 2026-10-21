"""Behavioral checks for raw APK data conversion and reproducible outputs."""
import base64
import gzip
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from Crypto.Cipher import AES

import generate_apk as generate


def encoded(value):
    raw = gzip.compress(generate.canonical(value), mtime=0)
    padded = raw + b" " * (-len(raw) % 16)
    return base64.b64encode(AES.new(generate.local.FORMAT_KEY, AES.MODE_ECB).encrypt(padded)).decode()


def fixture():
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript("""
      CREATE TABLE brand (ID TEXT, CONTENT TEXT);
      CREATE TABLE kk_remote_ircode_src (ID TEXT, IR_ZIP_KEY TEXT);
      CREATE TABLE mi_remote_ircode (ID TEXT, BRAND_ID TEXT, DEVICE_ID TEXT, FREQUENCY TEXT, SP_ID TEXT, IR_ZIP_KEY TEXT, INDEXED TEXT, VERSION INT);
      CREATE TABLE kk_remote_ircode (ID TEXT, FREQUENCY TEXT, SP_ID TEXT, DEVICE_ID TEXT, BRAND_ID TEXT, IR_ZIP_KEY TEXT, INDEXED INT, EXT_FLAG INT, VERSION INT);
      CREATE TABLE xm_remote_ircode (ID TEXT, FREQUENCY TEXT, SP_ID TEXT, DEVICE_ID TEXT, BRAND_ID TEXT, IR_ZIP_KEY TEXT, INDEXED INT, EXT_FLAG INT, VERSION INT);
    """)
    brand = {"id": 137, "name": "source-local-name", "info": [{"country": "EN", "name": "Panasonic"}]}
    db.execute("INSERT INTO brand VALUES (?,?)", ("137", encoded(brand)))
    raw = {"power": [9000, 4500, 560], "power_r": [9000, 4500, 561], "unknown source": [700, 800, 900]}
    db.execute("INSERT INTO kk_remote_ircode_src VALUES (?,?)", ("77", encoded(raw)))
    db.execute("INSERT INTO mi_remote_ircode VALUES (?,?,?,?,?,?,?,?)", ("xm_1_77", "137", "1", "37910", "", "77", "1", 1000))
    db.execute("INSERT INTO kk_remote_ircode VALUES (?,?,?,?,?,?,?,?,?)", ("137_77", "38000", "", "3", "137", "77", 1, 0, 1000))
    db.execute("INSERT INTO kk_remote_ircode VALUES (?,?,?,?,?,?,?,?,?)", ("137_template", "38000", "", "3", "137", "77", 1, 1, 1000))
    encrypted = {key: encoded(pattern) for key, pattern in raw.items()}
    db.execute("INSERT INTO xm_remote_ircode VALUES (?,?,?,?,?,?,?,?,?)", ("xm_b_1_77", "38000", "", "1", "137", encoded(encrypted), 0, 0, 1000))
    return db, raw


class GenerateApkTest(unittest.TestCase):
    def test_symbol_only_source_keys_keep_nonempty_exact_labels(self):
        data = {key: [9000, 4500, 560] for key in ("_", "___", "-")}
        commands, excluded = generate.convert_commands(data, 38000, encrypted=False)
        self.assertEqual(excluded, {})
        self.assertEqual(set(commands), set(data))
        for key, command in commands.items():
            self.assertEqual(command["sourceKey"], key)
            self.assertEqual(command["label"], key)
            self.assertEqual(command["pattern"], data[key])

    def test_named_brand_and_exact_source_associations_are_preserved(self):
        db, raw = fixture()
        try:
            outputs, report = generate.build_database(db)
        finally:
            db.close()
        index = json.loads(outputs["index.json"])
        self.assertEqual(report["coverage"]["profiles"], 3)
        self.assertEqual(report["coverage"]["commands"], 9)
        self.assertEqual({p["brand"] for p in index["profiles"]}, {"Panasonic"})
        self.assertEqual({p["category"] for p in index["profiles"]}, {"tv", "air_conditioner"})
        for metadata in index["profiles"]:
            signals = json.loads(outputs[metadata["asset"].removeprefix("ir/mi_apk/")])["commands"]
            self.assertEqual(set(signals), set(raw))
            self.assertFalse(metadata["hardwareTested"])
            self.assertEqual(metadata["model"], metadata["source"]["modelId"])
            for key, command in signals.items():
                self.assertEqual(command["sourceKey"], key)
                self.assertEqual(command["pattern"], raw[key])
                self.assertEqual(command["carrierHz"], 37910 if metadata["model"] == "xm_1_77" else 38000)
        self.assertEqual(report["coverage"]["skippedProfiles"], {"stateful_ac_template_requires_encoder": 1})

    def test_numeric_brand_identity_cannot_be_silently_relabelled(self):
        db, _ = fixture()
        db.execute("UPDATE brand SET CONTENT=?", (encoded({"id": 999, "name": "Other brand"}),))
        with self.assertRaisesRegex(ValueError, "brand identity"):
            generate.build_database(db)
        db.close()

    def test_missing_brand_is_explicitly_excluded_instead_of_guessed(self):
        db, _ = fixture()
        db.execute("UPDATE mi_remote_ircode SET BRAND_ID='999'")
        outputs, report = generate.build_database(db)
        self.assertEqual(report["coverage"]["profiles"], 2)
        self.assertEqual(report["coverage"]["skippedProfiles"]["no_bundled_brand_name"], 1)
        self.assertNotIn('"brandId":"999"', outputs["index.json"].decode())
        db.close()

    def test_unsupported_signals_are_counted_without_changing_valid_arrays(self):
        good = [9000, 4500, 560]
        data = {"valid": good, "negative": [1, -1, 3], "fraction": [1, 2.5, 3],
                "boolean": [True, 2, 3], "long": [1000000, 1000000, 1], "script": "return vendorProgram()"}
        accepted, excluded = generate.convert_commands(data, 38000, encrypted=False)
        self.assertEqual(set(accepted), {"valid"})
        self.assertEqual(accepted["valid"]["pattern"], good)
        self.assertEqual(sum(excluded.values()), 5)
        accepted, excluded = generate.convert_commands({"valid": good}, 19000, encrypted=False)
        self.assertEqual(accepted, {})
        self.assertEqual(excluded, {"unsupported_carrier": 1})

    def test_missing_indexed_row_and_unsafe_ids_fail_closed(self):
        for field, value in [("IR_ZIP_KEY", "missing"), ("ID", "../escape")]:
            db, _ = fixture()
            db.execute("UPDATE mi_remote_ircode SET " + field + "=?", (value,))
            with self.assertRaises(ValueError):
                generate.build_database(db)
            db.close()

    def test_reproducible_check_detects_changed_and_extra_outputs(self):
        db, _ = fixture()
        outputs, report = generate.build_database(db)
        db.close()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets, metadata = root / "assets", root / "metadata"
            generate.write_or_check(outputs, report, assets, metadata=metadata)
            generate.write_or_check(outputs, report, assets, check=True, metadata=metadata)
            target = assets / next(name for name in outputs if name.startswith("profiles/"))
            original = target.read_bytes()
            target.write_bytes(original + b" ")
            with self.assertRaisesRegex(ValueError, "differs"):
                generate.write_or_check(outputs, report, assets, check=True, metadata=metadata)
            target.write_bytes(original)
            (assets / "invented.json").write_text("{}")
            with self.assertRaisesRegex(ValueError, "expected set"):
                generate.write_or_check(outputs, report, assets, check=True, metadata=metadata)

    def test_packaged_catalogue_matches_pinned_full_source_coverage_and_hashes(self):
        index = json.loads((generate.DEFAULT_OUTPUT / "index.json").read_bytes())
        report = json.loads((generate.ROOT / "import-summary.json").read_bytes())
        expected = json.loads((generate.ROOT / "generated-manifest.json").read_bytes())
        self.assertEqual(index["coverage"], report["coverage"])
        self.assertEqual(index["coverage"]["profiles"], 604)
        self.assertEqual(index["coverage"]["commands"], 24204)
        self.assertEqual(index["coverage"]["categories"]["tv"], {"profiles": 571, "commands": 23885, "brands": 19})
        self.assertEqual(index["coverage"]["categories"]["air_conditioner"], {"profiles": 33, "commands": 319, "brands": 19})
        self.assertEqual(index["coverage"]["skippedProfiles"], {"stateful_ac_template_requires_encoder": 701})
        self.assertEqual(len(report["omittedProfiles"]), 701)
        outputs = {}
        for name, digest in expected["outputs"].items():
            raw = (generate.DEFAULT_OUTPUT / name).read_bytes()
            self.assertEqual(len(raw), digest["bytes"])
            self.assertEqual(generate.local.sha256(raw), digest["sha256"])
            outputs[name] = raw
        self.assertEqual(len(outputs), 605)
        self.assertEqual(generate.manifest(outputs), (generate.ROOT / "generated-manifest.json").read_bytes())
        # A real bundled 37,910-Hz source stays in microseconds, including its gap.
        model = next(p for p in index["profiles"] if p["model"] == "xm_1_10")
        signals = json.loads(outputs[model["asset"].removeprefix("ir/mi_apk/")])["commands"]
        self.assertEqual(signals["power"]["carrierHz"], 37910)
        self.assertEqual(signals["power"]["pattern"][:6], [826, 845, 1662, 845, 826, 845])
        self.assertIn(85331, signals["power"]["pattern"])


if __name__ == "__main__":
    unittest.main()
