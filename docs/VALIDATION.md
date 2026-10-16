# Version 1.1.0 validation

Validated in the prepared cloud environment on 16 October 2026. No physical TV, AC, decoder or phone was attached.

## Build and software checks

`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. Android lint reports **No issues found**. Compiler/deprecation warnings in upstream/build tooling do not change that outcome.

| Check | Outcome |
| --- | --- |
| JVM unit/protocol tests | 43 passed; zero failures, errors or skips |
| Separate IR encoder known-vector/invalid-input checks | 28 passed |
| PRISM+/Daewoo source-fact/vector tests | 8 passed |
| Native AC host checks | 17,360 passed across 65 stateful families and 141 advertised remote profiles |
| Android native/JNI encoding | 423 passed across all 141 profiles, with first-send and power on/off |
| Actual Android asset-loader/category/old-ID checks | 7 passed |

The JVM tests exercise real local mock HTTP/WebSocket servers, TV approval/rejection, command failures, endpoint validation, SmartThings capabilities, redirects and imported IR bounds. The expanded catalogue test validates all 170 capture profiles and 2,039 commands, including unique IDs, category separation, honest source metadata and Android timing limits. Captured AC frames remain fixed-state; no relative-temperature behavior is invented.

Native host checks cover each advertised model and requested mode/temperature/fan/swing combination. Eleven representative stateful families are decoded back through the upstream decoder to verify protocol, power/mode/temperature and changed-temperature frames. A known published Gree checksum vector and Teknopoint regression are included. Native encoding is checked on an Android 10 x86_64 emulator through the real JNI library, without transmitting IR.

## Artifact and upgrade

- APK: [downloads/PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`
- Version: `1.1.0` / versionCode `2`; minimum API 23, target API 35
- Size: 5,186,407 bytes
- SHA-256: `77b3a78e81220a42fd8ce33404c41b27070c301808957bcd84de6189b6e2f9ee`
- APK signatures verified; certificate matches version 1.0.0 (`75e8320e…b9e4e64`)
- Native libraries included for ARM64, ARMv7 and x86_64; 16 KB APK ZIP alignment and ARM64/x86_64 ELF LOAD alignment checks passed
- Original legacy capture text and the former incorrect CC0 license asset are absent; numerical facts have exact provenance and NOASSERTION source rights metadata
- The APK includes the LGPL native-library license; full corresponding source and relink instructions are in this repository

The existing package was updated in place using `pm install -r`, preserving its data. All eight original TV profile IDs remain present. A real Android emulator launch returned `Status: ok`; direct IR is disabled because this emulator has no emitter. The installed APK was pulled back and its SHA-256 exactly matched the delivery file. See [TV brands](screenshots/tv-brands.png), [PRISM+ remote](screenshots/prism-remote.png), [AC profile picker](screenshots/ac-profile-picker.png), [AC remote](screenshots/ac-remote.png), [AC controls](screenshots/ac-controls.png), [saved devices](screenshots/saved-devices.png), [installed-byte verification](../downloads/emulator-verification.json) and [raw JNI test output](../downloads/native-validation.txt). Seven UI flow checks passed, including disabled sending on a phone without IR. A software-emulator System UI nonresponse dialog was dismissed during automation; the app itself remained resumed and the completed screen checks were clean.

Emulator execution establishes build, installation, library loading, catalogue/UI behavior and encoder output. It does not establish physical appliance responses, Wi-Fi pairing in a home network, universal brand/model support, iPhone support, Astro code availability or Google Play publication. Samsung still needs manufacturer authorization; Astro still needs an imported learned decoder profile.
