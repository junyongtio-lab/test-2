# Version 1.3.0 validation

Validated on 18–19 October 2026. This Android build adds the original dashboard, grouped recorded controls, guided keyset tester and five actual Astro keysets. Its GitHub v2.1 IR catalogue remains unchanged, with the Astro source retained separately. No physical appliance or phone was attached; the user separately reports Mi Remote controls their Ultra Box, which does not establish Palm Remote compatibility.

## Build and software checks

`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. Android lint reports **0 errors and 8 localization warnings**; the app UI remains English. The additional Android migration checks were compiled with `assembleDebugAndroidTest`.

| Check | Result |
| --- | --- |
| JVM tests | 91 passed, zero failures/errors/skips |
| Production Mi loader integration | All 3,706 profiles and 140,949 command associations passed hashes, source-key identity, labels, bounds and timing checks; eight-profile cache stays bounded |
| Recorded control grouping/cache recovery (included in JVM total) | Five source-key partition/alias cases and a real selected-profile cache-eviction/reload regression passed |
| Astro source and loading | Seven converter tests and seven JVM supplement-loader tests passed; all five profiles and 216 source commands retain identity, hashes, complete timings and carriers |
| Recorded alternate sending (included in JVM total) | Eight cases passed, including all 68 actual Astro normal/alternate pairs, per-profile state, exact explicit alternates and no state advance after failed sends |
| Static APK research helpers | 29 tests passed; actual pinned archive extraction and repeated local-database inspection also verified |
| IR sending compatibility (included in JVM total) | 10 fake-phone cases passed: missing emitter, query/transmit errors, unknown ranges, known matches/mismatches and unchanged valid signals |
| Hardware detection (included in JVM total) | 11 cases passed, including emitter-present, emitter-absent, query failures and carrier support |
| Offline database generator tests | 10 passed, including an independent complete-waveform golden vector, frequency units, duplicate-waveform label preservation and source model/brand associations |
| Offline regeneration | Pinned archive verified; every generated index/profile asset and input/output manifest matched byte for byte |
| Android APK catalogue/Astro/hardware/migration checks | 25 passed without emitting IR |
| Final installed-APK UI checks | 16 passed; installed bytes exactly match delivery SHA-256 |

Android checks read both actual packaged indexes, every Astro command and representative lazy profile assets in all four categories. A real Android Keystore migration test removed obsolete IR/native-AC selections while retaining Samsung/LG metadata, decryptable credentials and their exact ciphertext bytes. The migration was idempotent and the temporary test records were removed afterward.

## Android upgrade and UI checks

The delivered APK installed as an in-place update on Android 10 / API 29 x86_64 and launched successfully. The software emulator has no hardware acceleration; an initial cold launch hit the Android startup deadline, and System UI displayed a nonresponse dialog. A subsequent lower-resolution run launched successfully. Final UI check results and installed-byte comparison are recorded separately in [emulator-verification.json](../downloads/emulator-verification.json). These emulator incidents do not establish physical-device performance.

The final 19 October run had no System UI wait dialogs. All five Astro candidates loaded in the guided tester with the correct source IDs; sending and response confirmation stayed disabled without an emitter. Saving a labelled preview required no import and opened the actual grouped/complete recorded controls. Existing TV, decoder, AC and fan pages, Devices and Help passed on the same APK. The 20 [current screenshots](screenshots/v1.3/) show actual emulator states, including missing IR hardware. The installed APK was pulled and its SHA-256 matched the delivered file exactly.

## Mi Remote 7.2.4M research

The user-supplied archive and extracted APK hashes were verified. Manifest and v1/v2/v3 signatures verified version 7.2.4M/code6500. Four DEX files, five ARM64 libraries and all ZIP entries were inventoried; relevant raw IR decoder and UI/network paths were inspected. JADX reported 340 errors, so complete source recovery is not claimed. The nested RAR/SQLite database was extracted and all 3,616 applicable encrypted JSON records decoded with zero errors. No attributable Astro model was found in that bundled database; some numerical IDs remain unnamed.

After the environment's network policy became enforced on 19 October, anonymous regional public STB requests succeeded. The actual Astro brand ID 3833 led to five keysets and 216 valid commands, now bundled offline. The source has no Ultra Box / Ulti Box retail-model labels. All decoded arrays are microseconds, including the 56-kHz keyset; no carrier-cycle rescaling is applied. Seven pinned public responses and their provenance plus generated-output hashes are retained under [tools/mi_stb](../tools/mi_stb/README.md). A fresh full-keyset request with the corrected native-`key` validator matched its pinned response hash. No Palm physical response is claimed. See [detailed findings](MI_REMOTE_724M_INVESTIGATION.md).

## Data provenance and limits

Source: [ysard/mi_remote_database v2.1](https://github.com/ysard/mi_remote_database/releases/tag/v2.1). Original archive SHA-256: `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`. The original archive, standalone conversion tool and complete source/output hash manifest are included under [tools/mi_catalogue](../tools/mi_catalogue/README.md). AGPL notices are retained separately from independently written application code.

| Category | Brand labels | Keysets | Command associations |
| --- | ---: | ---: | ---: |
| TV | 1,021 | 2,810 | 123,883 |
| Set-top box / decoder | 182 | 411 | 12,416 |
| AC | 38 | 68 | 685 |
| Fan | 133 | 422 | 4,181 |
| Combined | 1,290 distinct | 3,711 | 141,165 |

The original decoded microsecond arrays and Hz carriers are preserved. No TVKill cycle counts, guessed protocol encodings, fabricated commands or executable AC templates are used. All 68 accepted AC records are upstream raw type 1; the other 1,760 AC model records use type 2 templates/programs. Invalid/unsupported source data is excluded with counts in the [index](../app/src/main/assets/ir/mi/index.json). Source categorization can be imperfect: the Aite keyset noted in [known limits](KNOWN_LIMITATIONS.md) contains decoder-style controls despite its upstream AC category.

The unchanged GitHub snapshot contributes 3,706 profiles / 140,949 commands; the separate Astro supplement adds five / 216. **NJOI-labelled and PRISM+ profiles are absent.** The earlier unverified Astro JSON remains removed. The new candidates are associated with Astro by the actual service responses, without assigning unverified Ultra Box or Ulti Box model names.

## Artifact

- APK: [PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`; version **1.3.0**, versionCode **6**
- Minimum API 23, target API 35; Java-only APK with no native ABI restriction
- Size: 10,009,046 bytes
- SHA-256: `116392d2f489154bc65924a8d9ff00deddb29a954fb90be82de5e0b6066e3a79`
- Signature verified with the same development certificate as prior versions: `75e8320e3978d0cc65f0fb98c0bb82354a05dd02e1c34d96823d883cab9e4e64`
- ZIP alignment passed; the APK contains no native libraries, old IR catalogue assets or uploaded Astro JSON
- APK contains both indexes, exactly 3,706 GitHub Mi profile JSON assets, five Astro profile JSON assets and retained source/attribution notices

Hardware detection reports Android's built-in emitter service; it cannot establish actual physical emission or appliance compatibility. Android emulator and synthetic hardware-present tests do not certify any TV/decoder/AC/fan response. APKs do not run on iPhone. Store publication and production signing are separate from this GitHub delivery.
