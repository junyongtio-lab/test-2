# Mi Remote 7.2.4M investigation

Inspected on 18–19 October 2026. The user uploaded the exact archive to GitHub after the chat attachment failed. The extracted APK reports package `com.duokan.phone.remotecontroller`, version **7.2.4M**, version code **6500**, minimum Android API 22 and target API 34.

**Five actual Astro keysets were retrieved from the regional public service on 19 October and are bundled in Palm Remote 1.3.0.** All 216 command associations decode to valid raw timings. The source identifies Astro, but does not name Ultra Box, Ulti Box or NJOI models. The user reports that Mi Remote controls their Ultra Box; Palm Remote still needs testing on that decoder to select the correct candidate.

## Provenance and verification

| Item | Result |
| --- | --- |
| User archive | 24,137,558 bytes; SHA-256 `72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6` |
| Extracted APK | 34,320,753 bytes; SHA-256 `4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73` |
| Signature | Android v1/v2/v3 signatures verify; certificate identifies `CN=KeyCenter, O=Xiaomi` |
| Certificate SHA-256 | `7d5096707835623b3879f417cad3df0d6717246acd8dad55d2959d526d95ab0a` |
| Bytecode | 4 DEX files; JADX wrote 14,523 Java files and reported 340 decompilation errors |
| Native libraries | 5 ARM64 libraries, inventoried and inspected statically |
| Flat APK entries | 4 JSON files, 10 other asset entries, 4,157 resource entries; zero inventory entries skipped |
| Nested IR database | RAR resource containing a 5,878,784-byte SQLite database with 19 tables |

The [user-uploaded archive](https://github.com/junyongtio-lab/test-2/blob/f91a98e76015ad3011e3e4a674ffde229ff10bf8/downloads/com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22%28arm64-v8a%29%28nodpi%29_apkmirror.com.7z) matches the archived source bytes. The user supplied an [APKMirror release page](https://www.apkmirror.com/apk/beijing-xiaomi-mobile-software-co-ltd/mi-remote-controller-for-tvac/mi-remote-controller-for-tv-stb-ac-and-more-7-2-4m-release/mi-remote-controller-for-tv-stb-ac-and-more-7-2-4m-android-apk-download/), but the managed proxy blocked access to that site. Signature verification establishes integrity and certificate identity, not independent publisher provenance; a trusted publisher certificate comparison remains unperformed.

## Bundled data

The four plain JSON files are animation/configuration/build metadata, rather than a plain per-brand IR catalogue. The important additional data is `res/raw/irlocaldata`, mapped from original entry `res/Jy4`: a RAR archive containing `irlocaldata.db`. Its SQLite SHA-256 is `727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa`.

All applicable encrypted JSON columns in the 19 tables decoded with zero errors. The database includes 125 brand rows, 176 device-brand rows, 430 IR trees and 1,747 Kookong source-capture rows, plus China/India provider and lineup records. Its country table contains CN/EN/TW/IN. Searching decrypted and plain records found no Astro, NJOI, Ultra Box or Ulti Box record. Some numerical brand IDs lack bundled name mappings, so this does not prove that every unnamed waveform is incompatible; there is no evidence for assigning them to Astro.

Resource hits for `astro` were Albanian translations containing `Pastro`/`pastrohen`, not the Astro brand. Initial binary-string candidates also included false positives. The native inputs `all_output_dec.dat` and `b64.dat` are opaque IR/AC inputs; neither yields an attributable Astro profile from this analysis.

No bundled SQLite database, proprietary implementation or graphics were copied into the Android app. The existing 3,706-keyset GitHub catalogue remains unchanged, with five separately sourced online Astro keysets added as an offline supplement. The original inspection tools and metadata-only APK evidence can be reviewed under [tools/apk_inspection](../tools/apk_inspection/README.md). Public Astro responses, hashes and independent conversion are under [tools/mi_stb](../tools/mi_stb/README.md).

## Code and native findings

The raw Mi IR path uses Base64, AES-ECB, gzip and a JSON array of integer timings, followed by Android `ConsumerIrManager.transmit`. That path uses microseconds and agrees with Palm Remote's existing raw decoder/data format. A separate Kookong path accepts carrier-cycle counts and requires explicit conversion; it must not be confused with raw microseconds.

`libmiir.so` is a JNI decoder/transmit bridge, `libkksdk.so` supplies generic encoders requiring separately supplied model parameters, and `libluajava.so` is a Lua interpreter. These libraries do not by themselves provide a recoverable, named Astro command set. Native symbol/string inspection and relevant JNI disassembly were performed; this is not a claim to reconstruct every native function. JADX's 340 errors also prevent a claim of complete original-source recovery. Decompiled third-party files remain outside the application repository.

## Online Astro retrieval

For the non-China, non-India STB path, the recovered network client selects the Singapore server for Malaysia:

| GET endpoint | JSON in the URL's `data` parameter |
| --- | --- |
| `https://sg-urc.io.mi.com/app/public/get_stb_brand` | `{"country":"ID"}` in the inspected source; compare explicit `MY` separately |
| `https://sg-urc.io.mi.com/app/public/get_stb_match` | `{"country":"MY","brandid":"<returned ID>"}` |
| `https://sg-urc.io.mi.com/app/public/get_stb_keyset` | `{"id":"<returned match ID>"}` |

The relevant plain GET client has no account-authentication interceptor or legacy opaque request signature. Live anonymous requests succeeded on 19 October. Legacy signed `/controller/*` routes are separate. No embedded credentials are reused and no manufacturer support guarantee is implied.

Requests on 18 October failed with managed proxy CONNECT HTTP 403 while network readiness remained unknown. After the environment resumed on 19 October, runtime status reported the unrestricted policy enforced and the startup policy agreed. Ordinary HTTPS requests then returned HTTP 200/code 0. The proxy, TLS verification and honest independent research user agent were preserved; no bypass or alternate fetching service was used.

Both ID and MY brand-list queries returned the same list of 1,262 brands. Astro has brand ID `3833`. Its Malaysian match response returned five candidates, whose complete keysets were then retrieved:

| Source keyset | Carrier Hz | Recorded commands |
| --- | ---: | ---: |
| `kk_2_3833_536` | 36,000 | 58 |
| `kk_2_3833_1819` | 56,000 | 37 |
| `kk_2_3833_350` | 36,000 | 78 |
| `kk_2_3833_211` | 31,360 | 30 |
| `kk_2_3833_2160` | 38,000 | 13 |

The live full-keyset payload uses `result.data.key`, rather than the `rc` shape initially expected by the research helper. Initial structural rejection preserved the raw responses; the helper was corrected against this evidence. Every encrypted key decoded through the existing independent raw-format decoder, with zero excluded commands. The encrypted raw branch yields microseconds directly, including for these `kk_`-prefixed IDs; the separate carrier-cycle branch described above is not used. Source keys and alternate captures remain distinct, with exact carriers and complete timings preserved.

The app loads the hashed supplement locally and makes no Xiaomi requests at runtime. Choose **Home → Set up Astro → Find a working remote** and test several buttons on an IR-equipped Android phone. Source names alone do not identify a retail decoder or prove response. The earlier unverified NEC JSON remains removed, and another brand is never relabelled Astro. Exact response and generated-asset hashes support offline reproduction.

The inspected key parser explicitly pairs a normal key with its `key_r` capture. Its sending manager alternates normal and alternate captures. Palm Remote independently implements this recorded-pair behavior per saved source profile, advancing after an accepted transmission. It preserves every waveform and allows an explicitly selected `_r` button to replay exactly. This avoids repeatedly sending one toggle state while keeping failed sends from consuming the next state; no alternate waveform is generated.

## Sharing and backup limits

There is no verified generic Export JSON menu for the full waveform data in this release. Its Share QR contains vendor/device/brand/match identifiers, without timings; the menu is restricted by China-region/account conditions and may be absent in Malaysia. Cloud backup excludes full IR data and downloads codes again on restore. Full saved codes are in private app storage; ordinary Android backup is disabled. Do not instruct users to bypass private storage or claim a nonexistent export feature.

If a Share option is actually visible on the working Astro remote, its QR identifiers could help identify the precise online keyset. They would still need resolution to actual commands before bundling. No per-phone profile-import feature has been added to Palm Remote.

## Original Palm Remote changes

The recovered interface structure supports category selection, searchable brands, candidate tests and explicit confirmation before saving. Palm Remote implements that flow independently, with original vector drawings, a Home dashboard, grouped navigation/volume/channel/keypad controls and complete access to extra recorded buttons. No Xiaomi art or decompiled implementation is shipped.

The guided tester never declares appliance success automatically. Without an emitter it disables test transmission and confirmation. Browsing uses the bounded profile cache and restores the selected remote after cache eviction. The persistent **IR ready**, **No IR transmitter detected**, **IR check unavailable** and **Check IR** diagnostics remain available on every tab.
