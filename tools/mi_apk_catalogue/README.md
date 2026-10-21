# Uploaded Mi Remote TV/AC catalogue

This independently written, offline converter reads the **exact user-uploaded
Mi Remote 7.2.4M / code 6500 APK** as data. It imports the named TV and raw
air-conditioner records in that APK's bundled local database. The uploaded
archive is 7.2.4M; it is not a verified 7.4.2M release.

No APK, DEX, native library, SQL script, Lua program or AC template is executed.
The Android app receives normalized JSON signals, rather than the vendor APK,
SQLite database or executable encoder implementation.

## Verified input

The default command uses the archive already committed under `downloads/`:

`com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22(arm64-v8a)(nodpi)_apkmirror.com.7z`

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| User-uploaded 7z | 24,137,558 | `72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6` |
| Extracted APK | 34,320,753 | `4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73` |
| Nested SQLite database | 5,878,784 | `727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa` |

The bounded [archive extractor](../apk_inspection/extract_mi724_archive.py)
checks the 7z hash, single expected member, output size and APK hash before
writing one temporary file. The existing
[local-database inspector](../apk_inspection/LOCAL_IR_RESEARCH.md) checks the
APK resource, RAR and database hashes, schemas and record counts. SQLite queries
run against an in-memory, read-only database with trusted schemas disabled.

## Accepted data and limits

| Source category | Brand labels | Profiles | Source command associations |
| --- | ---: | ---: | ---: |
| TV | 19 | 571 | 23,885 |
| Air conditioner, raw captures | 19 | 33 | 319 |
| Combined | 32 distinct | 604 | 24,204 |

All 604 accepted profiles have complete validated raw commands, with zero raw
command exclusions for this pinned input. The importer also identifies **701
stateful AC templates**, which need encoders and are excluded from this raw
catalogue. Their source IDs and exclusion reason are retained in
[`import-summary.json`](import-summary.json). Separate supported Panasonic state
encoding is documented in [tools/panasonic_ac](../panasonic_ac/).

Palm Remote 1.5.0 additionally preserves **all 734 AC source records**, including
those 701 definitions, through the separate [complete AC source-data exporter](../mi_ac_database/README.md).
This raw importer's exclusion counts describe raw transmission conversion, not
the complete source-data copy. Complete data inclusion does not implement the
remaining 698 stateful control profiles.

This is the recoverable raw TV/AC data bundled in the supplied APK. It is not Mi
Remote's complete online catalogue or a port of its full AC engine. Other device
categories are outside this converter. The bundled database has no named Daewoo
TV waveform set, so importing it does not add a new Daewoo TV match. Existing
GitHub-derived TV profiles and the separately retrieved Astro profiles remain
separate sources. None of these records establishes physical appliance
compatibility.

The converter retains each native model row's category and numeric brand
association. It resolves the bundled English brand name where available and
otherwise retains its source name; it never substitutes a similarly named brand
from another device category. A missing brand mapping is explicitly excluded.
Source model IDs denote code sets, not verified retail model numbers.

For indexed records, the referenced `kk_remote_ircode_src` values are already
microsecond arrays. The inspected Mi raw path re-encrypts those arrays for its
normal raw decoder, then passes the decoded integers directly to Android
`ConsumerIrManager.transmit`. Unindexed Xiaomi records contain the same raw
arrays in the public Base64/AES/gzip JSON representation. This differs from
Mi's separate carrier-cycle encoder path: these values must not be multiplied
by a carrier period. Exact carrier hertz, complete timing arrays, original
button keys and `_r` captures are preserved.

Runtime bounds are checked offline: integer carriers from 20–60 kHz, 3–2,000
positive integer durations, each at most 1,000,000 microseconds, with total
duration at most 1,900,000 microseconds. Unsupported data is rejected or counted
without trimming frames, guessing a carrier, adding repeats or evaluating a
program.

## Reproduce offline

Use Python 3.11 or later with `sqlite3.Connection.deserialize`, the system
libarchive shared library with RAR4 support (`libarchive13` or `libarchive13t64`
on Debian/Ubuntu), and the pinned Python dependencies:

```sh
python3 -m venv /tmp/mi-apk-catalogue-venv
/tmp/mi-apk-catalogue-venv/bin/python -m pip install \
  -r tools/mi_apk_catalogue/requirements.txt
/tmp/mi-apk-catalogue-venv/bin/python tools/mi_apk_catalogue/generate_apk.py --check
/tmp/mi-apk-catalogue-venv/bin/python -m unittest discover \
  -s tools/mi_apk_catalogue -p 'test_*.py' -v
```

The default `--check` temporarily unpacks the committed 7z, verifies the complete
input chain, regenerates all accepted records, and compares every output byte.
No network access is used during conversion or testing. A previously extracted
APK can be supplied to skip 7z decompression; its exact size and SHA-256 are
still checked:

```sh
python3 tools/mi_apk_catalogue/generate_apk.py \
  --apk /path/to/MiRemote-7.2.4M.apk --check
```

Without `--check`, the tool writes only its expected files under
`app/src/main/assets/ir/mi_apk/` and its own two metadata files. Unknown output
files cause refusal rather than deletion. `--output` can select another asset
directory; the tool's manifest and import summary remain in this directory.

## Provenance and tests

`index.json` records exact APK/database/archive hashes and per-profile logical
source paths, native row/version, category, brand ID, source hash and generated
asset hash. A profile's `sourceSha256` covers its canonical original model row
and referenced waveform-source row, when present. Its brand hash covers the
canonical original brand row. These values allow independent source-association
review without shipping the database file. `generated-manifest.json` records
all 605 JSON output hashes and sizes.

Seven tests cover original brand and command association, exact carriers and
microseconds, encrypted and indexed raw formats, stateful exclusions, invalid
signals, missing rows and unsafe IDs, changed/extra output detection, and all
packaged profile hashes/counts. A real bundled 37,910-Hz vector also checks that
microsecond timings and its long gap are preserved.

The converter code is MIT licensed. Xiaomi/Kookong source data keeps separate
attribution and is not relicensed under the application or converter license.
No additional vendor-data rights, manufacturer endorsement, full-catalogue
access or physical-device certification are asserted.
