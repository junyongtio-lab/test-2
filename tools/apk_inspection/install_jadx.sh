#!/usr/bin/env bash
# Install trusted static-analysis tooling; never runs a target APK.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 TOOLCHAIN_DIRECTORY [CACHED_JADX_ZIP]" >&2
  exit 2
fi
JADX_INSTALL_ROOT="$1"
JADX_VERSION=1.5.6
JADX_EXPECTED_SHA256=545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974
mkdir -p "$JADX_INSTALL_ROOT"
JADX_ARCHIVE_PATH="${2:-$JADX_INSTALL_ROOT/jadx-$JADX_VERSION.zip}"
if [[ ! -f "$JADX_ARCHIVE_PATH" ]]; then
  curl --fail --silent --show-error --location \
    "https://github.com/skylot/jadx/releases/download/v$JADX_VERSION/jadx-$JADX_VERSION.zip" \
    --output "$JADX_ARCHIVE_PATH"
fi
python3 - "$JADX_ARCHIVE_PATH" "$JADX_INSTALL_ROOT/jadx-$JADX_VERSION" "$JADX_EXPECTED_SHA256" <<'PY'
import hashlib
from pathlib import Path, PurePosixPath
import stat
import sys
import zipfile

archive, destination = Path(sys.argv[1]), Path(sys.argv[2])
with archive.open('rb') as stream:
    actual = hashlib.file_digest(stream, 'sha256').hexdigest()
if actual != sys.argv[3]:
    raise SystemExit(f'JADX checksum mismatch: {actual}')
with zipfile.ZipFile(archive) as zipped:
    entries = zipped.infolist()
    if len(entries) > 10_000 or sum(i.file_size for i in entries) > 300_000_000:
        raise SystemExit('JADX ZIP exceeds size bounds')
    names = set()
    for info in entries:
        path = PurePosixPath(info.filename)
        if (not path.parts or path.is_absolute() or '..' in path.parts or '\\' in info.filename
                or stat.S_ISLNK(info.external_attr >> 16) or info.filename in names
                or info.file_size > 150_000_000
                or not (destination / info.filename).resolve().is_relative_to(destination.resolve())):
            raise SystemExit(f'Unsafe JADX ZIP entry: {info.filename}')
        names.add(info.filename)
    zipped.extractall(destination)
for name in ('jadx', 'jadx-gui'):
    (destination / 'bin' / name).chmod(0o755)
print(f'JADX archive SHA256 verified: {actual}')
PY
"$JADX_INSTALL_ROOT/jadx-$JADX_VERSION/bin/jadx" --version
