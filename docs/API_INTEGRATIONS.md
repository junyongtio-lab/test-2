# Integration sources and boundaries

This Android build has no application account, subscription, advertising service or application backend. It cannot provide an account-free network interface where a television manufacturer requires authorization. A manufacturer account used to authorize SmartThings is separate from an account for this app.

There is no verified public remote-control API covering every model of Samsung, LG, Panasonic and Hisense. Brand names cover different generations and operating systems. Manufacturer developer APIs for applications running **on a television** are not automatically APIs for a phone to control that television.

## Samsung: SmartThings

The app uses Samsung's published SmartThings REST interface at `https://api.smartthings.com/v1`. It lists devices authorized by the user, inspects their advertised capabilities and submits capability commands to a selected device. Unsupported controls remain unavailable. SmartThings does not provide a universal D-pad or numeric keypad for every Samsung TV.

The user needs an existing Samsung/SmartThings account, a TV already associated with it, internet access and an access token authorized to read that device and execute commands. Tokens expire according to Samsung's current policy. This app does not issue tokens or contain a shared token. Tokens belong in the app's local credential field, never in source control or a GitHub issue.

Sources:

- [Official SmartThings Core SDK](https://github.com/SmartThingsCommunity/smartthings-core-sdk): its README documents personal access tokens, bearer authentication and refreshed-token authentication.
- [Official device endpoint implementation](https://github.com/SmartThingsCommunity/smartthings-core-sdk/blob/master/src/endpoint/devices.ts): device listing, capabilities and command execution.
- [SmartThings developer documentation](https://developer.smartthings.com/docs/).
- [Samsung token management](https://account.smartthings.com/tokens).

This build does not use the unofficial Samsung `samsung.remote.control` local WebSocket endpoint. Samsung Tizen product APIs and older Smart View casting samples do not establish permission or compatibility for all-model phone remote control.

## LG: published Connect SDK webOS transport

LG Electronics' published Connect SDK source implements webOS SSAP requests and TV-approved pairing. The app independently implements a small remote-control subset of that interface: local registration, saved client key, permitted audio/channel/power commands and the pointer-input socket for navigation.

Only the legacy `ws://<tv-ip>:3000` transport is implemented in this build. LG's current SDK source also implements secure connections on port 3001. A television that requires that secure transport is outside this build's scope. No certificate-verification bypass is used to claim otherwise. The legacy connection is unencrypted and should be used only on a trusted private LAN.

Registration uses a manifest with `manifestVersion: 1` and requested permissions. The user approves the TV prompt; the returned `client-key` is retained locally. The implementation does not copy another application's signed manifest or impersonate its application identity.

Published request examples include:

| Purpose | SSAP URI or pointer message |
| --- | --- |
| Increase/decrease volume | `ssap://audio/volumeUp`, `ssap://audio/volumeDown` |
| Set mute | `ssap://audio/setMute`, with a boolean `mute` payload |
| Change channel | `ssap://tv/channelUp`, `ssap://tv/channelDown` |
| Turn off | `ssap://system/turnOff` |
| Open navigation socket | `ssap://com.webos.service.networkinput/getPointerInputSocket` |
| Navigation button | `type:button\nname:UP\n\n` and the corresponding `DOWN`, `LEFT`, `RIGHT`, `HOME` or `BACK` |
| Select | `type:click\n\n` on the pointer socket |

Sources bearing LG Electronics copyright:

- [Connect SDK Android](https://github.com/ConnectSDK/Connect-SDK-Android).
- [webOS TV service](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/WebOSTVService.java).
- [Socket registration and manifest](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVServiceSocketClient.java).
- [Permissions, media and power controls](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVDeviceService.java).
- [Pointer input messages](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVMouseSocketConnection.java).

SDK publication is evidence of the interface, not evidence that every LG model or firmware accepts it. Pre-webOS NetCast sets, other platforms and secure-only sets are not covered by this transport.

## Panasonic and Hisense

No suitable all-model, manufacturer-published phone remote API was verified for these brands during this build. Network adapters are therefore not presented as working Panasonic or Hisense integrations.

Panasonic VIERA's commonly described `X_SendKey` SOAP interface and Hisense VIDAA's commonly described local MQTT interfaces appear in community integrations. Those integrations alone do not establish the requested manufacturer-published API contract. Hisense televisions also ship with different platforms, including VIDAA, Google TV and Roku; one platform's protocol does not cover the brand.

Roku ECP is excluded from this mobile application. Its [official documentation](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md) and current third-party mobile-use terms must be checked before any future addition; an accessible port is not sufficient evidence of permitted use. The official Roku documentation host was unavailable under this cloud environment's network policy, so this build does not claim a current terms review.

Selected Panasonic, Hisense, PRISM+ and Daewoo TV profiles use infrared. Their numeric functional parameters have exact provenance in the assets. Legacy capture sources are marked `NOASSERTION`; no blanket CC0 claim or original capture-file text is bundled. IR data is distinct from a manufacturer-published network API.

## Stateful and recorded air-conditioner IR

The native encoder uses the complete unmodified source of [IRremoteESP8266 v2.9.0](https://github.com/crankyoldgit/IRremoteESP8266/tree/1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f), pinned to commit `1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`. Its portable sending interface is connected to a hardware-free timing collector, then Android ConsumerIrManager transmits the result. Genuine upstream state conversion, checksums and repeated frames are used.

The Java catalogue exposes 141 documented remote/model profiles across 65 implemented stateful families. Source mappings are in AcProfiles.java with model notes. This community implementation is not a manufacturer certification or a claim of universal model support. York's unfinished common dispatcher is excluded; Teknopoint calls its dedicated upstream encoder to avoid incorrect generic TCL timings.

The separately labelled recorded catalogue has 158 handset profiles / 96 source brand labels / 1,656 signals. Recorded complete frames are replayed, not treated as an arbitrary-temperature state engine. One excessive-duration capture is excluded intact. Import accepts a learned profile for any AC brand.

IRremoteESP8266 and the native adapter are LGPL-2.1-or-later, dynamically linked in libpalmac.so. The corresponding source, copyright notices, license and relink instructions are included in third_party/IRremoteESP8266 and app/src/main/cpp. The APK also includes its LGPL license. Legacy numerical capture facts retain accurate source rights metadata without asserting a missing license.

## Astro/NJOI and infrared

Astro support consists of validated learned-profile import and Android infrared transmission. No verified Malaysian Astro/NJOI decoder profile is bundled. The user must supply a compatible profile captured using appropriate learning hardware or another legitimate source. No private decoder network API, service credentials or conditional-access mechanism is used.

Android's [ConsumerIrManager API](https://developer.android.com/reference/android/hardware/ConsumerIrManager) provides an emitter capability check, supported carrier ranges and transmission of alternating mark/space timings. It cannot add missing IR hardware or learn a remote through an emitter. The app checks hardware and validates imported patterns before transmission. See [IR profile format](IR_PROFILE_FORMAT.md).

## Verification status

Sources above were reviewed on 16 October 2026. The official LG and SmartThings GitHub sources were retrieved successfully. Manufacturer documentation hosts outside the configured network allowlist returned proxy denials; those links are provided for follow-up verification, not represented as successfully fetched documentation.

Cloud software checks cannot verify physical TV/decoder behavior. See [validation evidence](VALIDATION.md) and [supported devices](SUPPORTED_DEVICES.md) before describing a specific model as tested.
