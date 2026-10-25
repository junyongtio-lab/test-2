# Four AC protocol candidates

`FamilyAcEncoder.java` is an independent Java implementation using four exact source templates from the bundled Mi archive. Numeric packet layouts, framing and checksums are checked against an external IRremoteESP8266 reference. The Android app does not link the upstream C++ library, load vendor libraries or execute Mi hooks.

`oracle.cpp` drives the actual upstream setters for all 960 combinations of four families, two modes, 15 temperatures, four fans and two power states. `oracle.csv` contains those expected packets. The Java unit test compares every generated packet to this fixture. `decode.cpp` feeds the Java waveforms through the upstream strict decoders and checks the decoded bytes, including checksums and the Mitsubishi repeated message. Durations retain the pinned source timing rather than substituting upstream timing.

To reproduce, check out IRremoteESP8266 at `1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`. With JDK/Android SDK and g++ available, from the Palm repository root:

```sh
PALM_FAMILY_WAVEFORMS="$PWD/work/family-waveforms.csv" ./gradlew --no-daemon testDebugUnitTest --tests com.universalremote.app.FamilyAcEncoderTest --rerun-tasks
python3 tools/ac_families/verify_reference.py --reference /path/to/IRremoteESP8266 --waveforms work/family-waveforms.csv
```

The reference library and headers retain their LGPL-2.1-or-later license and author notices in that external checkout. The new Palm Java and harness code is MIT. Vendor source data retains its existing separate notices. See [scope and limitations](../../docs/AC_PROTOCOL_CANDIDATES.md). Upstream protocol conformance does not prove identical output to Mi's native engine or appliance response.
