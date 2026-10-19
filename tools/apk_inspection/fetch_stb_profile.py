#!/usr/bin/env python3
"""Fetch one documented anonymous regional STB catalogue response for research.

Original MIT-licensed tool. No vendor implementation, credentials, signing,
account access, executable model scripts, or legacy API client is included.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import queue
import re
import threading
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


ORIGIN = "https://sg-urc.io.mi.com"
PATHS = {
    "brands": "/app/public/get_stb_brand",
    "matches": "/app/public/get_stb_match",
    "keyset": "/app/public/get_stb_keyset",
}
USER_AGENT = "PalmRemoteResearch/1.0 (independent interoperability research)"
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
TIMEOUT_SECONDS = 30
ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}", re.ASCII)
BRAND_PATTERN = re.compile(r"(?:0|[1-9][0-9]{0,9})", re.ASCII)


class FetchError(Exception):
    """A bounded request could not be completed."""


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Fixed endpoints must not silently become a different host or path.
        return None


def make_url(command: str, country=None, brand_id=None, match_id=None) -> str:
    if command not in PATHS:
        raise ValueError("Unknown endpoint")
    if command == "brands":
        if country not in ("ID", "MY") or brand_id is not None or match_id is not None:
            raise ValueError("Brands requires country ID or MY only")
        data = {"country": country}
    elif command == "matches":
        if country != "MY" or match_id is not None:
            raise ValueError("Matches requires country MY")
        if not isinstance(brand_id, str) or not BRAND_PATTERN.fullmatch(brand_id):
            raise ValueError("Brand ID must be a returned nonnegative decimal ID of at most 10 digits")
        data = {"country": country, "brandid": int(brand_id)}
    else:
        if country is not None or brand_id is not None:
            raise ValueError("Keyset accepts a match ID only")
        if not isinstance(match_id, str) or not ID_PATTERN.fullmatch(match_id):
            raise ValueError("Match ID must be a returned identifier of at most 128 safe ASCII characters")
        data = {"id": match_id}
    return ORIGIN + PATHS[command] + "?" + urlencode({
        "data": json.dumps(data, separators=(",", ":"), ensure_ascii=True)
    })


def read_bounded(response, limit=MAX_RESPONSE_BYTES):
    """Retain at most the limit, using one extra byte to detect truncation."""
    body = bytearray()
    while len(body) <= limit:
        chunk = response.read(min(65536, limit + 1 - len(body)))
        if not chunk:
            return bytes(body), False
        body.extend(chunk)
    return bytes(body[:limit]), True


def fetch_response(url: str, timeout=TIMEOUT_SECONDS, opener=None):
    """Enforce a wall-clock deadline, even if a server slowly drips its body.

    The single daemon worker does not write files. On deadline the CLI can exit
    without waiting for a socket operation; urllib also receives a socket timeout.
    Its normal proxy environment handling and default TLS trust remain enabled.
    """
    results = queue.Queue(maxsize=1)
    opener = opener or build_opener(NoRedirect())

    def work():
        response = None
        try:
            request = Request(url, headers={
                "User-Agent": USER_AGENT,
                "Accept": "application/json",
                "Accept-Encoding": "identity",
            }, method="GET")
            try:
                response = opener.open(request, timeout=timeout)
            except HTTPError as exc:
                # Preserve a bounded error body for review, without following it.
                response = exc
            body, truncated = read_bounded(response)
            results.put(({
                "httpStatus": response.getcode(),
                "responseUrl": response.geturl(),
                "contentType": response.headers.get("Content-Type", ""),
                "responseTruncated": truncated,
                "body": body,
            }, None))
        except (OSError, URLError, ValueError):
            # Do not echo potentially sensitive inherited proxy configuration.
            results.put((None, "Network request failed; check permitted host access, TLS, proxy, and connectivity"))
        finally:
            if response is not None:
                response.close()

    threading.Thread(target=work, daemon=True, name="stb-catalogue-fetch").start()
    try:
        result, error = results.get(timeout=timeout)
    except queue.Empty as exc:
        raise FetchError("Request exceeded the wall-clock timeout") from exc
    if error:
        raise FetchError(error)
    return result


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON object key")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError("Nonstandard JSON numeric constant")


def validate_payload(command: str, body: bytes):
    try:
        document = json.loads(body.decode("utf-8-sig"),
                              object_pairs_hook=unique_object,
                              parse_constant=reject_constant)
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise ValueError("Response is not a valid unambiguous UTF-8 JSON document") from exc
    if not isinstance(document, dict) or type(document.get("code")) is not int:
        raise ValueError("Response lacks the expected integer code")
    if document["code"] != 0:
        raise ValueError("Service returned a nonzero result code")
    result = document.get("result")
    if not isinstance(result, dict):
        raise ValueError("Response lacks the expected result object")
    if command == "brands":
        payload = result.get("data")
        if not isinstance(payload, list) or not all(isinstance(row, dict) for row in payload):
            raise ValueError("Brand response lacks the expected result.data object array")
    elif command == "matches":
        rows = result.get("matchs")
        if not isinstance(rows, list) or not all(isinstance(row, dict) for row in rows):
            raise ValueError("Match response lacks the expected result.matchs object array")
        payload = result
    elif command == "keyset":
        payload = result.get("data")
        if not isinstance(payload, dict) or not payload:
            raise ValueError("Keyset response lacks a nonempty result.data object")
        frequency = payload.get("frequency")
        if type(frequency) is not int or frequency <= 0:
            raise ValueError("Keyset response lacks a positive integer frequency")
        # Static regional callers mention rc; actual anonymous Astro responses
        # retrieved 2026-10-19 use native encrypted raw timings in key instead.
        # Accept one explicit representation without conflating their encodings.
        representations = [name for name in ("key", "rc") if name in payload]
        if len(representations) != 1:
            raise ValueError("Keyset response needs exactly one key or rc command representation")
        commands = payload[representations[0]]
        if not isinstance(commands, dict) or not commands:
            raise ValueError("Keyset response lacks a nonempty command object")
        if representations[0] == "key" and not all(
                isinstance(key, str) and bool(key) and isinstance(value, str) and bool(value)
                for key, value in commands.items()):
            raise ValueError("Native key representation requires nonempty string commands")
    else:
        raise ValueError("Unknown endpoint")
    return payload


def save_json(path: Path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def run(command, output: Path, country=None, brand_id=None, match_id=None, fetcher=fetch_response):
    url = make_url(command, country=country, brand_id=brand_id, match_id=match_id)
    # A new directory avoids replacing previous evidence or unrelated files.
    output.mkdir(parents=True, exist_ok=False)
    metadata = {
        "schemaVersion": 1,
        "purpose": "Independent, anonymous regional STB catalogue research",
        "endpoint": command,
        "requestUrl": url,
        "requestMethod": "GET",
        "userAgent": USER_AGENT,
        "retrievedAtUtc": datetime.now(timezone.utc).isoformat(),
        "maximumResponseBytes": MAX_RESPONSE_BYTES,
        "timeoutSeconds": TIMEOUT_SECONDS,
        "payloadValidated": False,
        "irTimingsDecoded": False,
        "physicalCompatibilityVerified": False,
    }
    try:
        response = fetcher(url)
        body = response.pop("body")
        metadata.update(response)
        metadata["responseBytes"] = len(body)
        metadata["responseSha256"] = hashlib.sha256(body).hexdigest()
        (output / "response.bin").write_bytes(body)
        if metadata["responseTruncated"]:
            metadata["validationError"] = "Response exceeded the byte limit; stored body is a truncated prefix"
        elif metadata["httpStatus"] != 200:
            metadata["validationError"] = "HTTP response was not 200; no payload accepted"
        elif metadata["responseUrl"] != url:
            metadata["validationError"] = "Response URL differed from the fixed requested endpoint"
        else:
            try:
                payload = validate_payload(command, body)
                save_json(output / "payload.json", payload)
                metadata["payloadValidated"] = True
                if command == "keyset":
                    metadata["commandRepresentation"] = "key" if "key" in payload else "rc"
            except ValueError as exc:
                metadata["validationError"] = str(exc)
    except FetchError as exc:
        metadata["requestError"] = str(exc)
    save_json(output / "metadata.json", metadata)
    return metadata


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    brands = subparsers.add_parser("brands", help="Fetch STB brands; APK uses ID, MY is an explicit comparison query")
    brands.add_argument("--country", choices=("ID", "MY"), required=True)
    matches = subparsers.add_parser("matches", help="Fetch Malaysian keyset matches using a brand ID from a response")
    matches.add_argument("--country", choices=("MY",), default="MY")
    matches.add_argument("--brand-id", required=True)
    keyset = subparsers.add_parser("keyset", help="Fetch one selected match ID from a match response")
    keyset.add_argument("--id", dest="match_id", required=True)
    for subparser in (brands, matches, keyset):
        subparser.add_argument("--output", type=Path, required=True, help="New output directory, preferably outside this repository")
    args = parser.parse_args(argv)
    try:
        metadata = run(args.command, args.output, country=getattr(args, "country", None),
                       brand_id=getattr(args, "brand_id", None), match_id=getattr(args, "match_id", None))
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(json.dumps({"output": str(args.output), "payloadValidated": metadata["payloadValidated"],
                      "physicalCompatibilityVerified": False,
                      "error": metadata.get("requestError") or metadata.get("validationError")}, indent=2))
    return 0 if metadata["payloadValidated"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
