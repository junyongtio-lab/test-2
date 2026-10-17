# Version 1.2.0 validation

Validated on 17 October 2026. This Android build replaces all previous IR sources with supported JSON signals from the published Mi Remote v2.1 GitHub archive. No physical appliance or phone was attached.

## Build and software checks

`./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. Android lint reports **No issues found**. The additional Android migration checks were compiled with `assembleDebugAndroidTest`.

| Check | Result |
| --- | --- |
| JVM tests | 60 passed, zero failures/errors/skips |
| Production Mi loader integration | All 3,706 profiles and 140,949 command associations passed hashes, source-key identity, labels, bounds and timing checks; eight-profile cache stays bounded |
| Hardware detection (included in JVM total) | 11 cases passed, including emitter-present, emitter-absent, query failures and carrier support |
| Offline database generator tests | 10 passed, including an independent complete-waveform golden vector, frequency units, duplicate-waveform label preservation and source model/brand associations |
| Offline regeneration | Pinned archive verified; every generated index/profile asset and input/output manifest matched byte for byte |
| Android APK catalogue/hardware/migration checks | 23 passed without emitting IR |

Android checks read the actual packaged index and representative lazy profile assets in all four categories. A real Android Keystore migration test removed obsolete IR/native-AC selections while retaining Samsung/LG metadata, decryptable credentials and their exact ciphertext bytes. The migration was idempotent and the temporary test records were removed afterward.

## Android upgrade and UI checks

All 12 UI/artifact checks passed on Android 10 / API 29 x86_64. The in-place update removed old IR/native-AC selections. The persistent hardware banner and Check IR diagnostic remained available before setup and on all tabs. Searchable Samsung TV, Airtel decoder, Hisense AC and Panasonic fan keysets saved and loaded their actual recorded buttons. IR commands stayed disabled because the emulator has no emitter. All four saved IR devices referenced only new Mi catalogue IDs. Decoder setup explicitly reported the missing Astro/NJOI data.

The installed APK was pulled back and matched the delivery SHA-256 exactly. No System UI nonresponse dialog occurred. See [home](screenshots/mi-home.png), [TV](screenshots/mi-tv-remote.png), [decoder coverage](screenshots/mi-decoder-coverage.png), [decoder remote](screenshots/mi-decoder-remote.png), [AC](screenshots/mi-ac-remote.png), [fan](screenshots/mi-fan-remote.png), [saved devices](screenshots/mi-saved-devices.png), [UI/installed-byte evidence](../downloads/emulator-verification.json) and [raw Android catalogue/migration output](../downloads/catalog-validation.txt).

## Data provenance and limits

Source: [ysard/mi_remote_database v2.1](https://github.com/ysard/mi_remote_database/releases/tag/v2.1). Original archive SHA-256: `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`. The original archive, standalone conversion tool and complete source/output hash manifest are included under [tools/mi_catalogue](../tools/mi_catalogue/README.md). AGPL notices are retained separately from independently written application code.

| Category | Brand labels | Keysets | Command associations |
| --- | ---: | ---: | ---: |
| TV | 1,021 | 2,810 | 123,883 |
| Set-top box / decoder | 181 | 406 | 12,200 |
| AC | 38 | 68 | 685 |
| Fan | 133 | 422 | 4,181 |
| Combined | 1,289 distinct | 3,706 | 140,949 |

The original decoded microsecond arrays and Hz carriers are preserved. No TVKill cycle counts, guessed protocol encodings, fabricated commands or executable AC templates are used. All 68 accepted AC records are upstream raw type 1; the other 1,760 AC model records use type 2 templates/programs. Invalid/unsupported source data is excluded with counts in the [index](../app/src/main/assets/ir/mi/index.json). Source categorization can be imperfect: the Aite keyset noted in [known limits](KNOWN_LIMITATIONS.md) contains decoder-style controls despite its upstream AC category.

**Astro, NJOI and PRISM+ are absent from the included snapshot.** Their previous profiles are removed. No substitute decoder profile is labelled as Astro compatible.

## Artifact

- APK: [PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`; version **1.2.0**, versionCode **5**
- Minimum API 23, target API 35; Java-only APK with no native ABI restriction
- Size: 9,787,999 bytes
- SHA-256: `9b83d724a738bd1cf8832dc73081fa05e48fd99edfc94a541557842a31165a6e`
- Signature verified with the same development certificate as prior versions: `75e8320e3978d0cc65f0fb98c0bb82354a05dd02e1c34d96823d883cab9e4e64`
- ZIP alignment passed; the APK contains no native libraries, old IR catalogue assets or uploaded Astro JSON
- APK contains the Mi index, exactly 3,706 Mi profile JSON assets and retained source/license notices

Hardware detection reports Android's built-in emitter service; it cannot establish actual physical emission or appliance compatibility. Android emulator and synthetic hardware-present tests do not certify any TV/decoder/AC/fan response. APKs do not run on iPhone. Store publication and production signing are separate from this GitHub delivery.
