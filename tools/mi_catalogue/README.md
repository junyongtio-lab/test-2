# Offline Mi Remote JSON catalogue

The Android catalogue is generated only from the published GitHub JSON archive
of [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database),
release **v2.1**. The upstream source format reference is pinned at commit
`8a60b419506e8c407f2b756ec8ab4b4bc446678d`.

Archive: `database_dump.tar.xz`, 7,892,348 bytes, SHA-256
`44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`.
The original archive is committed under `source/`. The generator verifies its
hash and checks every consumed JSON against the archive contents.
`source-manifest.json` records every input JSON hash, every profile asset hash,
and the generated index hash. Output is deterministic and contains no timestamps.

This standalone build tool is AGPL-3.0-or-later. It independently implements
the file format documented by upstream; it does not import upstream Python,
execute embedded AC programs, query Xiaomi, or make network calls. It is not
linked into the Android application. The upstream repository and its exported
datasets carry AGPL notices. The upstream license is retained in the APK at
`licenses/mi_remote_database-LICENSE.txt`; this dataset is not relicensed MIT.
The app's separately written code keeps its existing license. No claim of
additional rights over Xiaomi or manufacturer data is made.

## Rebuild

From the repository root:

```sh
python3 -m venv /tmp/mi-catalogue-venv
/tmp/mi-catalogue-venv/bin/pip install -r tools/mi_catalogue/requirements.txt
/tmp/mi-catalogue-venv/bin/python tools/mi_catalogue/generate.py --check
/tmp/mi-catalogue-venv/bin/python -m unittest discover -s tools/mi_catalogue -p 'test_*.py'
```

The check regenerates from the committed archive entirely offline, compares
every byte of the index, profile assets and source manifest, and changes no
committed files. To regenerate those files intentionally, omit `--check`.
Optional `--archive` and `--database` arguments accept an identical archived
download or an existing extraction; modified JSON fails the archive hash check.

The build inputs are categories 1 (TV), 2 (set-top boxes), 3 (air conditioners),
6 (fans), 11 (cable/satellite), and 12 (internet boxes). Categories 2, 11 and 12
are grouped as `set_top_box` without inventing brand aliases. Models are
included only through their published brand references. Original code-set IDs
are retained, rather than presented as verified appliance model numbers.

## Signal handling

The original model JSON contains `data.frequency` in Hz and `data.key` button
associations. Each signal is Base64-encoded AES-256-ECB ciphertext, using the
public 32-ASCII-byte format constant published by upstream. After removing
space padding and decompressing gzip, the plaintext JSON is an array of
**microsecond durations**, alternating mark/space. These exact integers are
preserved; no protocol guessing, bit reversal, duration trimming, repeat
insertion, command merging, or carrier substitution is performed.

The upstream **TVKill** export uses **carrier-cycle counts** instead. It is not
used here. Sending TVKill counts as microseconds would produce incorrect IR.
Identical waveforms under different button names remain distinct command
associations, avoiding metadata reuse in the upstream full-pattern cache.

Only safe Android signals are included: integer 20,000–60,000 Hz carriers;
3–2,000 positive integer durations, at most 1,000,000 µs each and 1,900,000 µs
total. Invalid carriers, empty model responses and unsupported representations
are reported in `index.json` coverage. AC byte templates, Lua/Java fragments,
and proprietary state definitions are not executable raw signals and are
excluded. The remaining AC data provides only the specific decoded buttons;
it does not imply full temperature/state control for every brand.

The release has no Astro, NJOI, PRISM+ or PrismPlus brand in these categories.
There are therefore no bundled Astro Ultra/Ulti Box codes or PRISM+ codes from
this source. No substitute profile is fabricated. No profile has been tested
against physical appliances by this project.
