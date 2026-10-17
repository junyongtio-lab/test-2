# Palm Remote for Android

Version **1.1.2** adds an always-visible **IR hardware status** and a **Check IR** diagnostic button. The supplied **Astro Ultra Box / Ulti Box JSON profile** remains built in, with no smartphone import needed. PRISM+, Daewoo, Samsung, LG, Panasonic and Hisense TV controls and the searchable air-conditioner infrared catalogue remain available. No app registration, paid account, subscriptions, advertisements or analytics.

## Download and install

Download [PalmRemote.apk](downloads/PalmRemote.apk) on an Android phone (Android 6.0 or later), open it and allow installation from that source if prompted. This is a development-signed APK, not a Google Play production release. It uses the previous app's package and signing certificate, so it can update versions 1.0.0 and 1.1.0 while keeping saved devices. APKs do not run on iPhone.

## Included controls

| Device | Implemented connection | Requirements and limits |
| --- | --- | --- |
| Samsung TV | Official SmartThings API; IR profiles | SmartThings requires an existing Samsung account, authorized token and internet. No Palm account. Only capabilities exposed by the TV are enabled. |
| LG TV | Local webOS pairing; IR profiles | Same trusted LAN, TV approval and a model accepting port 3000. Secure-only models are outside this transport. |
| Panasonic / Hisense TV | Matching IR profiles | Android consumer IR emitter and matching handset/model. No brand-wide Wi-Fi API is claimed. |
| PRISM+ TV | Sourced IR handset profile, 14 commands | Power, volume, mute and navigation on matching models; no Bluetooth voice control. |
| Daewoo TV | Three IR handset profiles, 42 commands total | Generic, R-40A15 and R-43A08 profiles. Missing source buttons stay disabled. |
| Astro Ultra Box / Ulti Box | Bundled user-supplied NEC IR profile, 29 commands | No file import needed. Two bit-order interpretations are available because the supplied JSON does not specify bit order. Physical decoder compatibility is unverified; see the caveats below. Other Astro/NJOI models can use a compatible learned profile. |
| Air conditioners: adjustable controls | 141 documented remote/protocol profiles across 79 brand labels and 65 encoded protocol families | Sends full requested AC state through the genuine IRremoteESP8266 encoders: power, temperature, mode, fan and vertical swing where supported. |
| Air conditioners: recorded controls | 158 handset profiles across 96 source brand labels, 1,656 recorded commands | Replays exact recorded states/commands; cannot generate arbitrary settings. Some source-category devices are air coolers or ventilation devices. |
| Other AC brands/models | Learned IR import for any AC manufacturer | Add a legitimately learned waveform profile with category `air_conditioner`. |

The AC picker combines **147 distinct searchable brand labels** from encoded and recorded profiles. This covers the available documented catalogue, **not every brand/model ever sold**. All physical TV/AC/decoder compatibility is untested in this cloud environment. Brand names identify compatibility and do not imply endorsement.

## Check the phone's IR hardware

The indicator below the app heading stays visible on **Remote, Devices and Help**, including before adding any device. It refreshes when the app returns to the foreground.

- **IR ready**: Android detected a built-in emitter. Carrier support is shown separately; this does not verify the selected remote codes or appliance response.
- **No IR transmitter detected**: Android exposes no built-in emitter to this app. IR sending stays disabled.
- **IR check unavailable**: Android could not complete the query. Tap **Check IR** to retry; a query error is not treated as proof that hardware is absent.

Tap **Check IR** for the phone model, Android/app versions, reported carrier-frequency ranges and whether 38 kHz is supported. The check works independently of remote-profile loading and sends no infrared signal. It does not add hardware or support external IR accessories.

## Use the app

Tap **+ → Television / Astro decoder** to choose a TV brand, or **+ → Air conditioner** and search a manufacturer/model. AC entries marked **Adjustable** use a full-state protocol encoder; entries marked **Recorded** replay captured signals. Point the phone's emitter at the appliance.

For the bundled decoder profile, choose **+ → Television / Astro decoder → Astro → Infrared**, then select the **Ultra Box / Ulti Box** default profile and save. It includes navigation, volume, channels, Guide, numeric entry, Record and the four colour buttons. If the decoder does not respond, try the alternate bit-order profile. Both entries use the same 29 supplied codes; the alternate changes only their transmission order.

The original [astro_ultrabox.json](app/src/main/assets/astro_ultrabox.json) is bundled unchanged. It is user-provided data, not a manufacturer-published or physically verified Astro code set. **Power and Blue share one code; Mute and Right share another.** Those pairs send identical signals and cannot perform distinct actions until corrected codes are supplied. Bundling this file removes the import step but does not establish compatibility with a real decoder.

For adjustable AC controls, choose power, temperature, mode, fan and swing, then tap **Send AC settings**. The app stores only the last successfully transmitted request; it cannot read the unit's actual state. Unsupported settings may be clamped or ignored by the selected protocol. Reset the last-sent state after using another remote. Toggle-based remotes can require synchronization.

**Most Android phones have no IR emitter.** The app detects this and disables transmission. Installing an APK cannot add hardware; external IR hubs and iPhone control are not implemented.

Samsung SmartThings is optional manufacturer authorization, not app registration. Use the [official token portal](https://account.smartthings.com/tokens) with device read/execute permissions; token lifetime is controlled by Samsung. Add the TV to your SmartThings account first.

## Build and validation

Install JDK 21, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.0.12077973 and CMake 3.22.1, then run:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
bash native_ir/tests/ac/run.sh
python3 -m unittest discover -s native_ir/tests -p 'test_tv_extra.py' -v
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Prepared cloud tasks can first source `/workspace/toolchains/env.sh`. See [build instructions](docs/BUILD_INSTRUCTIONS.md), [validation](docs/VALIDATION.md), [supported devices](docs/SUPPORTED_DEVICES.md) and [known limits](docs/KNOWN_LIMITATIONS.md). The app is native Java with a native C++ AC encoder.

## Privacy and source

Device names, IR profiles and requested AC state stay on the phone. Pairing keys and SmartThings tokens use Android Keystore encryption. Android backup is disabled. Samsung requests go to `api.smartthings.com`; LG control goes to the selected private-LAN TV. Removing a saved device deletes its associated credential.

Application code is MIT except the LGPL native AC adapter/library. [IRremoteESP8266 source, license and relink instructions](third_party/IRremoteESP8266/PALMREMOTE.md) are included. Numerical capture facts include exact provenance; legacy capture source licenses are accurately marked `NOASSERTION`, not CC0. Original legacy capture-file text is not bundled. See [IR attribution](app/src/main/assets/ir/ATTRIBUTION.md) and [API sources](docs/API_INTEGRATIONS.md).
