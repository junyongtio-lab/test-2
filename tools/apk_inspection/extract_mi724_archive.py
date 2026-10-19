#!/usr/bin/env python3
"""Verify and unpack only the exact user-supplied Mi Remote 7.2.4M archive.

The APK is kept in a bounded memory factory until its hash is verified, then
written to the explicit new output filename. No archive paths are materialized
and no APK/native code is executed. Original code covered by the root MIT license.
"""
from __future__ import annotations

import argparse
import hashlib
from importlib.metadata import version
import os
from pathlib import Path

ARCHIVE_BYTES = 24_137_558
ARCHIVE_SHA256 = "72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6"
APK_NAME = "com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22(arm64-v8a)(nodpi)_apkmirror.com.apk"
APK_BYTES = 34_320_753
APK_SHA256 = "4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73"
MEMORY_LIMIT = 40 * 1024 * 1024


def extract_verified_archive(source: Path, output: Path) -> Path:
    import py7zr
    from py7zr.io import BytesIOFactory

    if version("py7zr") != "1.0.0":
        raise ValueError("Use the pinned py7zr==1.0.0 in requirements-archive.txt")
    # Keep the caller's literal final path: resolving a dangling output symlink
    # would accidentally turn it into permission to create its target.
    destination = Path(os.path.abspath(output))
    if os.path.lexists(destination):
        raise FileExistsError("Output must be a new filename, including no existing symlink")
    with source.open("rb") as stream:
        if os.fstat(stream.fileno()).st_size != ARCHIVE_BYTES:
            raise ValueError("Input size differs from the exact user-supplied archive")
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if digest != ARCHIVE_SHA256:
            raise ValueError(f"Archive SHA-256 mismatch: {digest}")
        stream.seek(0)
        factory = BytesIOFactory(limit=MEMORY_LIMIT)
        with py7zr.SevenZipFile(stream, mode="r") as archive:
            members = archive.list()
            if (len(members) != 1 or members[0].filename != APK_NAME
                    or members[0].is_directory or members[0].uncompressed != APK_BYTES):
                raise ValueError("Archive must contain exactly the expected APK basename and size")
            # extract(factory=...) writes to in-memory objects, never to paths
            # controlled by member names. The archive hash and declared output
            # size above are pinned before decompression begins.
            archive.extract(targets=[APK_NAME], factory=factory)
        if set(factory.products) != {APK_NAME}:
            raise ValueError("Unexpected extracted member names")
        apk = factory.get(APK_NAME)
        if apk.size() != APK_BYTES or apk.size() > MEMORY_LIMIT:
            raise ValueError("Extracted APK size mismatch")
        apk.seek(0)
        apk_digest = hashlib.sha256()
        while chunk := apk.read(1024 * 1024):
            apk_digest.update(chunk)
        if apk_digest.hexdigest() != APK_SHA256:
            raise ValueError(f"APK SHA-256 mismatch: {apk_digest.hexdigest()}")
        apk.seek(0)
        # Exclusive creation protects existing files and symlinks. Restrictive
        # permissions ensure extracted APK bytes never gain execute permission.
        descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(descriptor, "wb") as target:
                while chunk := apk.read(1024 * 1024):
                    target.write(chunk)
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("new_apk", type=Path, help="Explicit new output filename; parent directory must already exist")
    options = parser.parse_args()
    try:
        destination = extract_verified_archive(options.archive, options.new_apk)
    except (ValueError, OSError, ImportError) as exc:
        parser.error(str(exc))
    print(f"Verified APK written: {destination}")
    print(f"SHA-256: {APK_SHA256}")
    print(f"Bytes: {APK_BYTES}")


if __name__ == "__main__":
    main()
