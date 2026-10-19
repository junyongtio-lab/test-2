"""Meaningful hostile ZIP fixtures; no target application or SDK is required."""
import hashlib
import importlib.util
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

SPEC = importlib.util.spec_from_file_location("inspect_apk", Path(__file__).with_name("inspect_apk.py"))
inspection = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inspection)


class ApkInspectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.apk = self.root / "fixture.apk"

    def tearDown(self):
        self.temporary.cleanup()

    def write_zip(self, members, compression=zipfile.ZIP_STORED):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "w", compression=compression) as archive:
                for name, value in members:
                    archive.writestr(name, value)

    def inspect(self, **kwargs):
        return inspection.inspect_apk(self.apk, self.root / "report", external_tools=False, **kwargs)

    def test_rejects_path_traversal_absolute_and_ambiguous_names(self):
        bad = ["../escape", "/absolute", "a/../../escape", "a\\escape", "C:/escape",
               "./alias", "a//alias", "a/./alias", ".", "bad\nname"]
        self.write_zip([(name, b"bad") for name in bad] + [("assets/good.json", b"{}")])
        report = self.inspect(extract=True)
        self.assertEqual(len(bad), sum("skipped" in e for e in report["entries"]))
        files = list((self.root / "report/extracted").rglob("*"))
        self.assertEqual(["assets/good.json"], [str(p.relative_to(self.root / "report/extracted")) for p in files if p.is_file()])
        self.assertFalse((self.root / "escape").exists())

    def test_symlink_is_never_followed_or_materialized(self):
        symlink = zipfile.ZipInfo("assets/link")
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.write_zip([(symlink, b"../../outside"), ("assets/link/child", b"safe regular data")])
        report = self.inspect(extract=True)
        self.assertEqual("symlink", report["entries"][0]["skipped"])
        self.assertFalse((self.root / "report/extracted/assets/link").is_symlink())
        self.assertEqual(b"safe regular data", (self.root / "report/extracted/assets/link/child").read_bytes())

    def test_duplicate_member_cannot_replace_first_bytes(self):
        self.write_zip([("assets/profile.json", b"first"), ("assets/profile.json", b"replacement")])
        report = self.inspect(extract=True)
        self.assertIn("duplicate", report["entries"][1]["skipped"])
        self.assertEqual(b"first", (self.root / "report/extracted/assets/profile.json").read_bytes())

    def test_oversized_member_and_total_budget_are_checked_before_read(self):
        self.write_zip([("too-large", b"x" * 11), ("first", b"123456"), ("second", b"123456")])
        with patch.object(inspection, "MAX_MEMBER", 10), patch.object(inspection, "MAX_TOTAL", 10):
            report = self.inspect(extract=True)
        self.assertIn("size", report["entries"][0]["skipped"])
        self.assertEqual(6, report["totalReadBytes"])
        self.assertIn("size", report["entries"][2]["skipped"])

    def test_high_compression_ratio_is_rejected(self):
        self.write_zip([("compressed-bomb", b"0" * 100_000)], zipfile.ZIP_DEFLATED)
        with patch.object(inspection, "MAX_RATIO", 10):
            report = self.inspect(extract=True)
        self.assertEqual("compression ratio exceeds bound", report["entries"][0]["skipped"])
        self.assertEqual(0, report["totalReadBytes"])

    def test_entry_count_is_bounded(self):
        self.write_zip([("first", b"a"), ("second", b"b")])
        with patch.object(inspection, "MAX_ENTRIES", 1), self.assertRaisesRegex(ValueError, "entry count"):
            self.inspect()

    def test_crc_corruption_is_reported_and_not_extracted(self):
        payload = b"UNIQUE_PAYLOAD_TO_CORRUPT"
        self.write_zip([("assets/profile", payload)])
        data = self.apk.read_bytes()
        self.apk.write_bytes(data.replace(payload, b"X" + payload[1:], 1))
        report = self.inspect(extract=True)
        self.assertIn("BadZipFile", report["entries"][0]["skipped"])
        self.assertFalse((self.root / "report/extracted/assets/profile").exists())

    def test_hashes_and_categories_do_not_require_native_execution(self):
        self.write_zip([("assets/hash.json", b"abc"), ("classes2.dex", b"dex\n035\0"),
                        ("lib/arm64-v8a/libtest.so", b"\x7fELFnot-executable"),
                        ("assets/catalogue.sqlite3", b"SQLite format 3\0" + b"\0" * 32),
                        ("assets/unnamed.bin", b"SQLite format 3\0" + b"\0" * 32)])
        archive_digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        report = self.inspect(extract=True)
        entries = {e["name"]: e for e in report["entries"]}
        self.assertEqual(archive_digest, report["sha256"])
        self.assertEqual("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", entries["assets/hash.json"]["sha256"])
        self.assertEqual("native", entries["lib/arm64-v8a/libtest.so"]["kind"])
        self.assertEqual("database", entries["assets/catalogue.sqlite3"]["kind"])
        self.assertTrue(entries["assets/unnamed.bin"]["sqliteHeader"])
        self.assertEqual("dex", entries["classes2.dex"]["kind"])
        self.assertEqual([], report["nativeReports"])
        self.assertEqual([], report["commands"])
        self.assertEqual(0, (self.root / "report/extracted/lib/arm64-v8a/libtest.so").stat().st_mode & 0o111)

    def test_names_are_candidates_and_utf16_offsets_are_preserved(self):
        content = b"Astro Ultra Box\0https://example.invalid/ir\0" + "Ulti Box".encode("utf-16le")
        self.write_zip([("assets/catalogue.json", content)])
        report = self.inspect()
        hits = report["interestingStrings"][0]["hits"]
        self.assertTrue(any(h["encodingCandidate"] == "ascii" and "Astro" in h["text"] for h in hits))
        self.assertTrue(any(h["encodingCandidate"] == "utf16le" and "Ulti Box" in h["text"] for h in hits))
        self.assertTrue(any(h["encodingCandidate"] == "utf16le" and h["offset"] == content.index("Ulti Box".encode("utf-16le")) for h in hits))
        self.assertEqual(["https://example.invalid/ir"], report["urls"][0]["values"])

    def test_nonempty_output_is_rejected(self):
        self.write_zip([("asset", b"data")])
        out = self.root / "report"
        out.mkdir()
        (out / "existing").write_bytes(b"do not overwrite")
        with self.assertRaisesRegex(ValueError, "empty"):
            self.inspect(extract=True)
        self.assertEqual(b"do not overwrite", (out / "existing").read_bytes())

    def test_external_parser_log_is_bounded_without_losing_exit_status(self):
        with patch.object(inspection, "MAX_COMMAND_OUTPUT", 50):
            report = inspection.command_report([sys.executable, "-c", "print('a'*100_000)"], self.root / "log.txt")
        self.assertEqual(0, report["returncode"])
        self.assertTrue(report["truncated"])
        self.assertEqual(50, (self.root / "log.txt").stat().st_size)

    def test_external_parser_timeout_is_reported(self):
        report = inspection.command_report([sys.executable, "-c", "import time; time.sleep(10)"], self.root / "timeout.txt", timeout=0.05)
        self.assertIsNone(report["returncode"])
        self.assertEqual("timeout", report["error"])


if __name__ == "__main__":
    unittest.main()
