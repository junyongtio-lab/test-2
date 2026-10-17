# Bundled Mi Remote JSON format

Version 1.2.0 uses only the GitHub JSON archive published by [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release v2.1. There is **no smartphone JSON-import feature** in this version. Older imports and saved profiles from other IR sources are removed after the new catalogue validates. The earlier user-supplied `astro_ultrabox.json` is no longer bundled.

The offline [catalogue generator](../tools/mi_catalogue/README.md) converts the source's supported signals into Android-ready JSON. It verifies the archive SHA-256 and preserves each accepted signal's integer carrier frequency and complete sequence of microsecond timings. It does not infer protocols, reverse bits, substitute carriers, trim frames, add repeats, or execute proprietary AC programs.

## Index and provenance

`app/src/main/assets/ir/mi/index.json` has `schemaVersion: 2` and contains:

- `source`: GitHub repository, release, pinned format-reference commit, archive URL/hash, units and source limitations.
- `profiles`: metadata for each keyset: stable `mi_…` ID, category, brand, source code-set ID in `model`, command count, asset path/hash and `hardwareTested: false`.
- Per-profile `source`: original model JSON path/hash, brand JSON path/hash, source device ID and source model ID.
- `coverage`: accepted category totals, skipped-model/signal reasons and unavailable brands.

Categories are `tv`, `set_top_box`, `air_conditioner` and `fan`. Categories 2, 11 and 12 in the source are grouped as `set_top_box`; brand names are retained. A source code-set ID is not a verified appliance model number. IDs incorporate the source association so the same model referenced under multiple brands is not silently assigned to only one brand.

## Per-keyset command JSON

Each `ir/mi/profiles/mi_….json` asset contains `schemaVersion: 2`, its original `sourceModelId`, and a `commands` object. The command object's key is the exact source button key. Each value contains:

- `sourceKey`: that same source key, checked against the object key when loaded.
- `label`: a readable display label derived from the source key; the original key remains in `sourceKey`.
- `carrierHz`: the integer carrier frequency in hertz.
- `pattern`: alternating **mark, space, mark, space** durations in integer microseconds, starting with a mark.

Source keys and command associations remain distinct even when waveforms match. No standard-TV button aliases or missing controls are fabricated. The UI lists the actual available commands for every category.

For Android transmission, carriers must be 20,000–60,000 Hz. Patterns must contain 3–2,000 positive integer durations, each at most 1,000,000 microseconds, with total duration at most 1,900,000 microseconds. The phone's reported carrier range must also accept the signal when range information is available. The generator excludes unsupported signals; runtime validation repeats these bounds before transmission.

The source's TVKill export uses carrier-cycle counts. Those values are **not** Android microsecond timings and are not used here. The accepted source model payload decodes directly to microsecond arrays; it is not converted from TVKill or Pronto.

## Loading and regeneration

Android loads the metadata index first and reads an individual keyset's waveform asset on a worker only when needed. A small bounded cache retains loaded keysets. Source IDs, command counts and every loaded signal are validated. An emitter capability check is independent of catalogue loading.

See [offline regeneration instructions](../tools/mi_catalogue/README.md) and [source-manifest.json](../tools/mi_catalogue/source-manifest.json) for input and output hashes. The app makes no Xiaomi API requests and has no online database refresh or profile upload. Raw AC commands remain recorded commands; their presence does not create arbitrary-temperature/state control.

All profiles are physically untested by this project. Neither a successful transmit call nor a matching brand label confirms that an appliance received or understood a command.
