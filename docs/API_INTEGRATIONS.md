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

## Mi Remote JSON infrared source

All bundled IR data in version 1.2.0 comes exclusively from the published GitHub JSON archive of [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release [v2.1](https://github.com/ysard/mi_remote_database/releases/tag/v2.1). The archive is pinned by SHA-256 `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`; upstream format documentation is pinned to commit `8a60b419506e8c407f2b756ec8ab4b4bc446678d`.

The archive is committed under `tools/mi_catalogue/source/` for reproducible offline conversion. The standalone generator verifies the archive and input JSON hashes, decodes supported encrypted/compressed raw timing arrays and preserves the exact carrier in hertz and timings in microseconds. It never contacts Xiaomi or executes embedded source definitions. Android receives only the converted JSON assets and uses `ConsumerIrManager` to transmit them. The source's TVKill output uses carrier-cycle counts and is not used as Android timings.

The app includes source categories 1 (TV), 2 (set-top box), 3 (AC), 6 (fan), 11 (cable/satellite) and 12 (internet box). Set-top box categories are combined without inventing brand aliases. Exact source brand-to-keyset associations are retained, including repeated waveform/button associations. Source model identifiers denote code sets, not independently verified retail models. See [coverage and source paths](../app/src/main/assets/ir/mi/index.json) and the [input/output hash manifest](../tools/mi_catalogue/source-manifest.json).

The upstream repository carries AGPL-3.0-or-later notices. Its license is included in the APK at `licenses/mi_remote_database-LICENSE.txt`. The standalone generator also uses AGPL-3.0-or-later and is outside the Android runtime. Source data is not relicensed under the application's MIT license. See [attribution](../app/src/main/assets/ir/ATTRIBUTION.md) and [offline tool instructions](../tools/mi_catalogue/README.md). This is a community database, not a manufacturer-published remote API or manufacturer endorsement.

## Air conditioners, fans and unavailable formats

The previous native IRremoteESP8266 stateful AC engine and all non-Mi captures have been removed. AC and fan remotes now replay only accepted raw commands from the Mi JSON. They cannot generate arbitrary temperature/mode/fan combinations or read appliance state. One command may set several properties simultaneously.

Most upstream AC definitions use proprietary templates, unsupported representations or executable Lua/Java fragments. The generator excludes these rather than executing them or fabricating a waveform. Invalid carrier frequencies and empty model responses are also excluded. Accepted counts and exclusion reasons appear in the index; the upstream README's broad totals are not a claim that every source model is usable in Android.

## Astro, NJOI, PRISM+ and other brands

No Astro, NJOI, PRISM+ or PrismPlus brand was found in the included release categories. The prior repository-owner Astro JSON and its two unverified bit-order interpretations have been deleted. This build therefore has no Astro Ultra Box / Ulti Box profile. A different receiver brand must not be relabelled Astro without a verified source association.

No suitable all-model, manufacturer-published phone remote API was verified for Panasonic, Hisense, Daewoo or PRISM+ during the original build. Their presence in an IR database does not establish a network API. Hisense televisions also use multiple platforms; one platform's protocol cannot cover the entire brand. Roku ECP is not implemented in this build.

## Android infrared and verification

Android's [ConsumerIrManager API](https://developer.android.com/reference/android/hardware/ConsumerIrManager) exposes emitter detection, supported carrier ranges and alternating mark/space transmission. An emitter does not provide learning capability or add missing hardware. External IR accessories are not supported.

The persistent hardware indicator remains independent of catalogue loading. **IR ready** means an emitter was detected, **No IR transmitter detected** means Android exposes none, and **IR check unavailable** means the query failed. **Check IR** reports the phone/app versions and carrier details without transmitting. These states do not verify that a chosen appliance responds.

The source archive and software decoding can be checked reproducibly; no physical television, decoder, AC or fan was available in the cloud. See [validation evidence](VALIDATION.md), [supported devices](SUPPORTED_DEVICES.md) and [known limitations](KNOWN_LIMITATIONS.md).
