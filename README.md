# Palm Remote for Android

Version **1.2.0** replaces every previous infrared catalogue with JSON-derived signals from [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release **v2.1**. It includes televisions, set-top boxes, air conditioners and fans. The **IR ready**, **No IR transmitter detected** and **IR check unavailable** indicators and **Check IR** diagnostic remain available on every tab. No Palm account, subscription, advertisements or analytics.

**Astro, NJOI and PRISM+ are absent from this source snapshot.** The previous unverified Astro JSON and other earlier IR datasets have been removed. This version does not provide an Astro Ultra Box / Ulti Box profile. A different decoder brand must not be assumed compatible.

## Download and install

Download [PalmRemote.apk](downloads/PalmRemote.apk) on an Android phone running Android 6.0 or later. Open it and allow installation from that source if prompted. This is a development-signed APK, not a Google Play production release. It retains the app's package and signing certificate for an in-place update. APKs do not run on iPhone.

After the new catalogue loads successfully, the update removes previous saved IR remotes, imported profiles and adjustable AC remotes. Add replacement remotes from the Mi Remote catalogue. Saved Samsung SmartThings and LG Wi-Fi devices and their encrypted credentials are retained. There is no JSON import step on the phone.

## Included controls

| Mi Remote category | Brand labels | Remote keysets | Command associations |
| --- | ---: | ---: | ---: |
| Television | 1,021 | 2,810 | 123,883 |
| Set-top box / decoder | 181 | 406 | 12,200 |
| Air conditioner | 38 | 68 | 685 |
| Fan | 133 | 422 | 4,181 |
| Total | 1,289 distinct across categories | 3,706 | 140,949 |

Brand counts overlap between categories. Command totals count source button associations, including repeated waveforms, rather than distinct IR signals. Model identifiers are the source's remote/code-set IDs, not verified retail appliance model numbers. All profiles are physically untested by this project. See [supported-device scope](docs/SUPPORTED_DEVICES.md) and the exact [catalogue coverage](app/src/main/assets/ir/mi/index.json).

Samsung's optional official SmartThings integration and LG's local webOS pairing remain available separately from infrared. SmartThings requires existing Samsung authorization and internet; the LG transport requires a compatible TV on the same trusted LAN. There is no all-model network API for the other brands.

## Use the app

Tap **+**, choose **Television**, **Set-top box / decoder**, **Air conditioner** or **Fan**, search the source brand, and choose a Mi Remote keyset. Point the phone's emitter at the appliance. Every IR remote shows the decoded commands actually present in its source JSON, with readable button labels. Missing buttons are not invented.

AC and fan controls replay the available recorded signals. An AC command may change several settings together. This version cannot generate arbitrary temperatures, modes or fan speeds, and cannot read or synchronize the appliance's state. Many upstream AC definitions are proprietary templates or executable fragments rather than raw signals; these are excluded.

## Check the phone's IR hardware

The indicator below the app heading stays visible on **Remote, Devices and Help**, including before adding a device. It refreshes when the app returns to the foreground.

- **IR ready**: Android detected a built-in emitter. Carrier support is shown separately; this does not verify the selected codes or appliance response.
- **No IR transmitter detected**: Android exposes no built-in emitter to this app. IR sending stays disabled.
- **IR check unavailable**: Android could not complete the query. Tap **Check IR** to retry; a query error is not treated as proof that hardware is absent.

Tap **Check IR** for the phone model, Android/app versions, reported carrier-frequency ranges and whether 38 kHz is supported. The check works independently of the catalogue and sends no infrared signal. Most Android phones have no consumer IR emitter. External IR accessories are not supported.

## Build and validation

Install JDK 21, Android SDK Platform 35 and Build Tools 35.0.0, then run:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. The Java Android app no longer includes a native AC encoder; NDK and CMake are unnecessary. Prepared cloud tasks can first source `/workspace/toolchains/env.sh`. See [build instructions](docs/BUILD_INSTRUCTIONS.md), [validation](docs/VALIDATION.md) and [known limits](docs/KNOWN_LIMITATIONS.md).

The offline [Mi catalogue generator](tools/mi_catalogue/README.md) verifies the pinned GitHub archive, decodes its supported JSON signals, and records per-input and per-output hashes. The app itself makes no Xiaomi API requests.

## Privacy and source

Device names and saved remote selections stay on the phone. Pairing keys and SmartThings tokens use Android Keystore encryption. Android backup is disabled. Samsung requests go to `api.smartthings.com`; LG control goes to the selected private-LAN TV. Removing a saved Wi-Fi device deletes its associated credential.

The separately written application code is MIT. The Mi Remote source data and standalone catalogue tooling retain their AGPL-3.0-or-later notices; they are not relicensed MIT. See [IR attribution](app/src/main/assets/ir/ATTRIBUTION.md), [third-party notices](app/src/main/assets/licenses/NOTICE.txt) and [integration sources](docs/API_INTEGRATIONS.md).
