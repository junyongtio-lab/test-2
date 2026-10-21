# Integration sources and boundaries

Version 1.5.0 also bundles [all 734 AC source records](COMPLETE_AC_DATABASE.md) from the exact supplied Mi APK as inspectable inert JSON. This source copy makes no network request and evaluates no vendor hook. Existing transmission controls cover 36 of those source records; the other 698 are explicitly data-only.

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

## Mi Remote infrared data sources

Version 1.4.0 combines three separately attributed raw sources: the unchanged [GitHub Mi v2.1 catalogue](../tools/mi_catalogue/README.md) (3,706 keysets / 140,949 command associations), the Astro public responses described below (five / 216), and the uploaded **Mi Remote 7.2.4M / code 6500** database (604 / 24,204). Together they supply 4,315 raw profiles and 165,369 source button associations. This is not a transfer of Mi Remote's complete online catalogue.

The GitHub archive SHA-256 is `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`; format documentation is pinned to commit `8a60b419506e8c407f2b756ec8ab4b4bc446678d`. Its standalone converter verifies input hashes, decodes supported raw arrays and retains carrier hertz and full microsecond timings. The source's TVKill output uses carrier cycles and is not used as Android timings. Source categories 1, 2, 3, 6, 11 and 12 supply TV, decoder, AC and fan records; decoder categories are combined without inventing brand aliases.

The [uploaded-APK importer](../tools/mi_apk_catalogue/) verifies the exact archive, APK, nested RAR and SQLite hashes before converting named raw TV/AC records. The APK SHA-256 is `4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73`; database SHA-256 is `727a271f25889d97fc94b021b240f62d1ce559ce8a98cf14b7ac0d3efb657bfa`. Its 571 TV and 33 AC profiles retain source table/row/brand associations, keys, waveform-source references and hashes in the [separate index](../app/src/main/assets/ir/mi_apk/index.json). The database file itself, DEX, vendor libraries and model programs are not shipped as executable app dependencies.

Both source-array paths were traced through Mi's raw decoder to `ConsumerIrManager.transmit`; those arrays already contain microseconds. They must not be rescaled through the separate Kookong carrier-cycle encoder path. The Android app independently reads the converted JSON offline and makes no Xiaomi requests.

The [TV ordering helper](../tools/mi_matching/README.md) derives candidate priority from original matching-tree references followed by regional `others` priority, retaining all 2,810 original TV IDs. It does not claim to reconstruct Mi's complete conditional matching tree. Numbered TV candidates on emitter phones enter guided testing; only the user can confirm an appliance response before saving a working match.

GitHub source data and its standalone converter retain AGPL-3.0-or-later notices; the license is included as `licenses/mi_remote_database-LICENSE.txt`. Uploaded Xiaomi/Kookong data is attributed separately without assigning MIT or AGPL rights to it. The application and new independent helpers retain their own code license. See [attribution](../app/src/main/assets/ir/ATTRIBUTION.md) and [data format](IR_PROFILE_FORMAT.md). None of these sources implies manufacturer certification or endorsement.

## Air conditioners and fans

Raw AC/fan profiles replay complete recorded commands, which may change several settings at once. The uploaded-APK raw importer separately lists **701 stateful AC definitions** requiring encoding. These are not 701 failed raw decodes. Proprietary programs, unsupported representations, invalid carriers and empty responses are not executed, guessed or truncated into raw signals.

A separate, independently written Panasonic216 encoder handles six verified parameter profiles. Three (`137_2587`, `kk_3_137_4646`, `kk_3_137_7406`) derive numeric parameters from the uploaded APK; three (`kk_3_137_7790`, `kk_3_137_7791`, `kk_3_137_7793`) derive from the existing GitHub Mi data. Framing, packet templates, byte/bit field mappings and checksums are non-executable data under [tools/panasonic_ac](../tools/panasonic_ac/). No vendor Lua or native encoder is evaluated or copied into runtime.

The encoder supports 16–30 °C, Cool/Heat/Dry, Fan operating mode where mapped, and per-profile fan/swing choices. Auto operating mode is excluded. Five families have explicit on/off fields; `kk_3_137_7793` uses a power-toggle action. Editing requested settings, opening a remote or restoring saved settings sends nothing automatically. Only a settings or power action transmits. The application cannot read or synchronize actual AC state, and other Panasonic families and other vendors' stateful engines remain outside this implementation.

The previous native IRremoteESP8266 engine and non-Mi captures remain removed. The new Panasonic code uses independently implemented protocol facts and Mi numeric data; source verification and golden vectors do not establish physical compatibility with the user's unidentified AC.

## Astro public STB JSON supplement

Static inspection of the user-supplied Mi Remote 7.2.4M APK identified the regional STB route at `https://sg-urc.io.mi.com`. Development-time anonymous GET requests to `/app/public/get_stb_brand`, `/app/public/get_stb_match` and `/app/public/get_stb_keyset` retrieved source JSON for Malaysia and **Astro brand ID 3833**. The source keysets are `kk_2_3833_536`, `kk_2_3833_1819`, `kk_2_3833_350`, `kk_2_3833_211` and `kk_2_3833_2160`.

All **216 recorded command associations** from those five keysets are decoded offline with their exact carrier hertz and microsecond timings preserved. The separate [supplement index](../app/src/main/assets/ir/mi_stb/index.json), [source provenance](../tools/mi_stb/provenance.json) and [generated hash manifest](../tools/mi_stb/generated-manifest.json) record the actual source associations and reproducible outputs. The app bundles the converted JSON and does not call Xiaomi, require a Xiaomi account or ask for a smartphone JSON import. No copied Xiaomi implementation, native library or graphics are shipped.

The public service does not label these keysets Ultra Box or Ulti Box. The user reports that Palm Remote 1.3.0 controls their Ultra Box; the exact working keyset remains unknown. This user report does not validate all five candidates, other Ultra Box units or Ulti Box support. All five source IDs and command data remain unchanged in 1.4.0. The earlier owner-supplied Astro JSON and guessed bit-order interpretations remain deleted.

Public availability of these Xiaomi/Kookong response data does not establish a reuse license. The application's MIT license and the GitHub archive's AGPL notices are not assigned to this separate source. Attribution and hashes are retained; no additional vendor-data rights, manufacturer certification or endorsement are asserted.

## TV/AC online retrieval boundary

The inspected APK uses a separate generic client for TV/AC brand, matching-tree and code retrieval. Ordinary unsigned TV/AC brand requests returned HTTP 200 with service status `19` and no data, including comparisons with public version/country/timestamp metadata. The existing public STB keyset endpoint rejected five actual published TV/AC IDs with code `-6`, `invalid req` and no profile.

The managed proxy, TLS and unrestricted network policy were functioning. These service rejections do not establish usable anonymous TV/AC catalogue access or the exact cause of status `19`. No embedded credentials, opaque signing material or private app storage is reused. See the [TV/AC investigation](TV_AC_COMPATIBILITY_INVESTIGATION.md). The complete online catalogue cannot be claimed as recovered from the uploaded APK alone.

## NJOI, PRISM+ and other brands

No NJOI-labelled, PRISM+ or PrismPlus profile is included. A different receiver brand must not be relabelled Astro without a verified source association.

No suitable all-model, manufacturer-published phone remote API was verified for Panasonic, Hisense, Daewoo or PRISM+ during the original build. Their presence in an IR database does not establish a network API. Hisense televisions also use multiple platforms; one platform's protocol cannot cover the entire brand. Roku ECP is not implemented in this build.

## Android infrared and verification

Android's [ConsumerIrManager API](https://developer.android.com/reference/android/hardware/ConsumerIrManager) exposes emitter detection, supported carrier ranges and alternating mark/space transmission. An emitter does not provide learning capability or add missing hardware. External IR accessories are not supported.

The persistent hardware indicator remains independent of catalogue loading. **IR ready** means an emitter was detected, **No IR transmitter detected** means Android exposes none, and **IR check unavailable** means the query failed. **Check IR** reports the phone/app versions and carrier details without transmitting. These states do not verify that a chosen appliance responds.

The source archive and software decoding can be checked reproducibly; no physical television, decoder, AC or fan was available in the cloud. See [validation evidence](VALIDATION.md), [supported devices](SUPPORTED_DEVICES.md) and [known limitations](KNOWN_LIMITATIONS.md).
