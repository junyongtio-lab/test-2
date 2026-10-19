# Astro regional STB supplement

This directory contains independently written offline conversion code and pinned
public JSON responses retrieved on **19 October 2026** from
`https://sg-urc.io.mi.com`. The anonymous regional endpoints were identified by
static interoperability analysis of the user-supplied Mi Remote 7.2.4M APK.
No account, token, SDK signing material or private device storage was used.

The returned brand list identifies **Astro**, brand/provider ID **3833**, device
type **2**. The Malaysian match query returned these five keysets in this order:

| Source keyset | Carrier Hz | Commands |
| --- | ---: | ---: |
| `kk_2_3833_536` | 36,000 | 58 |
| `kk_2_3833_1819` | 56,000 | 37 |
| `kk_2_3833_350` | 36,000 | 78 |
| `kk_2_3833_211` | 31,360 | 30 |
| `kk_2_3833_2160` | 38,000 | 13 |

All **216** commands decoded without exclusions. These identifiers describe
remote code sets. The service returned no Ultra Box or Ulti Box retail-model
labels, and Palm Remote has not been tested on a physical decoder. The user's
report that Mi Remote works with an Ultra Box does not identify which of these
keysets it selected. Use Palm Remote's guided testing on an IR-equipped Android
phone, then confirm the appliance actually responds before saving a candidate.

`provenance.json` records exact request URLs, timestamps, response hashes and
sizes. `source/` retains the seven exact, bounded HTTP-200/code-0 response bodies:
one Malaysian brand list, its Astro matches, and five full keysets. Source
hashes are independently pinned in `generate_stb.py`; changing a source file
causes conversion to fail. `generated-manifest.json` records generated asset
hashes. Responses are data; they contain no vendor source implementation,
graphics, native libraries or executable model scripts.

## Timing representation and aliases

The live keyset response uses `result.data.key`, containing Base64/AES-256-ECB/
gzip/JSON integer arrays. This is the documented public raw-waveform format
also understood by the existing GitHub catalogue converter. The AES format
constant is a file-format constant, not a personal credential.

Static tracing of Mi Remote's raw native boundary shows these decoded arrays
passed directly to Android `ConsumerIrManager.transmit`. The durations are
already **microseconds**. The carrier is retained exactly; multiplying these
durations by a carrier period would corrupt them. The separate Kookong
carrier-cycle/encoder path does not describe these returned raw arrays. This
converter bounds decrypted expansion to 32 KiB, validates integer timings and
rejects waveforms exceeding 1.9 seconds.

Original command keys remain unchanged, including numeric keys, `FF`, `REW`,
`i`, `?`, `Clear/Cancel`, and `_r` alternatives. No command is invented, merged
with another key or inferred as “off.” Normal and alternate waveforms remain
separate commands in the app. In particular, preserving an `_r` entry does not
claim that all required toggle/repeat behavior has been physically established.

The first research fetcher expected a regional `rc` representation and rejected
these HTTP-success responses on schema grounds. Those failed-validation records
remain outside the repository as research evidence. The fetcher now explicitly
recognizes either `key` or `rc`, with separate representation metadata; it never
treats structural validation as timing decoding or hardware verification.

## Reproduce offline

```sh
/workspace/toolchains/mi-catalogue-venv/bin/python -m pip install \
  -r tools/mi_stb/requirements.txt
/workspace/toolchains/mi-catalogue-venv/bin/python tools/mi_stb/generate_stb.py --check
/workspace/toolchains/mi-catalogue-venv/bin/python -m unittest discover \
  -s tools/mi_stb -p 'test_*.py' -v
```

Run without `--check` to regenerate only
`app/src/main/assets/ir/mi_stb/` and this tool's generated manifest. The Android
app reads those bundled schema-2 assets offline; it does not call Xiaomi's API
or ask the user to import a JSON file. The supplement is separate from the
unchanged GitHub-derived `ir/mi/` catalogue.

The original converter code is MIT licensed. The source response data is
attributed to the Xiaomi/Kookong remote service and is **not relicensed** under
Palm Remote's code license or the GitHub database's license. No additional
rights over that data, manufacturer certification or endorsement are asserted.
