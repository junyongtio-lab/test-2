# Validation evidence

Validated on 16 October 2026 in the prepared Linux cloud environment. No physical television, decoder or phone was attached.

## Build and automated checks

The final application source passed:

```sh
source /workspace/toolchains/env.sh
./gradlew --no-daemon assembleDebug lintDebug testDebugUnitTest
```

Result: **BUILD SUCCESSFUL**, 39 tests executed, zero failures/errors/skips.

| Suite | Passed |
| --- | ---: |
| SmartThings client contracts | 17 |
| LG webOS mock-TV integration | 14 |
| Local endpoint and SSDP validation | 5 |
| Packaged IR profiles and hostile input bounds | 3 |

The protocol tests run actual local HTTP/WebSocket fake services. They exercise pairing approval/rejection, saved pairing keys, input messages, command errors, socket failure, live mute state, destination validation and cancellation. SmartThings contracts cover authorization, device capability filtering, live toggle state, channel/input commands, pagination, malformed responses and token redirect protection. They do not authenticate against a real Samsung account or pair with a real LG television.

The separate Java IR encoder test runner executed **28 passing checks** for known frame bytes, NEC/Samsung/Kaseikyo encoding, raw bounds and Pronto parsing. All eight packaged profiles (327 commands) include provenance and valid transmission patterns. None is marked physically tested.

Android lint exits successfully with zero errors. It retains one advisory asking for a legacy backup XML resource; this app explicitly sets `allowBackup=false` and `fullBackupContent=false`, with Android 12+ cloud/device-transfer exclusion rules. No lint baseline or disabled assertions are used.

The workspace-local setup helper was rerun successfully against the installed toolchain, and Gradle wrapper activation was exercised. Build dependencies preserve TLS verification; downloaded toolchain archives were checksum-verified.

## APK

- File: [`downloads/PalmRemote.apk`](../downloads/PalmRemote.apk)
- Package: `com.universalremote.app`
- Version: `1.0.0` / code `1`
- Minimum Android: API 23 (Android 6.0)
- Target Android: API 35
- Size: 394,443 bytes
- SHA-256: `f385f117dc59e4d1fb4402877ff67929ba67e819a0bc2cbdad89e5a83f9b62ef`
- `apksigner verify`: successful (v1 and v2 signatures)
- Signing: Android development certificate, not production Google Play signing

Machine-readable metadata and the checksum are included next to the APK. The source and APK contain no shared manufacturer token or private signing key.

## Runtime smoke check

The exact delivered APK was installed in an Android 10 / API 29 x86_64 software emulator using direct ADB-over-TCP. Installation returned `Success`; `MainActivity` reached the resumed state, and a clean rendered home screen was captured. The installed `base.apk` was pulled from the emulator and its SHA-256 matched the delivered file exactly; see [installed-artifact verification](../downloads/emulator-verification.json) and [home screenshot](screenshots/home.png).

The cloud host has no KVM acceleration: Android itself booted slowly, briefly displayed a System UI nonresponse dialog, and the first cold `am start -W` timed out before the UI later rendered. The final smoke run showed the app resumed without a dialog. These system-level delays are not recorded as a successful fast-start benchmark. No Palm Remote crash was observed during the limited smoke run.

This smoke check establishes installation and application startup only. Actual TV commands, Wi-Fi discovery on a home LAN, IR emission and Astro decoder responses remain **unverified on physical hardware**. Astro requires a compatible user-supplied learned IR profile; none is bundled. No iOS app, Play publication, backend deployment, payment or all-model compatibility is claimed.
