# Mi Remote infrared data attribution

Palm Remote **1.5.0** bundles three separately attributed raw-signal sources, six Panasonic 216-bit parameter profiles and complete source data for all 734 AC records in the supplied APK. Independently written Android code reads the converted JSON or encodes supported Panasonic settings through Android ConsumerIrManager. The app makes no Xiaomi API requests and requires no smartphone JSON import. It includes no Xiaomi native libraries, decompiled Java implementation or copied Xiaomi graphics. Vendor parameter/hook text is preserved as inert source data and is never executed.

## GitHub catalogue and matching metadata

The unchanged base catalogue comes from [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), maintained by ysard and contributors:

- Release [v2.1](https://github.com/ysard/mi_remote_database/releases/tag/v2.1); archive 7,892,348 bytes.
- Archive SHA-256: `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`.
- Format-reference revision: `8a60b419506e8c407f2b756ec8ab4b4bc446678d`.
- Upstream **AGPL-3.0-or-later** notices are retained at `licenses/mi_remote_database-LICENSE.txt`.

The source archive, independent offline AGPL converter and input/output hashes are retained under `tools/mi_catalogue/` in the project repository. The base `mi/` assets remain unchanged: **3,706 profiles and 140,949 command associations**. Original source keys, brand associations, Hz carriers and complete microsecond timing arrays are preserved. The TVKill carrier-cycle export is not used. Source code-set identifiers are not verified retail model numbers.

The separate `matching_order.json` derives TV candidate ordering from the same pinned brand trees and Malaysian regional priorities. It adds no profiles or signals and changes no waveform. Its original generator and provenance are under `tools/mi_matching/`. It does not reproduce Mi's complete conditional matching tree.

This community source is not a manufacturer-published API or endorsement. Its data is not relicensed under the application's MIT license. Proprietary/executable AC definitions omitted by the raw converter remain explicit exclusions in `mi/index.json`; no vendor program is executed.

## Astro public regional supplement

Development-time anonymous queries to `https://sg-urc.io.mi.com` returned **Astro brand ID 3833**, Malaysia (MY), and five keysets: `kk_2_3833_536`, `kk_2_3833_1819`, `kk_2_3833_350`, `kk_2_3833_211` and `kk_2_3833_2160`. Their **216 command associations** retain exact carriers, complete timings and alternate captures under `mi_stb/`.

Original response JSON, request timestamps, hashes, the independent converter and generated manifest are retained under `tools/mi_stb/`. Data is attributed to the Xiaomi/Kookong service. Public access does not establish a reuse license; neither MIT nor the GitHub source's AGPL notices are assigned to this separate vendor source. No additional rights, certification or endorsement are asserted. No Ultra Box/Ulti Box retail-model label was returned, and no other brand is relabelled Astro.

## Mi Remote 7.2.4M bundled TV/AC data

The user supplied the archive for package `com.duokan.phone.remotecontroller`, version **7.2.4M / code 6500**. The extracted APK SHA-256 is `4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73`. Its pinned RAR resource contains `irlocaldata.db`, SHA-256 `727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa`.

The independent importer under `tools/mi_apk_catalogue/` verifies the APK, RAR, database, schemas and row counts and preserves attributable brand/table/row identities. It converts **571 TV profiles / 23,885 commands** and **33 raw AC profiles / 319 commands** into `mi_apk/`, with no raw-profile exclusions. All complete validated raw arrays remain microseconds. Another **701 stateful AC definitions** require separate encoding and are not imported as raw commands. The APK's full SQLite database and native engines are not included in the app.

These vendor data are attributed to Xiaomi/Kookong and the respective source brands. APK possession and signature verification do not establish a redistribution license or independently authenticate the publisher certificate. No additional vendor-data rights or MIT/AGPL relicensing are asserted.

## Complete AC source-data copy

At the user's request, `mi_ac_database/` additionally retains **all 734 AC source records across 45 brand labels**, with no omissions: 33 recorded profiles and 701 stateful definitions. Each JSON asset preserves the original database row, complete referenced source row, decoded definition, original brand row and decoded brand data. Original encrypted strings and parameter/hook text remain unchanged. The independent generator and per-file hashes are under `tools/mi_ac_database/`. This is vendor source data under the same separate attribution above, not newly MIT/AGPL-licensed data.

These assets are read as data only. No interpreter, vendor Java compiler, Lua runtime or native AC engine is included or invoked. Existing controls implement 36 source profiles (33 recorded and three Panasonic216); the remaining 698 are explicitly labelled as included data with controls unavailable. Preserving their source data is not implementation of their encoders or a physical compatibility claim. The three additional GitHub Panasonic profiles are outside the 734-source count.

## Independent Panasonic 216-bit encoder

`panasonic_ac/index.json` contains six non-executable parameter profiles for an independently written Java encoder. Records `137_2587`, `kk_3_137_4646` and `kk_3_137_7406` derive from the pinned APK database. Additional records `kk_3_137_7790`, `kk_3_137_7791` and `kk_3_137_7793` derive from the pinned GitHub archive and retain its source notices. Parameter hashes, provenance and the original extraction/generation tool are under `tools/panasonic_ac/`.

The encoder uses verified framing, packet fields and checksums; it does not execute or copy a vendor script or native encoder. Supported modes, temperature ranges, fan/swing fields and explicit-power versus power-toggle behavior are restricted by each parameter profile. This is a supported subset, not Mi Remote's complete AC engine or all Panasonic models. Settings describe the requested transmission, not appliance readings.

## Scope and physical evidence

The three raw sources total **4,315 profiles and 165,369 command associations**; the six adjustable Panasonic profiles are counted separately. Duplicate waveforms under different source associations remain distinct. All generated per-profile `hardwareTested` flags remain false. The user reports Palm Remote 1.3.0 controls an Astro Ultra Box, without identifying its exact keyset; that report does not mark all five profiles tested. No physical TV/AC testing occurred in the cloud.

The earlier owner-supplied Astro JSON, Flipper-IRDB captures, learned imports and native IRremoteESP8266 engine remain removed. There is no NJOI-labelled or PRISM+ profile. Source presence, hardware detection and a successful Android transmit do not certify appliance compatibility.

## Experimental protocol candidates in 1.6.0

Four independently implemented Java candidates use the pinned Mi APK source templates and timings. Protocol layouts and checksums were cross-checked against IRremoteESP8266 commit `1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f` (LGPL-2.1-or-later). The upstream C++ library is used only as an external validation reference; it is not bundled or executed in Android. See `docs/AC_PROTOCOL_CANDIDATES.md` and `tools/ac_families/README.md`. This does not assert rights to redistribute vendor implementations or compatibility with all model profiles.
