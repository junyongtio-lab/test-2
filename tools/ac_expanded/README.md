# Expanded experimental AC112 candidates — 1.6.6

An independent Java encoder supports 70 explicitly reviewed, source-hash-pinned
Mi definitions with two canonical timing variants each. It does not implement all
694 formerly unsupported definitions: **624 remain unsupported**.

All 70 implement power, Cool/Heat, whole-degree 16–30 °C, fan-speed and vertical
swing. **56 have reviewed timers and 56 have mapped special functions**. See
[per-definition capabilities](../../downloads/AC-additional-controls.csv).
Special functions are exposed only when a mapped source table or exact reviewed
hook translation exists. Missing mappings remain unavailable. No source program,
Lua engine, hook or vendor native library is executed.

## Source fields and limits

`approved.json` pins complete source assets and templates. `Ac112Controls` reads
bounded bit-patch tables (`1013`, `1015`, `1017`) from those already hash-validated
assets, and rejects unreviewed fields, values and control requests. Fan values
are source Auto/Low/Medium/High codes 0/2/3/5, consistent with the public TCL field.
Mitsubishi's fan labels differ; these are source-table candidate labels, not a
claim of Mitsubishi brand compatibility. Swing options vary per source table.

Three exact source-hook SHA-256 values select independently written, bounded
translations for timer and sleep behavior. These are **not executed scripts**.
Native timers use a ten-minute count, with profile-specific selectable steps:
52 profiles use 10–1,440/10, three use 0–1,440/30 and one uses 60–480/60.
The public TCL timer API instead uses a twenty-minute count and cannot be used
as evidence of equivalence for these source timers. Timer packets use source
header values 0x34 (on) / 0x2c (off), with the reviewed indicator variation.
Cancel clears both timer values and enable bits; a request encodes only one timer.
Timer operations are stripped from persisted requests, preventing ordinary sends
from automatically repeating an earlier countdown request.

Sleep/night fan and some auxiliary flags also differ from public TCL semantics.
Only implemented source options are offered, and source mode restrictions are
enforced. Unknown options and unmapped functions fail before waveform generation.
Other packet fields retain template values, not live AC state. The existing
Panasonic `137_2587` encoder is unchanged.

## Reproduction and evidence

External reference: `crankyoldgit/IRremoteESP8266` commit
`1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`.
`reference-files.json` pins all 81 upstream files used by the checker.

- `oracle.cpp` compares all 4,200 basic power/mode/temperature packets against
  both public encoders and getters. Java checks both timing variants: 8,400 cases.
- `control_vectors.py` independently applies the reviewed source bit tables using
  MSB-indexed bit strings, plus explicit translations of the three timer/sleep
  hooks. It regenerates 14,832 packet fixtures and the per-profile coverage CSV.
  Fan/swing Cartesian combinations, timer boundaries/cancellation, every allowed
  timer value for each dialect, mode limits, special options and interactions
  are included. It never evaluates source script text.
- `controls_reference.cpp` matches 7,120 fan/swing packets to the pinned public
  TCL encoder. It deliberately makes no public timer/auxiliary equivalence claim.
- Java tests compare all control fixtures across both transports (29,664 cases),
  reject unsupported/fractional/out-of-range controls and check saved-state
  isolation and one-shot timer persistence.
- `decode.cpp` strictly decodes all exported Java waveforms (8,400 basic plus
  29,664 additional), checking variant, intended carrier and every payload byte.

```sh
python3 tools/ac_expanded/check_sources.py
python3 tools/ac_expanded/control_vectors.py --check
mkdir -p work
PALM_EXPANDED_WAVEFORMS="$PWD/work/ac112-waveforms.csv" \
PALM_AC112_CONTROLS_WAVEFORMS="$PWD/work/ac112-control-waveforms.csv" \
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
python3 tools/ac_expanded/verify_reference.py \
  --reference /path/to/pinned/IRremoteESP8266 \
  --waveforms work/ac112-waveforms.csv \
  --control-waveforms work/ac112-control-waveforms.csv
```

These checks establish conformance to the stated source packet mappings and
reference transports, **not physical compatibility or exact Mi engine equivalence**.
Guided testing must confirm the actual requested effect for each function.
UI instrumentation checks compile; hardware and emulator UI execution are not
claimed for this release.

Independent implementation, allowlist and harness code use the repository MIT
license. The external reference retains LGPL-2.1-or-later and is not linked into
or shipped in the app. Vendor data retains existing attribution/license status.
