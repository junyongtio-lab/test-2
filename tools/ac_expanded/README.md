# Expanded experimental 112-bit AC candidates

This independent Java encoder enables **70 previously unsupported source
definitions**, with two canonical protocol timing variants per definition.
It does **not** implement all 694 formerly unsupported definitions: **624 remain**.

Implemented controls: explicit power, Cool/Heat and whole-degree 16–30 °C.
Fan, swing, timers, light, sleep and other auxiliary controls are not exposed.
Unexposed packet fields retain source-template values, not live appliance state.

`approved.json` is an explicitly reviewed list, not an automatic prefix matcher.
It pins complete source-asset SHA-256 hashes and template bytes. These definitions
passed normal-frame, checksum-layout, basic mode-table and inactive-timer checks.
Definitions with unsupported flags, tables or power hooks remain excluded. The
runtime rejects unlisted sources, changed definitions and unimplemented controls.
No stored Mi script or hook is executed.

The packet fields exposed by this subset agree between the public Mitsubishi112
and TCL112 implementations. The two public protocols differ in timing and some
unexposed features. Each definition therefore offers two explicitly named timing
variants for user testing. These use the published canonical carrier/timing,
not a claim of byte-for-byte reproduction of Mi's native signal engine.

## Evidence and reproduction

Reference: `crankyoldgit/IRremoteESP8266` commit
`1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`.
`reference-files.json` pins the exact upstream files used by the checker.

`oracle.cpp` initializes both upstream encoders from each approved template and
tests every supported power/mode/temperature combination. It checks matching
packets and the upstream getters. `oracle.csv` contains 4,200 expected packets.
The Java test compares both timing-variant candidates against those packets.
`decode.cpp` strictly decodes all 8,400 exported Java waveforms, checking protocol
type, carrier intent and every payload byte. Corrupted or truncated evidence fails.

```sh
python3 tools/ac_expanded/check_sources.py
mkdir -p work
PALM_EXPANDED_WAVEFORMS="$PWD/work/ac112-waveforms.csv" ./gradlew --no-daemon testDebugUnitTest
python3 tools/ac_expanded/verify_reference.py --reference /path/to/pinned/IRremoteESP8266 --waveforms work/ac112-waveforms.csv
```

Passing these checks proves conformance to the referenced basic packet/transport
rules, **not physical AC compatibility**, full support for a brand, or exact Mi
engine equivalence. Guided testing must confirm actual appliance responses.
The existing Panasonic `137_2587` encoder remains unchanged.

Independent Java, allowlist checker and harness code use the repository MIT
license. The external reference library retains its LGPL-2.1-or-later license and
author notices and is not shipped or linked into the app. Vendor source data
retains the existing attribution and license status.
