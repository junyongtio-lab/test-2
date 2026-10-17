# Version 1.1.1 validation

Validated in the prepared cloud environment on 17 October 2026. No physical decoder, TV, AC or phone was attached. The owner confirmed that the supplied Astro codes have not been tested; they are bundled unchanged.

## Build and software checks

`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` completed **BUILD SUCCESSFUL**. Android lint reports **No issues found**.

| Check | Outcome |
| --- | --- |
| JVM unit/protocol tests | 49 passed; zero failures, errors or skips |
| Astro adapter tests (included in JVM total) | 6 passed: exact uploaded bytes, literal MSB/LSB wire vectors, all 29 commands, collision preservation and invalid-source rejection |
| PRISM+/Daewoo source-fact/vector tests | 8 passed, including deterministic catalogue regeneration |
| Android native/JNI encoding | 423 passed across all 141 AC profiles |
| Actual Android asset-loader/category/old-ID checks | 11 passed, including both built-in Astro entries and their supplied buttons |

The original `astro_ultrabox.json` is in the APK with SHA-256 `1daa99e6a4be4407ef1830c733b0522830e635683f902334b1022f582b15d747`, matching the uploaded file exactly. Its adapter generates two explicit interpretations because bit order was not specified. Independent literal vectors verify Power, Home and digit 1 in each interpretation; every resulting frame has 67 durations at 38 kHz and lasts 67,980 microseconds. Power/Blue and Mute/Right remain identical as supplied. No commands were invented to separate these pairs.

Existing HTTP/WebSocket, endpoint, SmartThings, IR-bound and catalogue safety tests also passed. The bundled catalogue has 14 TV/decoder entries with 441 command entries, including the same 29 Astro source codes interpreted twice. Recorded AC coverage remains 158 profiles and 1,656 commands; adjustable AC coverage remains 141 profiles and 65 families.

## Artifact and upgrade

- APK: [downloads/PalmRemote.apk](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`
- Version: `1.1.1` / versionCode `3`; minimum API 23, target API 35
- Size: 5,184,892 bytes
- SHA-256: `57ee9270d7eda6eb0ed308ad59ce3548186f7534b21d6f2e5ea96f12a38674d8`
- APK signatures verified; certificate matches the earlier APK (`75e8320e3978d0cc65f0fb98c0bb82354a05dd02e1c34d96823d883cab9e4e64`)
- Native ARM64, ARMv7 and x86_64 libraries are included; 16 KB APK ZIP alignment passed
- All three native libraries are byte-identical to version 1.1.0, retaining their previously checked ELF page alignment
- The exact uploaded Astro JSON is packaged as an asset and loaded by the actual Android catalogue without a phone file import

The package installed using `pm install -r` and launched with `Status: ok` on Android 10 / API 29 x86_64. Device-side checks exercised the real JNI library and packaged assets without sending infrared. Raw output is in [native-validation.txt](../downloads/native-validation.txt).

## Astro setup and Android UI

Nine UI/artifact checks passed: both Astro choices appeared without importing JSON; supplied-code guidance appeared before saving; the default remote opened; missing IR hardware was reported; Record, colour and Guide controls were visible and disabled without an emitter; the numeric keypad opened; the alternate profile saved; both previously saved devices were retained; and the installed APK was pulled back and its SHA-256 matched the delivery file exactly.

See the [built-in profile picker](screenshots/astro-profiles.png), [profile guidance](screenshots/astro-guidance.png), [Astro remote](screenshots/astro-remote.png), [decoder controls](screenshots/astro-decoder-buttons.png) and [installed-byte verification](../downloads/emulator-verification.json). The software emulator displayed one System UI nonresponse dialog; automation chose Wait and the completed app checks passed.

## Unchanged-code baseline

Version 1.1.0 previously passed 28 standalone IR encoder checks and 17,360 native host AC checks, including model/state combinations, decoder round trips, the Gree checksum vector and the Teknopoint regression. Those separate host sweeps were not repeated for this JSON/UI update; their source code is unchanged, native binaries match byte for byte, and the 423 Android JNI checks were rerun. Numerical legacy capture facts retain provenance and `NOASSERTION` source-rights metadata; the LGPL library license, corresponding source and relink instructions remain included.

Emulator execution establishes installation, library loading, catalogue behavior and encoder output. It does not establish real Astro decoder responses, physical appliance compatibility, home Wi-Fi pairing, support for every model, iPhone support or Google Play publication. An Android IR emitter and correct decoder codes are still required.
