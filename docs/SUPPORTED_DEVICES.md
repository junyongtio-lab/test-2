# Supported-device scope

The deliverable is an **Android APK for Android 6.0 or newer**. An APK cannot be installed on an iPhone. This repository does not contain an iOS application or an Apple-signed build.

No physical TV or Astro decoder was available during development. The entries below describe implemented transports and prerequisites, not certified model compatibility. Support must be confirmed on the exact TV/decoder model, region and firmware. There is no all-model guarantee.

| Device family | Implemented route | What is required | Limits |
| --- | --- | --- | --- |
| Samsung TVs available to SmartThings | Official SmartThings cloud API | Existing Samsung account, TV registered in SmartThings, internet and a token permitting device read/command execution | Only capabilities actually advertised by the device are offered. Token expiry and TV standby settings apply. No universal navigation/numeric keypad. |
| LG webOS TVs accepting port 3000 | Published Connect SDK local SSAP subset | Phone and powered TV on the same private LAN; remote/mobile-control setting enabled; approve TV pairing prompt | Legacy unencrypted connection only. Secure-only port-3001 models and pre-webOS NetCast sets are outside this transport's scope. |
| Selected Samsung TV handsets | Bundled IR profile or imported profile | Android phone with built-in consumer IR emitter, supported carrier frequency, matching handset profile and line of sight | Profile similarity is not an all-model guarantee. Commands absent from the selected profile remain unavailable. |
| Selected LG TV handsets | Bundled IR profile or imported profile | Same IR requirements | Matching a brand alone does not establish matching IR codes. |
| Selected Panasonic TV handsets | Bundled IR profile or imported profile | Same IR requirements | No Panasonic Wi-Fi control is shipped; network availability does not change this. |
| Selected Hisense TV handsets | Bundled IR profile or imported profile | Same IR requirements | No VIDAA, Google TV or Roku network-control adapter is shipped. |
| Astro/NJOI decoder | User-supplied learned IR profile | A legitimately obtained profile for the exact decoder and an Android consumer IR emitter | **No compatible Astro decoder profile is bundled.** Profile import/transmission does not establish decoder compatibility. |

The bundled IR catalog records handset labels and source provenance in [profiles.json](../app/src/main/assets/ir/profiles.json). These are the source's profile labels, not a tested list of TV models:

| Brand | Bundled source labels |
| --- | --- |
| Samsung | `BN59-01315B` |
| LG | `AKB75375604` |
| Panasonic | `Viera`; `TH-43HS550K / HS550K / GX650K` |
| Hisense | `EN3Y39H`; `EN3AH39H`; `U7G`; `55U6K (2023)` |
| Astro/NJOI | None; compatible user-supplied capture required |

These eight profiles come from the community [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB/tree/d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d), pinned to commit `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d` under CC0-1.0. Source originals and provenance are included with the catalog. Every bundled profile is marked physically untested. Community handset data is distinct from a manufacturer-published API; a profile's label does not establish compatibility with every device sharing that label or brand.

## Setup

For Samsung SmartThings, associate the TV with your existing SmartThings account, obtain an appropriately scoped personal access token through Samsung's portal, then choose **Add → Samsung → SmartThings**. The app lists devices the token may access and enables supported commands. There is no account registration or paid plan for this app. Samsung authorization remains necessary for this route.

For LG local control, put the phone and TV on the same trusted Wi-Fi/LAN, enable the TV's mobile/remote-control option if present, and choose the LG Wi-Fi route. Select a discovered TV or enter its local IP address. Approve the prompt on the television. Guest Wi-Fi, client isolation or a sleeping TV can prevent discovery/connection. Use manual entry when discovery is unavailable. A firmware requiring secure port 3001 cannot connect with this build.

For infrared, the phone must actually contain an IR emitter. Most Android phones do not; iPhones do not provide a consumer IR emitter. Select the matching handset profile, point the phone's emitter toward the device and test a harmless command such as volume. Profiles may have only a subset of buttons. External USB, Bluetooth and Wi-Fi IR hubs are not implemented.

For Astro, obtain a compatible learned profile, import it using **Devices → Import learned IR profile**, then save it as an Astro infrared remote. Capturing a signal requires an IR receiver or learning device; this app's transmission engine cannot perform the capture. The supported import structure is in [IR profile format](IR_PROFILE_FORMAT.md).

## How to report a tested model

Record manufacturer, exact model, region, firmware, phone model/Android version, chosen transport/profile and the commands tested. Check pairing/reconnection, volume, mute, channel, navigation and standby behavior separately. A successful socket connection or transmitted IR frame is not proof that the TV performed a command.

See [integration sources](API_INTEGRATIONS.md), [known limitations](KNOWN_LIMITATIONS.md) and [validation evidence](VALIDATION.md).
