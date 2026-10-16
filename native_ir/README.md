# Infrared validation

The Android application reads `app/src/main/assets/ir/profiles.json`, which
contains precomputed waveforms. `IrCatalog` validates JSON commands before
transmission and applies a 1.9-second limit. The standalone `IrEncoder` library
also provides raw waveform validation and a `pronto(hex)` converter for learned
Pronto 0000 data; the app UI imports waveform JSON, not Pronto text directly.
The converter sends the introductory burst for one tap; repeat-only codes send
one repeat burst. It rejects nonpositive timings, unsupported carriers and
commands longer than Android's two-second transmit limit.

The encoder supports the exact Flipper parsed formats used by the bundled
profiles: NEC, NECext, Samsung32 and Kaseikyo. It interprets hexadecimal byte
fields as little endian. RC5/RC6 have no bundled parsed codes and are not claimed
as supported parsed formats. Learned raw recordings of those protocols can be
imported normally.

Run the standalone known-frame and malformed-import tests with a JDK:

```sh
mkdir -p /tmp/palm-ir-tests
javac -d /tmp/palm-ir-tests app/src/main/java/com/universalremote/app/ir/IrEncoder.java native_ir/tests/IrEncoderTest.java
java -cp /tmp/palm-ir-tests IrEncoderTest
python3 native_ir/tools/build_catalog.py
```

These checks validate bytes, timing conversion and input bounds. They do not
establish physical compatibility or IR hardware performance. Provenance and CC0
license are bundled alongside the catalog.
