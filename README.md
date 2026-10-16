# Palm Remote for Android

An account-free Android remote for **Samsung, LG, Panasonic, Hisense and Astro/NJOI**. No app registration, paid tiers, advertisements, analytics or backend.

This is a native Java Android application. Flutter's artifact host was unavailable in the build environment, so the requested installable Android app was implemented using the Android SDK directly.

## Install

Download [`downloads/PalmRemote.apk`](downloads/PalmRemote.apk) to your Android phone (Android 6.0 or later), open it and allow installation from that source if Android prompts. This development APK is debug-signed; it is not a Google Play production release. It does not run on iPhone.

## Connect a device

Tap **+**, choose the brand and choose a connection:

| Brand | Implemented connection | Requirements and limits |
| --- | --- | --- |
| Samsung | Official SmartThings API; sourced IR profiles | SmartThings needs an existing Samsung account, authorized device access, internet and a personal access token. No Palm account. IR needs a phone emitter and matching handset profile. |
| LG | Local webOS pairing; sourced IR profiles | Same trusted LAN, TV approval, and a webOS model accepting port 3000. Secure-only models are not supported by this transport. |
| Panasonic | Sourced IR profiles | Matching handset profile and Android consumer IR hardware. No all-model public manufacturer API verified. |
| Hisense | Sourced IR profiles | Matching handset profile and Android consumer IR hardware. VIDAA/Google TV/Roku are different platforms; no universal brand API claimed. |
| Astro/NJOI | Learned IR profile import and transmission | **No verified Malaysian decoder IR profile is bundled.** Supply a legitimately learned profile for your decoder and an IR-equipped Android phone. No unofficial decoder Wi-Fi interface is used. |

**Not every model is supported.** The app disables unavailable commands and checks for an IR emitter. Physical-device compatibility was not tested in the cloud. See [device support](docs/SUPPORTED_DEVICES.md), [integration sources](docs/API_INTEGRATIONS.md), and [validation evidence](docs/VALIDATION.md).

### Samsung authorization

Use Samsung's official developer portal to create a SmartThings personal access token with device read and execute permissions for your TV: <https://account.smartthings.com/tokens>. Add the TV to your existing SmartThings account first. Token policies and expiration are controlled by Samsung; current tokens may expire after 24 hours. Re-add the connection when a token expires. Only TV-related capabilities returned by the API are exposed. D-pad and numeric buttons are not invented when the API does not offer them.

### Infrared and Astro

Most Android phones have no IR emitter; iPhones have none. Merely installing an app cannot add this hardware. Selected TV handset profiles include provenance in the APK's assets. Astro support is a transmission/import engine, **not a claim of a ready-made compatible Astro code set**. See [learned profile format](docs/IR_PROFILE_FORMAT.md). External USB/Wi-Fi IR hubs are not implemented in this build.

## Build and test

Install JDK 21 and Android SDK platform 35/build-tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir`. Then:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. A cloud-specific activated toolchain is available with `source /workspace/toolchains/env.sh` in the prepared environment. Do not commit local SDK paths, credentials or signing keys.

## Privacy

Device names and imported IR profiles are stored only on the phone. Pairing credentials and Samsung tokens are encrypted using Android Keystore. Android backup is disabled. Samsung control sends authorized HTTPS requests to `api.smartthings.com`; LG control goes to the selected TV on the trusted local network. Remove a remote or clear app storage to delete its saved credentials. No customer email database exists because the app has no registration.

## Source and license

Application code is MIT licensed. Dependencies and IR data retain their own licenses; see the IR asset attribution. Brand names identify compatibility and do not imply endorsement.
