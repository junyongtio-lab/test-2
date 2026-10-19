# Mi Remote infrared JSON attribution

Palm Remote version 1.3.0 bundles the published GitHub JSON archive of [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), maintained by ysard and contributors, plus a separate public regional STB JSON supplement for Astro attributed to Xiaomi/Kookong. No earlier Flipper-IRDB captures, owner-supplied Astro JSON, learned imports or IRremoteESP8266 stateful encoders remain in this app version.

## GitHub catalogue

- GitHub release: [v2.1](https://github.com/ysard/mi_remote_database/releases/tag/v2.1)
- Source archive: [database_dump.tar.xz](https://github.com/ysard/mi_remote_database/releases/download/v2.1/database_dump.tar.xz), 7,892,348 bytes
- Archive SHA-256: `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`
- Upstream format-reference commit: `8a60b419506e8c407f2b756ec8ab4b4bc446678d`
- Upstream license notices: **AGPL-3.0-or-later**, retained at `licenses/mi_remote_database-LICENSE.txt` in the APK

The original archive and the independent offline AGPL-3.0-or-later generator are published in this repository under `tools/mi_catalogue/`. The source data is not relicensed under the application code's MIT license. No additional rights over Xiaomi or manufacturer material are asserted. The upstream project is a community database and is not represented as a manufacturer-published API, certification or endorsement.

Included source device IDs are 1 (TV), 2 (set-top box), 3 (AC), 6 (fan), 11 (cable/satellite) and 12 (internet box). Source brand-to-keyset references and original command keys are retained. Readable display labels are derived from those keys. Model identifiers describe code sets and are not independently verified appliance model numbers. Accepted encrypted/compressed raw arrays are decoded offline; carrier hertz and microsecond timing integers are preserved. The TVKill carrier-cycle export is not used.

Proprietary AC templates, executable fragments, unsupported representations, invalid carriers and empty responses are excluded rather than guessed or executed. Exact accepted coverage and exclusions appear in `mi/index.json`. Each profile records original brand/model JSON paths and SHA-256 hashes. `tools/mi_catalogue/source-manifest.json` records all input/output hashes and supports deterministic offline verification.

This unchanged GitHub source contributes 3,706 keysets and 140,949 source command associations. Its included categories have no Astro, NJOI, PRISM+ or PrismPlus brand. The separate Astro supplement below does not alter the original archive or its attribution.

## Astro regional STB supplement

Development-time anonymous queries to Xiaomi's public regional STB service at `https://sg-urc.io.mi.com` supplied brand, match-list and keyset JSON for Malaysia. The source identifies Astro brand ID 3833 and five code sets: `kk_2_3833_536`, `kk_2_3833_1819`, `kk_2_3833_350`, `kk_2_3833_211` and `kk_2_3833_2160`. Their 216 recorded command associations are decoded offline with source carriers and complete microsecond timings preserved. These JSON assets are recorded separately in `mi_stb/index.json` and `mi_stb/profiles/`.

The original independently written converter, retained public response JSON, request provenance, SHA-256 hashes and generated output manifest are published under `tools/mi_stb/` in the project repository. The response data is attributed to Xiaomi/Kookong. Public access does not establish a reuse license, and neither the application's MIT license nor the GitHub source's AGPL notices are assigned to this separate vendor source. No additional rights over vendor data, certification or endorsement are asserted.

The source does not label these candidates Ultra Box or Ulti Box. No NJOI-labelled or PRISM+ keyset is included. The earlier unverified Astro file remains removed; no substitute brand is relabelled Astro. The combined catalogue contains 3,711 keysets and 141,165 command associations. All bundled profiles have `hardwareTested: false`; no physical appliance testing was performed in the cloud. Android hardware detection and successful transmission do not establish compatibility with a particular appliance.

The Android app makes no Xiaomi API requests and requires no smartphone JSON import. It independently reads the bundled converted JSON and transmits only supported raw commands through Android ConsumerIrManager. It does not include upstream Python, either standalone converter, Xiaomi native libraries, decompiled implementation or copied Xiaomi graphics in its runtime.
