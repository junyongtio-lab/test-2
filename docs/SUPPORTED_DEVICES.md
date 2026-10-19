# Supported-device scope, version 1.3.0

Palm Remote is an Android APK for Android 6.0 or newer. The app uses Java and Android's consumer IR service; it has no native AC library or processor-specific encoder. It cannot run on iPhone. No physical appliance was attached during cloud validation, so source labels and successful software tests do not certify appliance compatibility.

## Mi Remote infrared catalogue

The unchanged published JSON archive of [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release **v2.1**, supplies 3,706 keysets and 140,949 command associations. Version 1.3.0 adds five Astro keysets with 216 recorded commands from Xiaomi's public regional STB JSON service. Both sources are converted offline and bundled. The earlier TV captures, user-supplied Astro file, learned-profile imports and generated full-state AC protocols remain removed.

| Category in Palm Remote | Distinct brand labels | Keysets | Command associations |
| --- | ---: | ---: | ---: |
| Television | 1,021 | 2,810 | 123,883 |
| Set-top box / decoder | 182 | 411 | 12,416 |
| Air conditioner | 38 | 68 | 685 |
| Fan | 133 | 422 | 4,181 |

There are **3,711 keysets**, **141,165 source command associations** and **1,290 distinct brand labels across categories**. Brand labels overlap between categories and are not added together. Repeated waveforms under different source buttons remain separate associations. The TV source contains duplicate brand labels in different brand files; the picker combines their labels without discarding the associated keysets.

Set-top box coverage combines source categories 2 (set-top box), 11 (cable/satellite) and 12 (internet box). Television, AC and fan use categories 1, 3 and 6 respectively. Other upstream categories, such as DVD and projectors, are outside this build.

The [GitHub catalogue index](../app/src/main/assets/ir/mi/index.json) records its accepted profiles, source paths and hashes, skipped-signal reasons and unavailable brands. The separate [Astro supplement index](../app/src/main/assets/ir/mi_stb/index.json) records the additional keysets. The GitHub generator's [source manifest](../tools/mi_catalogue/source-manifest.json) records its input and generated output hashes; [tools/mi_stb](../tools/mi_stb/) records the public Astro responses and conversion provenance. The original GitHub index still reports Astro absent from that archive. Upstream README totals describe the source archive and are not the combined Android catalogue totals.

Choose **+ → category → brand → Mi Remote keyset**. Source identifiers such as `xm_1_4947` identify code sets; they are not promises of support for a retail model with that name. A brand match alone does not establish compatibility. Only the source's actual decoded buttons are shown.

## Astro candidates, NJOI and PRISM+

The public regional STB service identifies **Astro, brand ID 3833**, and supplies five source keysets: `kk_2_3833_536`, `kk_2_3833_1819`, `kk_2_3833_350`, `kk_2_3833_211` and `kk_2_3833_2160`. All 216 decoded source command associations are preserved. Choose **Decoder → Astro → Guided testing**, send a recorded button and confirm the appliance responds before saving a candidate.

The service does not assign Ultra Box or Ulti Box retail-model labels to these keysets. The user reports that Mi Remote 7.2.4M controls their Ultra Box, but the working Mi selection has not been identified and Palm Remote's physical decoder response remains untested. These are Astro candidates, with no guarantee for either model. No NJOI-labelled, PRISM+ or PrismPlus profile is included. The previous untested Astro hexadecimal codes and guessed bit-order interpretation remain removed.

## Air conditioners and fans

These remotes replay specific raw commands decoded from the Mi JSON. AC commands can set several properties together; a label such as temperature, mode or fan does not create an arbitrary-state encoder. The app cannot query appliance state or synchronize it with other remotes.

Most upstream AC definitions are not raw timing arrays. They contain proprietary byte templates, unsupported formats or executable fragments. The generator does not execute those definitions or invent conversions. Only 68 AC keysets have supported decoded commands in this build; this is not coverage of all 280 source AC brand files or every model sold.

## Retained network integrations

| Family | Route | Requirements and limits |
| --- | --- | --- |
| Samsung TV | Official SmartThings API | Existing Samsung/SmartThings account, authorized token and internet. Only capabilities returned for the selected TV are enabled. No Palm account is required. |
| LG TV | Published webOS SSAP subset | Same trusted private LAN, TV approval and legacy port 3000. Secure-only webOS and pre-webOS sets are outside this transport. |

These network integrations do not contribute non-Mi signals to the infrared catalogue. No new Panasonic, Hisense, Daewoo or other brand-wide network API is claimed. See [integration sources](API_INTEGRATIONS.md).

## Phone requirements and upgrades

Every direct IR path needs a built-in Android consumer IR transmitter, a supported carrier and line of sight. The app retains **IR ready**, **No IR transmitter detected**, **IR check unavailable** and the **Check IR** diagnostic. Most smartphones lack a consumer IR emitter; iPhone's Face ID hardware does not provide Android remote-control transmission. External USB, Bluetooth and Wi-Fi IR hubs are not supported.

Upgrades from before 1.2.0 remove obsolete IR remotes and imported profiles after the replacement index validates. Version 1.3.0 keeps current Mi catalogue selections; add an Astro candidate from the decoder category. Samsung/LG Wi-Fi devices and encrypted credentials are retained. No smartphone JSON upload is required or offered, and the app does not query Xiaomi's catalogue.

When reporting physical compatibility, record the appliance model, region, firmware, original handset, phone/Android version, source keyset ID and tested commands. See [validation](VALIDATION.md) and [known limits](KNOWN_LIMITATIONS.md).
