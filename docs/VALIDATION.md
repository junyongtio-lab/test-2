# Version 1.4.0 validation

Validated on 21 October 2026. This release adds raw TV/AC data converted from the exact user-uploaded Mi Remote 7.2.4M/code 6500 APK, source-prioritized TV matching and independent Panasonic216 parameter encoding. The original GitHub and working Astro source profiles remain. There is no separate 7.4.2M source archive in the repository.

## Build and software checks

`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. There are **112 JVM tests**, with zero failures, errors or skips. Android lint reports **0 errors and 15 SetTextI18n warnings**; the UI remains English.

| Check | Result |
| --- | --- |
| JVM tests | 112 passed |
| Python tools and data tests | 68 passed: original catalogue 10, APK research 29, Astro conversion 7, uploaded-APK conversion 8, TV matching 8, Panasonic parameters 6 |
| Original GitHub/Astro data | Offline regeneration matched; retained original profile IDs and carriers/timings |
| Uploaded-APK conversion | 604 profiles / 24,204 commands recovered; all source identities, hashes, exact microseconds and carriers validated |
| TV source matching order | All 2,810 original TV associations retained across 1,023 source groups; Daewoo begins with xm_1_3399 / xm_1_3390 |
| Panasonic source verification | All selected numeric fields match three actual uploaded-APK records and three pinned GitHub model records; no Lua/native execution |
| Panasonic Java encoding | 13 regression cases, including an independent published golden packet, section checksums, complete LSB framing, per-model caps, state copying and hardware send gates |
| Android packaged-data checks | 32 passed without emitting IR |
| Installed emulator UI checks | 13 passed; exact original preferences restored; installed APK SHA-256 equals delivered file |

The exhaustive JVM loader checks read every imported APK signal asset and preserve all five Astro profiles / 216 commands. A real Android Keystore migration test retains Samsung/LG metadata, decryptable credentials and their exact ciphertext bytes. A new regression retains Panasonic requested-state selections alongside existing Astro and Wi-Fi selections.

Validation caught and corrected two real import integration errors: the three encrypted Xiaomi source records needed their own provenance-format label, and a source button named `_` needed a nonempty fallback display label. Source waveform arrays, command keys and carrier frequencies were not changed.

## Installed Android app and UI

The delivered APK installed as an in-place update on the retained Android 10 / API 29 x86_64 software emulator. All 32 packaged catalogue, encoder, hardware and credential checks and all 13 UI checks passed. The UI checks exercised Astro and fan controls, Daewoo candidate order, all six Panasonic candidates, untested preview replacement, temperature bounds, mode changes, toggle semantics and disabled transmission without an emitter. Exact original preferences were restored after the temporary fixtures. The pulled installed APK matched the delivered SHA-256. The report is recorded in [emulator-verification.json](../downloads/emulator-verification.json), with [instrumentation output](../downloads/catalog-validation.txt) and [current screenshots](screenshots/v1.4/).

This host has no KVM. Earlier attempts encountered a **System UI** nonresponse dialog and a test-harness reference to a fixture's old name after replacement; those attempts are not reported as passing. The harness now follows the renamed selection and records and limits recovery from the emulator System UI dialog. It does not ignore a Palm Remote nonresponse. The final passing run required zero dialog recoveries. Emulator performance does not establish physical-phone performance.

## Data and capability scope

| Raw category | Distinct brand labels | Profiles | Command associations |
| --- | ---: | ---: | ---: |
| TV | 1,021 | 3,381 | 147,768 |
| Decoder | 182 | 411 | 12,416 |
| AC | 40 | 101 | 1,004 |
| Fan | 133 | 422 | 4,181 |
| Combined | 1,291 | 4,315 | 165,369 |

Raw coverage combines 3,706 original GitHub profiles, five public-service Astro profiles and 604 uploaded-APK profiles. Source associations are counted separately even when waveforms repeat. Six Panasonic parameter profiles are additional selectable remotes; their generated states are not counted as recorded command associations. The total is 4,321 selectable profiles.

The raw APK importer records 701 stateful AC definitions that require an encoder; the Panasonic implementation supports three of those definitions plus three additional source profiles. This is not a port of all proprietary AC families or Mi Remote's entire online catalogue. Unsigned generic TV/AC service requests returned nonzero status 19; known TV/AC IDs submitted to the public STB endpoint returned code -6. No usable TV/AC service payload or authentication bypass is claimed. See [the investigation](TV_AC_COMPATIBILITY_INVESTIGATION.md), [APK importer](../tools/mi_apk_catalogue/README.md) and [Panasonic source verification](../tools/panasonic_ac/README.md).

## Artifact

- APK: [PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`; version **1.4.0**, versionCode **7**
- Minimum API 23, target API 35; Java-only, with no copied native libraries
- Size: 11,914,545 bytes
- SHA-256: `40f947fdb31b81b6b2b9b81463d6f218146e1c3b42aac0008277d2556ea82775`
- v1/v2 signature verified; development certificate SHA-256 `75e8320e3978d0cc65f0fb98c0bb82354a05dd02e1c34d96823d883cab9e4e64`, unchanged for in-place updates
- ZIP 16-KiB alignment passed; all 4,325 packaged asset files matched the current workspace byte for byte

The user confirms Palm Remote 1.3.0 controls their Ultra Box, but the exact working source keyset is unknown; the five source profiles remain unchanged. The user reports Daewoo TV and Panasonic AC failed with the previous version while Mi Remote controlled both on the same phone. Their exact models are unknown, and only some Daewoo candidates were tested. The new matching/encoding paths still require appliance testing; cloud checks do not establish a physical match or claim that every imported profile works with every model. APKs do not run on iPhone, and external IR accessories remain unsupported.
