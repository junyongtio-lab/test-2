# Supported-device scope, version 1.5.0

All **734 AC source records / 45 brand labels** from the supplied Mi APK are now included as complete inspectable data: 33 recorded and 701 stateful. **36 have implemented controls; 698 are data-only** and cannot transmit through the source-data view. This auxiliary source copy does not add 734 working remotes to the raw coverage table. See [complete AC data](COMPLETE_AC_DATABASE.md).

Palm Remote runs on Android 6.0 or newer using Java and Android's consumer IR service. It has no native AC library or processor-specific encoder and cannot run on iPhone. The user reports Palm Remote 1.3.0 controls an Astro Ultra Box. No physical appliance was attached during cloud validation; source labels and software checks do not certify other appliance matches.

## Bundled raw catalogue

| Category | Distinct brand labels | Raw keysets | Recorded command associations |
| --- | ---: | ---: | ---: |
| Television | 1,021 | 3,381 | 147,768 |
| Set-top box / decoder | 182 | 411 | 12,416 |
| Air conditioner | 40 | 101 | 1,004 |
| Fan | 133 | 422 | 4,181 |
| Combined | 1,291 distinct | 4,315 | 165,369 |

Brand labels overlap between categories. Command totals preserve source button associations, including repeated waveforms. Separate source records remain separate even when their timings match.

| Data source | Raw keysets | Recorded associations | Scope |
| --- | ---: | ---: | --- |
| GitHub Mi v2.1 archive | 3,706 | 140,949 | Existing TV, decoder, AC and fan data retained unchanged |
| Astro regional public responses | 5 | 216 | Existing Astro candidates retained unchanged |
| Uploaded Mi Remote 7.2.4M APK database | 604 | 24,204 | 571 TV keysets / 23,885 associations and 33 AC keysets / 319 associations |

The APK raw importer recovers every attributable raw TV/AC record in the supported database tables. All accepted raw commands pass validation. Another **701 stateful AC definitions** need separate encoders and are listed as omissions by that importer; this is not a claim that the complete Mi online catalogue or every AC format is supported.

Exact source associations and hashes are recorded in the [GitHub index](../app/src/main/assets/ir/mi/index.json), [Astro index](../app/src/main/assets/ir/mi_stb/index.json), [uploaded-APK index](../app/src/main/assets/ir/mi_apk/index.json), [GitHub manifest](../tools/mi_catalogue/source-manifest.json) and [APK import summary](../tools/mi_apk_catalogue/import-summary.json). The actual uploaded source reports version **7.2.4M / code 6500**. The app makes no Xiaomi requests and asks for no phone-side JSON upload.

## Television matching and Daewoo

Use **Television → brand → Find a working remote**. TV candidates from the original archive follow source matching-tree priority and regional `others` priority; all 2,810 original TV IDs remain available. Uploaded-APK TV records are appended without overwriting the original identities. Selecting a numbered candidate on an IR-equipped phone opens the tester before a working match is saved.

Daewoo has 13 raw candidates / 446 source command associations. The source tree begins with `xm_1_3399` and `xm_1_3390`; `kk_1_195_3001` and `kk_1_195_2995` follow the tree candidates. The ordering is a flattened candidate sequence rather than Mi's full conditional matching tree. No named Daewoo TV waveforms were found in the uploaded APK database. The user tried only some of the older candidates and reported failure; the correct match remains unknown.

On an existing recorded remote, **Find a working remote** tests replacement candidates and changes that saved record only after confirmation. Source keyset IDs such as `xm_1_3399` identify code sets, not independently verified retail models. A brand match or accepted transmission alone is insufficient.

## Panasonic AC parameter controls

Six Panasonic216 parameter profiles are available **in addition to the raw catalogue**:

| Source keyset | Parameter source | Modes | Fan-speed choices | Power |
| --- | --- | --- | --- | --- |
| `137_2587` | Uploaded APK database | Cool, Heat, Fan, Dry | Auto, Low, Medium, High | Explicit on/off |
| `kk_3_137_4646` | Uploaded APK database | Cool, Heat, Dry | Auto, Low, Medium, High | Explicit on/off |
| `kk_3_137_7406` | Uploaded APK database | Cool, Heat, Dry | Auto, Low, Medium, High | Explicit on/off |
| `kk_3_137_7790` | Existing GitHub Mi source | Cool, Heat, Dry | Source default retained | Explicit on/off |
| `kk_3_137_7791` | Existing GitHub Mi source | Cool, Heat, Dry | Source default retained | Explicit on/off |
| `kk_3_137_7793` | Existing GitHub Mi source | Cool, Heat, Fan, Dry | Source default retained | Toggle action |

Each supports source-defined temperature values from 16–30 °C and vertical swing mappings. Auto operating mode is excluded; available Auto fan and Auto swing choices are separate settings. Packet fields, frame timings and checksums come from bounded numeric parameters. No model script or vendor native encoder is executed.

Choose **Air conditioner → Panasonic → Find a Panasonic AC remote**, then test power-on or power-toggle at Cool 24 °C. Confirm a response before saving a working candidate. An explicitly labelled untested preview can be saved, while all sending remains disabled without an emitter. Requested setting changes are sent only by an explicit settings or power action. The app cannot read actual AC temperature, mode or power state.

The user reports the older recorded Panasonic selection failed while Mi Remote controlled the AC on the same phone. Exact AC/handset models are unknown. These six families extend coverage but are not a guarantee for that appliance or every Panasonic model. The three earlier raw Panasonic keysets remain available alongside raw records imported from the APK.

## Astro, NJOI and PRISM+

Five Astro keysets remain: `kk_2_3833_536`, `kk_2_3833_1819`, `kk_2_3833_350`, `kk_2_3833_211` and `kk_2_3833_2160`, with all 216 source commands unchanged. The user confirms Palm Remote 1.3.0 works with their Ultra Box, but the exact working keyset was not supplied. Individual metadata therefore keeps `hardwareTested: false`; the report does not validate all five candidates or Ulti Box compatibility.

No NJOI-labelled, PRISM+ or PrismPlus keyset is included. The earlier unverified Astro hexadecimal codes and guessed bit-order interpretation remain removed. No other decoder brand is relabelled Astro.

## Other ACs and fans

Raw AC/fan profiles replay recorded messages, which may change several settings at once. They do not provide arbitrary state generation. Other vendors' stateful AC definitions, other Panasonic protocol families, and appliance-state reading/synchronization remain outside the independent Panasonic216 encoder.

## Retained network integrations

| Family | Route | Requirements and limits |
| --- | --- | --- |
| Samsung TV | Official SmartThings API | Existing Samsung authorization, token and internet; only TV-advertised capabilities are enabled. No Palm account. |
| LG TV | Published webOS SSAP subset | Same trusted private LAN, TV approval and legacy port 3000. Secure-only and pre-webOS sets are outside this transport. |

These integrations do not add non-Mi infrared signals. No all-model Panasonic, Hisense, Daewoo or PRISM+ network API is claimed. See [integration sources](API_INTEGRATIONS.md).

## Phone requirements and upgrades

Direct IR needs a built-in Android consumer IR transmitter, supported carrier and line of sight. **IR ready**, **No IR transmitter detected**, **IR check unavailable** and **Check IR** remain available on every tab. External USB/Bluetooth emitters and Wi-Fi IR hubs are unsupported. iPhone Face ID hardware is not an Android consumer IR emitter.

The update retains current Mi selections, Panasonic parameter selections and Samsung/LG Wi-Fi credentials. Upgrades from before 1.2.0 remove obsolete imports and IR selections after validation. Record a source keyset ID, tested commands and phone/appliance details when reporting a response. See [validation](VALIDATION.md), [known limits](KNOWN_LIMITATIONS.md) and the [TV/AC investigation](TV_AC_COMPATIBILITY_INVESTIGATION.md).
