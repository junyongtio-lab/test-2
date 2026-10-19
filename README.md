# Palm Remote for Android

Version **1.3.0** adds five Astro decoder keysets, an original device dashboard, category tiles, grouped remote controls and a guided keyset tester. It retains the JSON-derived catalogue from [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release **v2.1**, and supplements it with recorded Astro signals retrieved from Xiaomi's public regional STB service. It includes televisions, set-top boxes, air conditioners and fans. Sending also tolerates phones that detect an emitter but do not expose carrier-frequency ranges; known unsupported frequencies remain blocked. The **IR ready**, **No IR transmitter detected** and **IR check unavailable** indicators and **Check IR** diagnostic remain available on Home, Remote, Devices and Help. No Palm account, subscription, advertisements or analytics.

**Astro now has five bundled candidate keysets with 216 recorded commands.** Choose **Decoder → Astro → Guided testing** to find a match. The source identifies Astro, but does not identify Ultra Box or Ulti Box retail models. Palm Remote's response on a real decoder remains untested; compatibility with either model is not guaranteed. No NJOI-labelled or PRISM+ profile is included. The previous unverified Astro JSON remains removed.

The supplied **Mi Remote 7.2.4M / code 6500 APK was extracted and inspected**, including its nested local IR database and native decoder. The Astro supplement comes from the public online STB catalogue identified during that research; its JSON is converted offline and bundled, so the phone needs no file import or Xiaomi request. See the [investigation](docs/MI_REMOTE_724M_INVESTIGATION.md). The new interface uses original drawings and independently written code; no Xiaomi graphics or decompiled implementation are included.

## Download and install

Download [PalmRemote.apk](downloads/PalmRemote.apk) on an Android phone running Android 6.0 or later. Open it and allow installation from that source if prompted. This is a development-signed APK, not a Google Play production release. It retains the app's package and signing certificate for an in-place update. APKs do not run on iPhone.

When upgrading from versions before 1.2.0, successful catalogue loading removes obsolete IR remotes, imported profiles and adjustable AC remotes. Version 1.3.0 keeps saved remotes already using the current catalogue. Add replacement remotes from the Mi Remote catalogue. Saved Samsung SmartThings and LG Wi-Fi devices and their encrypted credentials are retained. There is no JSON import step on the phone.

## Included controls

| Mi Remote category | Brand labels | Remote keysets | Command associations |
| --- | ---: | ---: | ---: |
| Television | 1,021 | 2,810 | 123,883 |
| Set-top box / decoder | 182 | 411 | 12,416 |
| Air conditioner | 38 | 68 | 685 |
| Fan | 133 | 422 | 4,181 |
| Total | 1,290 distinct across categories | 3,711 | 141,165 |

The unchanged GitHub archive contributes 3,706 keysets and 140,949 command associations; the Astro supplement contributes five keysets and 216 associations. Brand counts overlap between categories. Command totals count source button associations, including repeated waveforms, rather than distinct IR signals. Model identifiers are the source's remote/code-set IDs, not verified retail appliance model numbers. All profiles are physically untested by this project. See [supported-device scope](docs/SUPPORTED_DEVICES.md), the [GitHub catalogue index](app/src/main/assets/ir/mi/index.json) and the [Astro supplement index](app/src/main/assets/ir/mi_stb/index.json).

Samsung's optional official SmartThings integration and LG's local webOS pairing remain available separately from infrared. SmartThings requires existing Samsung authorization and internet; the LG transport requires a compatible TV on the same trusted LAN. There is no all-model network API for the other brands.

## Use the app

Use the **Home** category tiles or tap **+**, choose **Television**, **Set-top box / decoder**, **Air conditioner** or **Fan**, then search the source brand. Choose a keyset directly or use the guided keyset tester. The tester sends only a selected recorded command after you tap the test button. Only you can confirm whether the appliance responded; the phone cannot detect that response. Save a remote explicitly when you are satisfied with the match.

Point the phone's emitter at the appliance. Common recorded controls are grouped into power/input, navigation, volume/channel and keypad sections where matching source keys exist. **More commands** keeps all remaining recorded buttons accessible. The presentation never creates missing IR signals or treats repeated-key suffixes as invented commands. Saved remotes are available from Home and Devices. See the [Home dashboard](docs/screenshots/v1.3/palm-home.png), [Astro keysets](docs/screenshots/v1.3/palm-astro-keysets.png) and [Astro controls](docs/screenshots/v1.3/palm-astro-remote.png).

AC and fan controls replay the available recorded signals. An AC command may change several settings together. This version cannot generate arbitrary temperatures, modes or fan speeds, and cannot read or synchronize the appliance's state. Many upstream AC definitions are proprietary templates or executable fragments rather than raw signals; these are excluded.

## Check the phone's IR hardware

The indicator below the app heading stays visible on **Home, Remote, Devices and Help**, including before adding a device. It refreshes when the app returns to the foreground.

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

The offline [Mi catalogue generator](tools/mi_catalogue/README.md) verifies the pinned GitHub archive, decodes its supported JSON signals, and records per-input and per-output hashes. The separate [Astro supplement tooling](tools/mi_stb/) records the public response provenance and converts its supported timings offline. The app itself makes no Xiaomi API requests.

## Privacy and source

Device names and saved remote selections stay on the phone. Pairing keys and SmartThings tokens use Android Keystore encryption. Android backup is disabled. Samsung requests go to `api.smartthings.com`; LG control goes to the selected private-LAN TV. Removing a saved Wi-Fi device deletes its associated credential.

The separately written application code is MIT. The GitHub Mi Remote source data and standalone catalogue tooling retain their AGPL-3.0-or-later notices; they are not relicensed MIT. The additional public Astro response data is attributed to Xiaomi/Kookong; public availability does not establish a reuse license, and no MIT or AGPL license is assigned to it. No additional rights over vendor data are asserted. See [IR attribution](app/src/main/assets/ir/ATTRIBUTION.md), [third-party notices](app/src/main/assets/licenses/NOTICE.txt) and [integration sources](docs/API_INTEGRATIONS.md).
