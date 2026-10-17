# Version 1.1.2 validation

Validated on 17 October 2026. No physical phone, decoder, TV or AC was attached. Hardware-present and hardware-query-error branches use injected JVM probes; the actual Android emulator has no IR emitter.

## Build and tests

`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. Android lint reports **No issues found**.

| Check | Outcome |
| --- | --- |
| JVM unit/protocol tests | 60 passed; zero failures, errors or skips |
| IR hardware probe tests (included above) | 11 passed: emitter present/absent, query errors, unknown/malformed carrier data, inclusive 38 kHz boundaries and unsupported carriers |
| Astro adapter tests (included above) | 6 passed: exact source bytes, literal MSB/LSB vectors, 29 mapped commands, duplicate-code preservation and invalid inputs |
| Android native/JNI encoding | 423 passed across all 141 AC profiles |
| Actual Android asset-loader/category/old-ID checks | 11 passed, including bundled Astro profiles |
| Android UI and artifact checks | 11 passed |

Hardware detection reads Android's consumer IR service on a separate worker, independently of catalogue loading and TV pairing. A confirmed emitter produces **IR ready**; a missing service or false emitter result produces **No IR transmitter detected**. Query failures produce **IR check unavailable**. A carrier lookup failure retains confirmed emitter detection while reporting carrier support as unknown. The check sends no IR signal.

## Android UI and upgrade

The indicator was checked above the scrolling content on Remote, Devices and Help, before any saved device existed, and with the saved Astro remote selected. Check IR displayed the installed version, phone model, Android version and actual emitter result. Check again and foreground return refreshed detection. IR sending remained disabled without an emitter.

The existing app was upgraded using `pm install -r`; all four saved devices were retained. The first-use test temporarily moved the emulator's device preferences, then restored them and verified the original device IDs. No preferences were cleared. No System UI nonresponse dialog occurred in this run.

See [first-use status](screenshots/ir-status-first-use.png), [diagnostic dialog](screenshots/ir-status-diagnostics.png), [saved Astro remote](screenshots/ir-status-astro.png), [UI/installed-byte results](../downloads/emulator-verification.json) and [raw Android JNI/catalogue output](../downloads/native-validation.txt).

## APK identity

- APK: [PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`
- Version: **1.1.2**, versionCode **4**; minimum API 23, target API 35
- Size: 5,184,892 bytes
- SHA-256: `10246ebbafccc8f1d8eca4573813e0006c7fc5f5467b8a46ca17c2b01b01172d`
- Signature verified; development certificate matches earlier versions: `75e8320e3978d0cc65f0fb98c0bb82354a05dd02e1c34d96823d883cab9e4e64`
- APK ZIP alignment passed at 16 KB; ARM64, ARMv7 and x86_64 native libraries are byte-identical to version 1.1.1, retaining their previously verified page alignment
- The installed APK was pulled back and matched the delivery SHA-256 exactly

The supplied Astro JSON remains unchanged, with SHA-256 `1daa99e6a4be4407ef1830c733b0522830e635683f902334b1022f582b15d747`. Both bit-order interpretations and all 29 source commands remain available without smartphone import. Power/Blue and Mute/Right remain duplicate code pairs; real decoder compatibility is unverified.

## Unchanged-code baseline

Earlier versions passed 28 standalone IR encoder checks, 17,360 native host AC checks and 8 PRISM+/Daewoo source-regeneration tests. Those separate host sweeps were not repeated for this hardware-status update; the shared encoder, native libraries and catalogue data are unchanged. Current JVM protocol/IR tests, 423 Android JNI checks and 11 catalogue checks were rerun. TV/decoder coverage remains 14 profile entries and 441 command entries; AC coverage remains 141 adjustable profiles/65 families plus 158 recorded profiles. Licenses, corresponding native source and relink instructions remain included.

Hardware detection and emulator tests do not establish physical signal emission, correct Astro codes, appliance response, universal model support, home Wi-Fi pairing, iPhone support or Google Play publication.
