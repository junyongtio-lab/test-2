# Bundled Mi Remote JSON format

Version 1.3.0 bundles the unchanged GitHub JSON catalogue published by [ysard/mi_remote_database](https://github.com/ysard/mi_remote_database), release v2.1, plus a separate public regional STB JSON supplement for Astro. There is **no smartphone JSON-import feature**. Upgrades from before 1.2.0 remove obsolete imports and unavailable saved IR selections after the catalogue validates; current Mi selections are retained. The earlier user-supplied `astro_ultrabox.json` remains removed.

The offline [catalogue generator](../tools/mi_catalogue/README.md) converts the source's supported signals into Android-ready JSON. It verifies the archive SHA-256 and preserves each accepted signal's integer carrier frequency and complete sequence of microsecond timings. It does not infer protocols, reverse bits, substitute carriers, trim frames, add repeats, or execute proprietary AC programs.

## Index and provenance

The unchanged GitHub index at `app/src/main/assets/ir/mi/index.json` has `schemaVersion: 2` and contains:

- `source`: GitHub repository, release, pinned format-reference commit, archive URL/hash, units and source limitations.
- `profiles`: metadata for each keyset: stable `mi_…` ID, category, brand, source code-set ID in `model`, command count, asset path/hash and `hardwareTested: false`.
- Per-profile `source`: original model JSON path/hash, brand JSON path/hash, source device ID and source model ID.
- `coverage`: accepted category totals, skipped-model/signal reasons and unavailable brands.

Categories are `tv`, `set_top_box`, `air_conditioner` and `fan`. Categories 2, 11 and 12 in the source are grouped as `set_top_box`; brand names are retained. A source code-set ID is not a verified appliance model number. IDs incorporate the source association so the same model referenced under multiple brands is not silently assigned to only one brand.

The separate `app/src/main/assets/ir/mi_stb/index.json` also uses schema 2. Its source identifies `https://sg-urc.io.mi.com`, kind `mi-stb-public-api` and country `MY`. It adds five `set_top_box` keysets with brand `Astro`, source brand ID 3833 and 216 source commands. Per-profile provenance records the source keyset ID, original response file/hash, brand response file/hash, source URL and match rank. Stable `mi_…` IDs and asset hashes identify the converted keysets; their assets are under `ir/mi_stb/profiles/`.

The original archive contributes 3,706 keysets and 140,949 command associations; the supplement contributes five and 216, for 3,711 keysets and 141,165 associations in the app. Original archive coverage and absence statements remain scoped to that archive. The Astro source keysets have no Ultra Box or Ulti Box retail-model labels and retain `hardwareTested: false`.

## Per-keyset command JSON

Each `ir/mi/profiles/mi_….json` or `ir/mi_stb/profiles/mi_….json` asset contains `schemaVersion: 2`, its original `sourceModelId`, and a `commands` object. The command object's key is the exact source button key. Each value contains:

- `sourceKey`: that same source key, checked against the object key when loaded.
- `label`: a readable display label derived from the source key; the original key remains in `sourceKey`.
- `carrierHz`: the integer carrier frequency in hertz.
- `pattern`: alternating **mark, space, mark, space** durations in integer microseconds, starting with a mark.

Source keys and command associations remain distinct even when waveforms match. No standard-TV button aliases or missing controls are fabricated. The UI lists the actual available commands for every category.

Where a recorded `key_r` counterpart exists, ordinary button presses alternate between the original and alternate captures. State is kept separately for each source profile and advances after a successful send; missing alternates fall back to the normal capture. Explicitly choosing an `_r` source button replays it exactly without advancing that automatic state. The app never generates an alternate waveform or treats `_r` as an off command.

For Android transmission, carriers must be 20,000–60,000 Hz. Patterns must contain 3–2,000 positive integer durations, each at most 1,000,000 microseconds, with total duration at most 1,900,000 microseconds. A complete valid set of reported carrier ranges must accept the signal. If Android detects an emitter but omits or fails the optional range query, support remains unknown and the app attempts the validated original signal without substituting a carrier. The generators exclude unsupported signals; runtime validation repeats these bounds before transmission.

The source's TVKill export uses carrier-cycle counts. Those values are **not** Android microsecond timings and are not used here. The accepted source model payload decodes directly to microsecond arrays; it is not converted from TVKill or Pronto.

## Loading and regeneration

Android loads the metadata index first and reads an individual keyset's waveform asset on a worker only when needed. A small bounded cache retains loaded keysets. Source IDs, command counts and every loaded signal are validated. An emitter capability check is independent of catalogue loading.

See the GitHub [offline regeneration instructions](../tools/mi_catalogue/README.md) and [source-manifest.json](../tools/mi_catalogue/source-manifest.json), plus the separate [Astro supplement converter](../tools/mi_stb/README.md), [response provenance](../tools/mi_stb/provenance.json) and [generated hash manifest](../tools/mi_stb/generated-manifest.json). Offline regeneration uses the retained source JSON; live retrieval is a development-time step. The app makes no Xiaomi API requests and has no online database refresh or profile upload. Raw AC commands remain recorded commands; their presence does not create arbitrary-temperature/state control.

All profiles are physically untested by this project. Neither a successful transmit call nor a matching brand label confirms that an appliance received or understood a command.
