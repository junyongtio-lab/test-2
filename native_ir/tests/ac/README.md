# Stateful air conditioner encoder checks

Run `native_ir/tests/ac/run.sh` from a checkout with G++, Java and Python 3.
The script compiles the same unmodified IRremoteESP8266 source and recording
adapter used by Android. It uses four compiler processes and reuses outputs
in `/tmp/palm-ac-engine-tests` (`PALM_AC_TEST_BUILD` overrides the location).

Checks cover every advertised brand/model profile, all common modes, fan
speeds, swing on/off, and temperatures 16/24/32 C, alongside malformed inputs.
Representative captured waveforms are fed through upstream decoders to check
protocol identity, requested power/mode/temperature and changed temperature.
The Teknopoint regression detects the upstream common dispatcher's wrong
timings. A published Gree recording checks the source checksum implementation.
These are encoding checks, not physical appliance compatibility tests.

`profile_catalog.py --check` verifies profile coverage against the pinned
source, unique IDs and basic metadata. It can also print JSON or TSV.

For Android/JNI validation, build `assembleDebugAndroidTest`, install the app
and test APKs on a device/emulator, and run:

```
adb shell am instrument -w \
  com.universalremote.app.test/com.universalremote.app.ac.AcEngineInstrumentation
```

The custom dependency-free instrumentation runner verifies native library
loading and power-on/off/first-use encoding of every advertised model. It
never transmits infrared, so it also runs on an emulator without an IR emitter.
