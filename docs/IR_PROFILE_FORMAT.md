# Bundled Mi Remote IR formats

Version 1.4.0 bundles three independently attributed raw catalogues, TV candidate-order metadata and six Panasonic216 parameter profiles. There is **no smartphone JSON-import feature** or Xiaomi refresh request. Current saved Mi selections remain valid; upgrades from before 1.2.0 remove obsolete imports and unavailable IR selections after catalogue validation. The earlier user-supplied `astro_ultrabox.json` remains removed.

## Raw indexes and provenance

All raw indexes use `schemaVersion: 2`. Each profile has a stable `mi_…` ID, category, source code-set ID in `model`, source brand, command count, asset path/SHA-256 and `hardwareTested: false`. The source ID is not a verified retail appliance model number. A profile ID incorporates its source association so different records are not silently merged.

| Index and asset directory | Source | Profiles | Recorded associations |
| --- | --- | ---: | ---: |
| `ir/mi/index.json`, `ir/mi/profiles/` | Unchanged pinned GitHub Mi v2.1 archive | 3,706 | 140,949 |
| `ir/mi_stb/index.json`, `ir/mi_stb/profiles/` | Pinned Astro regional public responses | 5 | 216 |
| `ir/mi_apk/index.json`, `ir/mi_apk/profiles/` | Uploaded Mi Remote 7.2.4M database | 604 | 24,204 |

The GitHub index records archive/repository provenance and per-profile model/brand JSON paths and hashes. Categories 2, 11 and 12 are combined as `set_top_box`; TV, AC and fan use `tv`, `air_conditioner` and `fan`.

The Astro index identifies `https://sg-urc.io.mi.com`, kind `mi-stb-public-api`, country `MY` and source brand ID 3833. Its profile source contains the actual keyset ID, original response path/hash, brand response path/hash, source URL and match rank. Ultra Box/Ulti Box retail labels were not returned. The user's successful Palm Ultra Box report does not identify which candidate to mark hardware-tested.

The APK index identifies kind `mi-apk-bundled-database`, version `7.2.4M` / code `6500`, exact archive/APK/database SHA-256 values, and Hz/microsecond units. Each profile records source table, logical `irlocaldata.db/{table}/{rowID}` path, model/brand identities, brand-row hash, canonical source-row hash and optional referenced waveform-source row. Only named raw TV/AC associations from the verified database are accepted. Another 701 stateful AC definitions are recorded separately as requiring an encoder; they are not executed by the raw importer.

The three indexes combine to **4,315 raw profiles / 165,369 recorded command associations**. The original GitHub files remain unchanged, including absence statements scoped to that archive. New data does not replace another brand's label with Daewoo, Panasonic or Astro.

## Raw command assets

Each raw profile asset contains `schemaVersion: 2`, its original `sourceModelId`, and a `commands` object. The command object's key is the exact source button key. Each value contains:

- `sourceKey`: the same original key, checked against the object key on load.
- `label`: a readable label derived from that key.
- `carrierHz`: integer carrier frequency in hertz.
- `pattern`: alternating **mark, space, mark, space** integer microsecond durations, starting with a mark.

Source keys remain distinct when waveforms are identical. Known spellings can select grouped UI controls without creating a new signal or removing the remaining source buttons.

Where a real `key_r` counterpart exists, successful ordinary presses alternate normal/alternate captures per source profile; missing alternates fall back to normal. Explicit `_r` selection replays that source capture exactly without advancing automatic state. Failed sends do not advance state. `_r` is never inferred as an off command.

Signals require 20,000–60,000 Hz, 3–2,000 positive durations, each at most 1,000,000 µs, total at most 1,900,000 µs. Complete valid carrier ranges can reject a nonmatching frequency. If Android confirms an emitter but omits/fails its optional range query, the original validated carrier is attempted without substitution. Source arrays already contain microseconds; neither TVKill carrier-cycle counts nor Pronto values are accepted as raw Android durations.

## TV candidate ordering

`ir/matching_order.json` uses schema 1. Its source records the pinned GitHub archive hash, unchanged base-index hash and matching country `MY`. Groups record brand/category, exact source brand path/hash, original source country and ordered `{id, modelId}` references. All 2,810 original TV profile IDs appear exactly once across 1,023 source brand groups.

Tree references retain first-occurrence depth-first priority, followed by regional/declarative `others` priority. Accepted records not otherwise referenced remain available. The loader validates IDs and their brand/source/model associations before applying the order. Uploaded-APK TV profiles are appended stably; Astro and other categories retain their source order. This flattened candidate order is not a claim to reproduce Mi's conditional matching tree or automatically identify an appliance.

## Panasonic216 parameters and requested state

`ir/panasonic_ac/index.json` uses schema 1, kind `panasonic216-parameters`, and the pinned non-executable parameter-source SHA-256. Six source identities use `pana_{modelId}` IDs. Their metadata specifies template bytes, carrier, frame timings, mode/temperature byte patches, supported fan patches, swing bit patches, power semantics and source hashes. No Lua, Java definition or vendor native binary is stored as an executable model dependency.

Three parameter sets derive from the actual uploaded APK database; three derive from the existing GitHub Mi archive. The independent encoder copies a 27-byte Panasonic216 template, applies only supported state mappings, sets the model's power field/action, recomputes both section checksums and produces complete LSB-first 8-byte/19-byte frames with the source timings. It validates the result through the same raw transmission bounds.

Requested states contain `power`, `mode`, `temperature`, `fan` and `swing`. Temperature is limited to 16–30 °C; supported mode/fan/swing choices vary by source profile. Auto operating mode is excluded. A value of `-1` preserves a source-default fan/swing field when that control has no mapping. `kk_3_137_7793` uses a power-toggle action rather than an explicit on/off state; ordinary settings messages do not add a toggle action.

Saved devices use mode `panasonic_ac`, a parameter profile ID and locally stored requested state. No device reading or automatic transmission is implied by restoring or editing that state. A working-match save requires an accepted test and user confirmation; an untested preview is explicitly labelled. These six profiles are additional to the raw count, with no finite recorded-command association total assigned to generated settings.

## Loading and reproduction

The app loads metadata on a worker, then lazily validates raw asset hashes, source identities, command counts and timings. An eight-profile cache bounds loaded raw waveforms. Hardware detection remains independent of catalogue parsing. Source failure does not authorize clearing Wi-Fi metadata or encrypted credentials.

Offline tools cover the [GitHub catalogue](../tools/mi_catalogue/README.md), [Astro responses](../tools/mi_stb/README.md), [uploaded APK](../tools/mi_apk_catalogue/), [TV ordering](../tools/mi_matching/README.md) and [Panasonic parameters](../tools/panasonic_ac/). All can be reproduced from retained pinned inputs without a phone-side upload or Xiaomi runtime request. See [build instructions](BUILD_INSTRUCTIONS.md) and [validation](VALIDATION.md).

Passing source/encoder/software checks and an accepted Android transmit do not establish a physical appliance response. The user reports one Palm Astro Ultra Box match; the new TV/AC controls still need appliance testing.
