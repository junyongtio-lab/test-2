"""Small hostile-input checks for the original optional static IR helper."""
import base64
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

try:
    from Crypto.Cipher import AES
except ModuleNotFoundError:
    AES = None

if AES is not None:
    import inspect_local_ir as research


def encrypted_fixture(value, *, suffix=b"", raw=False):
    content = value if raw else json.dumps(value).encode()
    compressed = gzip.compress(content, mtime=0) + suffix
    compressed += b" " * (-len(compressed) % AES.block_size)
    return base64.b64encode(AES.new(research.FORMAT_KEY, AES.MODE_ECB).encrypt(compressed)).decode()


@unittest.skipIf(AES is None, "Optional local-IR tests require requirements-local-ir.txt")
class LocalIrInspectionTest(unittest.TestCase):
    def test_json_fixture_is_data_only(self):
        source = {"capture": [9000, 4500, 560], "script": "do not execute me"}
        self.assertEqual(source, research.decode_json(encrypted_fixture(source)))

    def test_decompression_bomb_is_rejected(self):
        encoded = encrypted_fixture({"value": "x" * 100_000})
        with self.assertRaisesRegex(ValueError, "decompression bound"):
            research.decode_json(encoded, limit=100)

    def test_concatenated_gzip_or_unknown_padding_is_rejected(self):
        encoded = encrypted_fixture({}, suffix=gzip.compress(b"{}", mtime=0))
        with self.assertRaisesRegex(ValueError, "after the gzip"):
            research.decode_json(encoded)

    def test_non_object_json_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "object or array"):
            research.decode_json(encrypted_fixture(123))

    def test_malformed_base64_or_partial_cipher_block_is_rejected(self):
        for value in ["%%%", base64.b64encode(b"short").decode()]:
            with self.assertRaises(ValueError):
                research.decode_json(value)

    def test_pinned_checks_fail_closed(self):
        digest = hashlib.sha256(b"known").hexdigest()
        research.verify(b"known", 5, digest, "fixture")
        with self.assertRaisesRegex(ValueError, "does not match"):
            research.verify(b"other", 5, digest, "fixture")

    def test_input_read_bound_is_enforced(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "input"
            path.write_bytes(b"1234")
            self.assertEqual(b"1234", research.bounded_read(path, 4))
            with self.assertRaisesRegex(ValueError, "pinned input size"):
                research.bounded_read(path, 3)

    def test_wrong_database_is_rejected_before_sqlite_parsing(self):
        with self.assertRaisesRegex(ValueError, "SQLite database length/SHA-256"):
            research.inspect_database(b"not a database")


if __name__ == "__main__":
    unittest.main()
