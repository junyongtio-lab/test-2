# Air-conditioner signal capture catalog

This catalog contains **158 community handset profiles, 1,656 signals and 96 source brand labels**. [coverage.csv](coverage.csv) lists every included brand, source model/handset identifier, command count and provenance. [summary.json](summary.json) gives the brand list and exclusions in machine-readable form.

The app replays recorded signals. An AC handset often sends its whole state in one signal: a capture labeled `Temp+`, `Mode` or `Fan` can set several properties together. Those buttons preserve the recorded functional names and do not calculate a new temperature, mode or fan speed. Use the app's separately labeled stateful protocol controls when a matching protocol/model exists.

Model matching matters. These signals have not been tested with physical appliances, and the catalog is not a claim of compatibility with every model from each brand. Some upstream AC-category files describe air coolers, ventilation devices or humidifiers rather than compressor air conditioners; their source model identifiers are preserved. Android phones need a built-in infrared emitter supporting the signal's carrier frequency. iPhones do not have an infrared transmitter.

One source signal is excluded intact: `Botti / Botti BL-168DLR / Power` lasts **5,586,706 µs**, beyond the app's 1.9-second transmission limit. No waveform has been shortened to fit. All included signals satisfy the same 3–2,000 timing, 20–60 kHz carrier and positive integer microsecond bounds used by the app.

## Provenance and rights information

Numeric infrared parameters and functional device/button identifiers were extracted from the `ACs` category in [Lucaslhm/Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB/tree/d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d/ACs), pinned to commit `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d`. Every profile records its exact source URL, source-file SHA-256 hash and commit.

The upstream README explicitly says contributions before commit `2319685f2cbf0cd3f809609622cade14d24fb819` are not covered by its CC0 dedication. Accordingly, source licenses are recorded as **`NOASSERTION`**, not CC0. This bundle contains a generated factual parameter schema; it does not contain original capture-file text, comments, images or source code from that repository. Source URLs provide attribution and allow comparison with the recorded numeric facts. Brand and model names identify devices; they do not imply manufacturer affiliation or approval.

## Rebuild

Place a clean upstream checkout outside this repository, check out the pinned commit, then run:

```sh
python3 native_ir/tools/build_ac_captures.py --source-dir /tmp/flipper-irdb
```

The generator checks the upstream commit and capture-tree cleanliness, uses the app's Java `IrEncoder` for parsed NEC/NECext parameters, and otherwise preserves the complete raw timing sequence. Source files remain in the external checkout. Rebuilding requires a JDK and Python 3, with no additional Python packages.
