# Learned infrared profiles

Version 1.1.0 also accepts any AC brand when category is `air_conditioner`. Only import IR captures you are entitled to use. This app cannot learn signals through an IR emitter: learning requires separate receiver hardware or a trusted profile supplied by the device owner. An Android phone with `ConsumerIrManager.hasIrEmitter()` is needed to transmit. External IR hubs are not implemented.

Create a UTF-8 JSON file containing an object with `profiles`, an array of profile objects. Each profile has:

- `brand`: `Samsung`, `LG`, `Panasonic`, `Hisense`, `PRISM+`, `Daewoo` or `Astro` for TV profiles; any manufacturer name (1–64 characters) for AC profiles.
- `category`: `tv` (default for old imports) or `air_conditioner`.
- `model`: the exact handset/decoder model or a clear user label.
- `commands`: an object mapping a command ID to `{ "carrierHz": integer, "pattern": [integer microsecond timings] }`.

Patterns alternate **mark, space, mark, space**, beginning with mark. Use the capture's actual carrier frequency and full frame timing. Do not substitute arbitrary example values. Frequencies from 20–60 kHz are accepted only if the phone supports them. Each pattern must have 3–2,000 positive integer durations and a total duration no greater than 1.9 seconds. Each duration is limited to one second. Imports are capped at 512 KB, 32 profiles per import, 64 imported profiles in total and 128 commands per profile. Validation occurs before profiles are saved or transmitted.

Recognized UI command IDs: `power`, `input`, `up`, `down`, `left`, `right`, `ok`, `back`, `home`, `menu`, `volume_up`, `volume_down`, `mute`, `channel_up`, `channel_down`, `info`, `guide`, `play_pause`, `rewind`, `fast_forward`, `record`, `red`, `green`, `yellow`, `blue`, and `digit_0` through `digit_9`. Record and colour controls are shown on Astro remotes. Unsupported/missing commands remain disabled.

Choose **Devices → Import learned IR profile**, open the JSON file, then **Add → Astro → Infrared** (or the relevant TV brand; choose Air conditioner for AC profiles) to save it as a remote. Imported profiles are marked as user-supplied and physically untested. A transmitted frame does not establish that a decoder received it or performed the desired action.

The supplied [astro_ultrabox.json](../app/src/main/assets/astro_ultrabox.json) is already bundled in version 1.1.1; choose its Ultra Box / Ulti Box entry under **+ → Television / Astro decoder → Astro → Infrared** without importing a file. Its original hexadecimal-code schema is separate from the learned-waveform import format above. The app adapts it to two explicitly labelled NEC bit-order interpretations because the source omits bit order. The file has duplicate Power/Blue and Mute/Right codes and has not been tested on a physical decoder. Before calling a model compatible, test power, navigation, channel changes and numeric entry on the actual decoder and record its model/firmware. Corrected learned profiles can still be imported using the format above.

Imported AC profiles are replayed as recorded commands. Use meaningful functional labels in each command's optional `label` field. They do not become arbitrary-temperature encoders merely by adding temperature names; a complete learned waveform for each desired state is needed. Unknown AC brands appear in the AC manufacturer picker after import. The source/hardware metadata is replaced with a user-supplied, physically untested designation rather than trusting an imported certification claim.
