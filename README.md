# Palm Remote for Android

Version **1.6.5** gives all **64 AC brand labels** a first **Find a working remote · Guided testing** entry and adds experimental basic encoders for **70 of the previously unsupported definitions**. These cover power, Cool/Heat and 16–30 °C using two validated 112-bit timing variants. All 64 brands now have candidates to test, but this is **not** proof every model works. **624 source definitions remain unsupported; all 694 have not been completed.** Your existing Panasonic `137_2587` encoder is retained. See [AC testing and remaining gaps](docs/AC_GUIDED_TESTING.md).

Version **1.6.4** gives all **133 fan brands** a first **Find a working remote · Guided testing** option, covering **422 candidate remotes**. Confirm two different recorded button responses before saving. Six limited candidates contain just one button; those are explicitly labelled and can confirm only that function. All existing signals are retained. See the [fan testing guide](docs/FAN_GUIDED_TESTING.md) and [complete fan brand list](downloads/Fan-brand-coverage.csv). Actual fan compatibility requires testing on the fan.

Version **1.6.3** gives all **182 decoder brands** a first **Find a working remote · Guided testing** option, covering **411 candidate remotes**. Aim at the decoder and confirm two different recorded button responses before saving a working match. The five Astro candidates and existing TV, AC and fan signals are retained. See the [decoder testing guide](docs/DECODER_GUIDED_TESTING.md) and [complete decoder brand list](downloads/Decoder-brand-coverage.csv). Physical decoder compatibility requires testing on your box.

Version **1.6.2** extends the first **Find a working remote · Guided testing** option to every bundled TV brand: **1,020 selectable brands and 3,381 remote candidates**. This includes Sony, Philips, TCL, Toshiba, Daewoo, JVC and the other catalogue brands. Confirm two different button responses before saving a working match. See the [complete TV brand list](downloads/TV-brand-coverage.csv) and [testing instructions](docs/TV_GUIDED_TESTING.md). Physical compatibility requires testing on the TV.

Version **1.6.1** improves Samsung, LG, Hisense, Panasonic, Sharp and Xiaomi TV setup. Each brand offers **Find a working remote · Guided testing** first. Confirm responses to two different recorded buttons before saving a working TV match; changing candidates clears those confirmations. See [TV testing instructions](docs/TV_GUIDED_TESTING.md). Physical TV compatibility still requires testing on your TV.

Version **1.6.0** adds four **experimental AC protocol candidates** using templates and timing from the supplied Mi Remote 7.2.4M archive: LG28 (`22_3577`), Mitsubishi144 (`kk_3_123_12255`), Daikin160 (`kk_3_58_4871`) and SamsungAC (`kk_3_125_4745`). Each exposes Cool/Heat, 16–30 °C, four fan speeds and explicit power. Existing Panasonic216 controls remain. **These are four specific profiles, not support for all 698 remaining AC definitions.** Exact equivalence to Mi's engine and physical appliance response are unverified. See [encoder scope and evidence](docs/AC_PROTOCOL_CANDIDATES.md).

All **734 AC source profiles across 45 brand labels** remain bundled offline. The existing 36 source-mapped controls are retained; four additional records now offer experimental protocol candidates, and 694 other records have no controls. Open **Home → Browse all 734 AC profiles**, search the source ID, and tap **Try protocol candidate**. Or select the matching AC brand and its experimental candidate. Test the signal on your AC before saving a working match; saving a preview sends nothing.

The existing 604 converted TV/AC raw profiles, six Panasonic AC parameter profiles and source-prioritized TV matching remain. On a phone with an IR emitter, selecting a TV candidate opens guided testing before it can be saved as a working match. Existing saved recorded remotes can be tested again with **Find a working remote**.

The supplied archive identifies **Mi Remote 7.2.4M / code 6500**. Its nested IR database is converted into bundled JSON offline. The existing GitHub Mi catalogue and five Astro profiles remain available. The complete Mi online catalogue and all proprietary AC encoders are not contained in this import; see [supported scope](docs/SUPPORTED_DEVICES.md) and the [TV/AC investigation](docs/TV_AC_COMPATIBILITY_INVESTIGATION.md).

The user reports that Palm Remote 1.3.0 controls their **Astro Ultra Box**. Those five Astro profiles and their source IDs are retained. The new Daewoo TV matching flow and Panasonic AC controls still require testing on the user's appliances. No Palm account, subscription, advertisements, analytics or smartphone JSON import is required.

## Download and install

Download [PalmRemote.apk](downloads/PalmRemote.apk) on an Android phone running Android 6.0 or later. Open it and allow installation from that source if prompted. The development-signed APK retains the package and signing certificate for an in-place update. APKs do not run on iPhone.

Version 1.6.0 retains saved current Mi remotes and Samsung/LG Wi-Fi devices with their encrypted credentials. Upgrades from before 1.2.0 remove obsolete IR selections and imports after the replacement catalogues validate. The earlier unverified Astro JSON and native AC engine remain removed.

## Included controls

| Raw IR category | Brand labels | Remote keysets | Recorded command associations |
| --- | ---: | ---: | ---: |
| Television | 1,021 | 3,381 | 147,768 |
| Set-top box / decoder | 182 | 411 | 12,416 |
| Air conditioner | 40 | 101 | 1,004 |
| Fan | 133 | 422 | 4,181 |
| Total | 1,291 distinct across categories | 4,315 | 165,369 |

The unchanged GitHub archive contributes 3,706 raw keysets and 140,949 associations; the Astro service contributes five and 216; the uploaded APK contributes 604 and 24,204. Counts preserve source associations, including repeated waveforms, rather than counting distinct signals. Brand labels overlap between categories. Source keyset IDs are not retail appliance model numbers.

**Six Panasonic216 parameter profiles are additional to the raw table.** They generate complete messages for 16–30 °C, Cool/Heat/Dry and each profile's supported fan/swing settings. Fan operating mode appears only where mapped. Auto operating mode is excluded. Five profiles have explicit power on/off; `kk_3_137_7793` uses a power toggle. Three profiles derive from the uploaded APK and three from the existing GitHub Mi source. Other AC families may need different encoding. No appliance state is read from the AC.

Samsung's optional official SmartThings integration and LG's local webOS pairing remain separate from infrared. SmartThings needs existing Samsung authorization and internet; LG needs a compatible TV on the same trusted LAN. There is no all-model network API for the other brands.

## Use the app

Use **Home → category → brand**. For a Daewoo TV, choose **Find a working remote**, point the phone at the TV, and test the candidates with **Next remote** or **Previous**. Daewoo's 13 candidates now follow the source matching-tree priority, beginning with `xm_1_3399` and `xm_1_3390`. Selecting a brand or the first numbered candidate does not identify a compatible model. Confirm an actual appliance response before saving. On an existing recorded remote, **Find a working remote** can replace that saved selection after confirmation.

For Panasonic AC, choose **Air conditioner → Panasonic → Find a Panasonic AC remote**. Test the six parameter candidates; the test sends a power-on or power-toggle message with Cool 24 °C. Save after confirming the AC responds. An explicitly labelled untested preview is also available, with sending disabled when no emitter is detected. Editing requested settings sends nothing until **Send requested settings** or a power button is tapped. Existing recorded Panasonic choices remain available separately.

Recorded remotes group available power/input, navigation, volume/channel and keypad controls. **More commands** and **All recorded buttons** retain access to every recorded source key. Actual `key_r` captures provide alternate sends where present; no missing waveform or off command is invented. Saved remotes remain available from Home and Devices. See [1.5 screenshots](docs/screenshots/v1.5/) and [validation evidence](docs/VALIDATION.md).

## Check the phone's IR hardware

The indicator below the heading stays visible on **Home, Remote, Devices and Help**, including before adding a device:

- **IR ready**: Android detected a built-in emitter. This does not verify a code set or appliance response.
- **No IR transmitter detected**: Android exposes no built-in emitter; IR sending stays disabled.
- **IR check unavailable**: the query failed. Tap **Check IR** to retry.

**Check IR** reports the phone/app versions, carrier capabilities and selected source details without transmitting. A confirmed emitter may omit its optional carrier-range query; validated signals keep their original carrier, while known unsupported frequencies remain blocked. Most Android phones have no consumer IR emitter. External IR accessories are not supported.

## Build and validation

Install JDK 21, Android SDK Platform 35 and Build Tools 35.0.0, then run:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. The app remains Java-only; NDK and CMake are unnecessary. Prepared cloud tasks can first source `/workspace/toolchains/env.sh`. See [build instructions](docs/BUILD_INSTRUCTIONS.md), [validation](docs/VALIDATION.md) and [known limits](docs/KNOWN_LIMITATIONS.md).

Offline source tools cover the [GitHub catalogue](tools/mi_catalogue/README.md), [Astro responses](tools/mi_stb/README.md), [uploaded APK database](tools/mi_apk_catalogue/), [TV ordering](tools/mi_matching/README.md) and [Panasonic parameters](tools/panasonic_ac/). No Xiaomi API request is made by the Android app.

## Privacy and source

Device names, selected profiles and requested AC settings stay in private phone storage. Pairing keys and SmartThings tokens use Android Keystore encryption. Android backup is disabled. Samsung requests go to `api.smartthings.com`; LG control goes to the selected private-LAN TV. Removing a saved Wi-Fi device deletes its credential.

Application code and independent new helpers are MIT. The GitHub Mi dataset and its standalone conversion tooling retain AGPL-3.0-or-later notices. Xiaomi/Kookong APK data and public Astro responses are attributed separately and are not relicensed MIT or AGPL. No Xiaomi graphics, decompiled Java implementation or native libraries are shipped. Original AC parameter/hook text is retained as inert source data and is never executed. See [IR attribution](app/src/main/assets/ir/ATTRIBUTION.md), [third-party notices](app/src/main/assets/licenses/NOTICE.txt) and [integration sources](docs/API_INTEGRATIONS.md).
