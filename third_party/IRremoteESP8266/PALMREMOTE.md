# IRremoteESP8266 source dependency

Source: https://github.com/crankyoldgit/IRremoteESP8266
Version: 2.9.0
Pinned commit: `1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`
License: LGPL 2.1; the complete upstream license is in `LICENSE.txt`.

The complete `src` directory is vendored without changes. Original copyright
and license notices are preserved. PalmRemote compiles the library's portable
`UNIT_TEST` interface with `app/src/main/cpp/capture/IRsend_test.h`, a local
hardware-free adapter that records carrier and mark/space calls. All protocol
state, model conversions, repeated frames and checksums run in upstream code.
No GPIO access, software PWM loop, network connection, or ESP hardware is used.

The Android native library is dynamically linked as `libpalmac.so`. Source for
the adapter, Java bridge and build scripts is published in this repository so
the library can be rebuilt and replaced. To rebuild, install the repository's
pinned Android SDK/NDK/CMake and run `./gradlew assembleDebug`. Do not omit this
source directory or license when redistributing corresponding APK builds.

Supported stateful protocols are those accepted by upstream
`IRac::isProtocolSupported`, except `YORK`: at this pinned commit its advertised
dispatcher case and power setter remain unfinished, so the adapter rejects it.
For `TEKNOPOINT` the adapter configures the upstream `IRTcl112Ac` state and
calls upstream `sendTeknopoint` directly; its common dispatcher incorrectly
calls the TCL timing encoder. This correction is covered by decode tests.
Brand or protocol support does not establish compatibility with every model.
Common controls unsupported by a particular appliance are ignored or clamped
by its upstream protocol implementation. The app's previous state is an
estimate; toggles can become unsynchronized after another remote is used.
