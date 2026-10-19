"""Offline fixtures for the independent anonymous STB response fetcher."""
import io
import json
from pathlib import Path
import tempfile
import threading
import time
import unittest
from urllib.parse import parse_qs, urlsplit

import fetch_stb_profile as fetch


class FetchStbProfileTest(unittest.TestCase):
    def test_fixed_urls_and_exact_data_fields(self):
        cases = [
            ("brands", {"country": "ID"}, {"country": "ID"}),
            ("brands", {"country": "MY"}, {"country": "MY"}),
            ("matches", {"country": "MY", "brand_id": "123"}, {"country": "MY", "brandid": 123}),
            ("keyset", {"match_id": "kk_2_123_456"}, {"id": "kk_2_123_456"}),
        ]
        for command, kwargs, expected in cases:
            with self.subTest(command=command, kwargs=kwargs):
                parsed = urlsplit(fetch.make_url(command, **kwargs))
                self.assertEqual((parsed.scheme, parsed.netloc), ("https", "sg-urc.io.mi.com"))
                self.assertEqual(parsed.path, fetch.PATHS[command])
                query = parse_qs(parsed.query)
                self.assertEqual(set(query), {"data"})
                self.assertEqual(json.loads(query["data"][0]), expected)

    def test_rejects_parameter_injection_or_other_endpoint(self):
        cases = [
            ("https://example.com", {}),
            ("brands", {"country": "MY&opaque=x"}),
            ("brands", {"country": "MY", "brand_id": "1"}),
            ("matches", {"country": "ID", "brand_id": "1"}),
            ("matches", {"country": "MY", "brand_id": "1&x=2"}),
            ("matches", {"country": "MY", "brand_id": "１２"}),
            ("matches", {"country": "MY", "brand_id": "012"}),
            ("keyset", {"match_id": "../../private"}),
            ("keyset", {"match_id": "x?opaque=secret"}),
            ("keyset", {"match_id": "x" * 129}),
        ]
        for command, kwargs in cases:
            with self.subTest(command=command, kwargs=kwargs):
                with self.assertRaises(ValueError):
                    fetch.make_url(command, **kwargs)

    def test_body_bound_keeps_only_prefix_and_flags_overflow(self):
        for body, expected_truncation in ((b"", False), (b"1234", False), (b"12345", True)):
            with self.subTest(body=body):
                result, truncated = fetch.read_bounded(io.BytesIO(body), limit=4)
                self.assertEqual(result, body[:4])
                self.assertEqual(truncated, expected_truncation)

    def test_payload_validates_source_response_shapes(self):
        cases = [
            ("brands", {"code": 0, "result": {"data": [{"id": 123, "name": "Example"}]}}),
            ("matches", {"code": 0, "result": {"matchs": [{"id": "example-keyset"}]}}),
            ("keyset", {"code": 0, "result": {"data": {"frequency": 38000, "rc": {"power": "opaque fixture"}}}}),
            ("keyset", {"code": 0, "result": {"data": {"frequency": 36000, "key": {"power": "native encrypted fixture"}}}}),
        ]
        for command, value in cases:
            self.assertTrue(fetch.validate_payload(command, json.dumps(value).encode()))

    def test_refuses_error_pages_ambiguous_json_and_incomplete_keysets(self):
        invalid = [
            b"<html>blocked</html>",
            b'{"code":false,"result":{"data":[]}}',
            b'{"code":0,"code":7,"result":{"data":[]}}',
            b'{"code":4,"result":{"data":[]}}',
            b'{"code":0,"result":{"data":{"frequency":true,"rc":{"power":1}}}}',
            b'{"code":0,"result":{"data":{"frequency":38000,"rc":{}}}}',
            b'{"code":0,"result":{"data":{"frequency":NaN,"rc":{"power":1}}}}',
            b'{"code":0,"result":{"data":{"frequency":38000,"key":{"power":1}}}}',
            b'{"code":0,"result":{"data":{"frequency":38000,"key":{},"rc":{"power":1}}}}',
        ]
        for body in invalid:
            with self.subTest(body=body):
                with self.assertRaises(ValueError):
                    fetch.validate_payload("keyset", body)

    def test_actual_pinned_astro_native_responses(self):
        source = Path(__file__).resolve().parents[1] / "mi_stb" / "source"
        files = list(source.glob("keyset_kk_2_3833_*.json"))
        self.assertEqual(len(files), 5)
        count = 0
        for path in files:
            payload = fetch.validate_payload("keyset", path.read_bytes())
            self.assertEqual(payload["_id"], path.stem.removeprefix("keyset_"))
            self.assertIn("key", payload)
            self.assertNotIn("rc", payload)
            count += len(payload["key"])
        self.assertEqual(count, 216)

    def test_records_hash_and_keeps_physical_compatibility_unverified(self):
        body = b'{"code":0,"result":{"data":[]}}'
        def fixture(url):
            return {"body": body, "httpStatus": 200, "responseUrl": url,
                    "contentType": "application/json", "responseTruncated": False}
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "new"
            metadata = fetch.run("brands", output, country="MY", fetcher=fixture)
            self.assertTrue(metadata["payloadValidated"])
            self.assertFalse(metadata["physicalCompatibilityVerified"])
            self.assertFalse(metadata["irTimingsDecoded"])
            self.assertEqual((output / "response.bin").read_bytes(), body)
            self.assertEqual(metadata["responseSha256"], fetch.hashlib.sha256(body).hexdigest())
            self.assertEqual(json.loads((output / "payload.json").read_text()), [])
            with self.assertRaises(FileExistsError):
                fetch.run("brands", output, country="MY", fetcher=fixture)

    def test_http_failures_and_truncation_never_produce_accepted_payload(self):
        body = b'{"code":0,"result":{"data":[]}}'
        for status, truncated in ((403, False), (200, True)):
            def fixture(url):
                return {"body": body, "httpStatus": status, "responseUrl": url,
                        "contentType": "application/json", "responseTruncated": truncated}
            with tempfile.TemporaryDirectory() as tmp:
                output = Path(tmp) / "new"
                result = fetch.run("brands", output, country="MY", fetcher=fixture)
                self.assertFalse(result["payloadValidated"])
                self.assertTrue((output / "response.bin").exists())
                self.assertFalse((output / "payload.json").exists())

    def test_wall_clock_deadline_and_truthful_unauthenticated_request(self):
        release = threading.Event()
        class SlowOpener:
            def open(self, request, timeout):
                release.wait(1)
                raise OSError("fixture")
        started = time.monotonic()
        try:
            with self.assertRaises(fetch.FetchError):
                fetch.fetch_response(fetch.make_url("brands", country="MY"), timeout=0.01, opener=SlowOpener())
            self.assertLess(time.monotonic() - started, 0.5)
        finally:
            release.set()

        class Response(io.BytesIO):
            headers = {"Content-Type": "application/json"}
            def getcode(self):
                return 200
            def geturl(self):
                return fetch.make_url("brands", country="MY")
        class FixtureOpener:
            def open(self, request, timeout):
                self.request = request
                return Response(b"{}")
        opener = FixtureOpener()
        fetch.fetch_response(fetch.make_url("brands", country="MY"), opener=opener)
        self.assertEqual(opener.request.get_method(), "GET")
        self.assertEqual(opener.request.get_header("User-agent"), fetch.USER_AGENT)
        self.assertEqual(set(key.lower() for key in opener.request.headers), {"user-agent", "accept", "accept-encoding"})


if __name__ == "__main__":
    unittest.main()
