# Static APK inspection

These original helpers are covered by the repository's [MIT license](../../LICENSE).
They do not contain Xiaomi implementation code, graphics, databases or credentials.
They inspect APKs as data and never install an APK or execute its native libraries.
Python 3.11 or later is required; inventory/tests use only the standard library.

The target APK has now been acquired and inspected statically: manifest package
`com.duokan.phone.remotecontroller`, version `7.2.4M`, version code `6500`,
34,320,753 bytes, SHA-256
`4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73`.
Android v1/v2/v3 signature verification passed. The signer certificate identifies
`CN=KeyCenter, O=Xiaomi`, but publisher identity has not been independently
corroborated against a trusted publisher certificate. No attributable Astro Ultra Box / Ulti Box profile has been recovered from its bundled data. Its raw resource contains a nested RAR/SQLite local IR database; see [local database analysis](LOCAL_IR_RESEARCH.md). Online profiles may differ. The
[investigation record](../../docs/MI_REMOTE_724M_INVESTIGATION.md) separates
verified findings from pending analysis. Inventory and tools do not establish
appliance compatibility.

## Inventory and bounded extraction

```sh
python3 tools/apk_inspection/inspect_apk.py /path/to/input.apk /path/to/new-report --extract
```

The output directory must be new or empty. The helper produces `inventory.json`
with APK/member SHA-256 hashes, ZIP sizes/compression/CRC, DEX/native/JSON/database
categories, SQLite header detection, candidate Astro/NJOI/Ultra Box/Ulti Box
strings and URL strings. Optional extraction writes regular files with no execute
permissions under `new-report/extracted`. Without `--extract`, native inputs are
written temporarily for static binutils inspection and then removed.

Bounds are 50,000 ZIP entries, 128 MiB per uncompressed member, 1 GiB total read,
compression ratio 1,000 and 8 MiB of retained output per external command. Unsafe
paths, symlinks, duplicate/ambiguous names, encrypted ZIP members, excessive
sizes/ratios and invalid CRCs are skipped and reported. Oversized entries are
checked before decompression. Native libraries receive `readelf` and `strings`
reports; APKs receive manifest/badging and signer-verification reports. No external
commands use a shell or interpolate APK-provided names into shell commands.

SDK lookup accepts `--build-tools /path/to/android-sdk/build-tools/VERSION`.
Otherwise it uses `ANDROID_HOME` or `ANDROID_SDK_ROOT` with build-tools **35.0.0**,
then searches `PATH` when neither SDK variable is present. Put a compatible JDK
on `PATH` for `apksigner`. Host `readelf` and `strings` are optional. Missing
tools are recorded as unavailable; use `--no-external-tools` for ZIP-only analysis
and fixtures without the Android SDK. Missing/failed signature tools mean no
signature verification was established.

A name/URL hit does not establish a usable IR waveform, authenticity or appliance
compatibility. Follow each candidate into its actual record/decoder. Signer
verification establishes signature integrity and certificate identity; compare
that certificate against a trusted Xiaomi source before attributing an APK to
Xiaomi. Obfuscated/encrypted data and server-side databases may remain unavailable.

## JADX installation and decompilation

The pinned trusted tool is upstream [JADX 1.5.6](https://github.com/skylot/jadx/releases/tag/v1.5.6).
Its official 72,646,741-byte ZIP has SHA-256
`545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`,
verified against the digest in GitHub's
[expanded release assets](https://github.com/skylot/jadx/releases/expanded_assets/v1.5.6).
The installer preserves the inherited network proxy/CA settings and TLS verification,
checks the pinned SHA-256 before extraction, then runs only the trusted tool's
`--version` command. Use Java 11 or later, preferably JDK 21.

```sh
bash tools/apk_inspection/install_jadx.sh /path/to/toolchains/apk-analysis
# An already cached archive can be supplied; it is still checked before use:
bash tools/apk_inspection/install_jadx.sh /path/to/toolchains/apk-analysis /path/to/jadx-1.5.6.zip
/path/to/toolchains/apk-analysis/jadx-1.5.6/bin/jadx --version
```

For the managed cloud workspace, these exact commands were tested successfully
with JDK 21.0.8+9 and reported `1.5.6`:

```sh
source /workspace/toolchains/env.sh
bash tools/apk_inspection/install_jadx.sh /workspace/toolchains/apk-analysis /workspace/toolchains/apk-analysis/jadx-1.5.6.zip
```

After acquiring and verifying the exact target APK, keep decompiled third-party
code/resources and logs outside this repository:

```sh
mkdir -p /workspace/research
/workspace/toolchains/apk-analysis/jadx-1.5.6/bin/jadx --threads-count 2 --show-bad-code \
  -d /workspace/research/mi-decompiled /path/to/MiRemote.apk > /workspace/research/jadx.log 2>&1
```

Inspect the log for failed methods and record limitations. Recoverable bytecode
is evidence for independently implemented interoperability; do not copy Xiaomi
implementation, graphics or embedded credentials into Palm Remote.

## Regional STB response research

[Anonymous STB profile fetching](STB_PROFILE_FETCH.md) documents an independently
written, development-only helper for the three regional public STB endpoints.
It records bounded raw responses and hashes, accepts no account/signing
credentials, and does not modify the APK. Live API access succeeded on
19 October 2026: Astro brand 3833 returned five keysets containing 216 decoded
commands. Their native `key` representation differs from the statically observed
regional `rc` caller; the helper records the accepted representation explicitly.
See [`../mi_stb/`](../mi_stb/README.md) for pinned public responses, provenance and
offline generation of the bundled Astro supplement. Physical compatibility
remains unverified. Offline fixtures cover both supported response shapes.

## Tests

```sh
python3 -m unittest discover -s tools/apk_inspection -p 'test_*.py' -v
```

Fixtures cover ZIP traversal/absolute/ambiguous paths, symlinks, duplicate names,
CRC corruption, entry/member/total/ratio limits, archive and golden member hashes,
native/DEX/SQLite detection, odd-offset UTF-16 candidate strings, non-executable
extraction and bounded parser logs. They require no vendor APK or network access.
