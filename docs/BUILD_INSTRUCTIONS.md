# Build and validate Palm Remote

The app uses Java and Android's consumer IR service. The tested build toolchain is JDK 21, Android SDK Platform 35, Build Tools 35.0.0 and Gradle 8.13. The checked-in Gradle wrapper downloads the pinned Gradle release and verifies its SHA-256. Version 1.4.0 includes an independently written Java Panasonic AC encoder; NDK and CMake are unnecessary. No API keys, app registration or hosted backend are needed to build it.

## Build and install

Open the repository in Android Studio and install SDK Platform 35 and Build Tools 35.0.0. Alternatively, set `ANDROID_HOME` to an existing SDK and run from the repository root:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

On Windows, use `gradlew.bat`. The installable development-signed APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Preserve the same signing key for compatible updates; use a private release key for store publication. Never commit signing keys or passwords.

Install on a connected Android device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.universalremote.app/.MainActivity
```

The app requires Android 6.0 (API 23) or newer. Emulator checks validate startup, catalogue access, migration and screen behavior; real appliance response requires physical hardware. A built-in Android consumer IR emitter is required for transmission.

## Verify or regenerate the Mi-only catalogue

The source archive is committed as `tools/mi_catalogue/source/database_dump-v2.1.tar.xz`. Its 7,892,348 bytes must have SHA-256 `44dcf13d815383c907884f7cdb294afcd085b7063c1de4780589e0626cb61895`. The generator performs this check before extracting JSON, runs offline and never executes embedded AC programs.

```sh
python3 -m venv /tmp/mi-catalogue-venv
/tmp/mi-catalogue-venv/bin/pip install -r tools/mi_catalogue/requirements.txt
/tmp/mi-catalogue-venv/bin/python tools/mi_catalogue/generate.py --check
/tmp/mi-catalogue-venv/bin/python -m unittest discover -s tools/mi_catalogue -p 'test_*.py'
```

`--check` regenerates the catalogue in temporary storage and compares the complete assets and manifest byte for byte without modifying the checkout. To regenerate checked-in outputs deliberately, run the generator without `--check`. PyCryptodome 3.23.0 is the pinned offline-tool dependency; it is not part of the Android app. See [tool instructions](../tools/mi_catalogue/README.md) for explicit archive/database path options, source licensing and decoding boundaries.

The Android APK build consumes the checked-in JSON assets and does not need Python or contact Xiaomi. Keep the per-input/per-output hashes in `tools/mi_catalogue/source-manifest.json` synchronized when regenerating data.

## Verify the uploaded-APK data and Panasonic parameters

The additional raw TV/AC records come from the exact user-uploaded **Mi Remote 7.2.4M/code 6500** archive under `downloads`. No separate 7.4.2M archive is present. The importer validates the 7z, APK, nested RAR and SQLite hashes before converting 604 raw profiles and 24,204 command associations. It does not execute the APK, native libraries or embedded AC programs. System libarchive with RAR4 support is required; Ubuntu provides it through `libarchive13`, `libarchive13t64` or `libarchive-dev`.

```sh
/tmp/mi-catalogue-venv/bin/pip install -r tools/mi_apk_catalogue/requirements.txt
/tmp/mi-catalogue-venv/bin/python tools/mi_apk_catalogue/generate_apk.py --check
/tmp/mi-catalogue-venv/bin/python -m unittest discover -s tools/mi_apk_catalogue -p 'test_*.py'
/tmp/mi-catalogue-venv/bin/python tools/mi_matching/generate_order.py --check
/tmp/mi-catalogue-venv/bin/python -m unittest discover -s tools/mi_matching -p 'test_*.py'
/tmp/mi-catalogue-venv/bin/python tools/panasonic_ac/generate.py --check
/tmp/mi-catalogue-venv/bin/python -m unittest discover -s tools/panasonic_ac -p 'test_*.py'
/tmp/mi-catalogue-venv/bin/python tools/panasonic_ac/verify_sources.py
```

The importer defaults to bounded temporary extraction of the committed archive; `--apk` accepts an already extracted exact APK. Matching order preserves all 2,810 original TV profile associations while restoring source-tree priority. The Panasonic generator uses pinned, non-executable parameters for six Panasonic216 profiles, including three from the supplied APK and three from the existing GitHub source. It does not implement every proprietary AC family. See [APK import](../tools/mi_apk_catalogue/README.md), [matching order](../tools/mi_matching/README.md) and [Panasonic encoding](../tools/panasonic_ac/README.md).

## Android catalogue checks

The instrumentation runner checks bundled catalogue loading without sending IR:

```sh
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.universalremote.app.test/com.universalremote.app.CatalogInstrumentation
```

The optional UI suite is restricted to an emulator that reports no IR emitter. It temporarily uses source-backed fixture selections, restores the exact original device metadata afterward, and never sends IR:

```sh
adb shell am instrument -w -r -e palm.ui true com.universalremote.app.test/com.universalremote.app.CatalogInstrumentation
```

Its JSON report and screenshots are written to the app-specific external directory `Android/data/com.universalremote.app/files/palm-validation-v140`. Runtime catalogue/encoder checks and UI assertion counts are reported separately. The test does not certify physical TV/AC/decoder response.

The persistent **IR ready**, **No IR transmitter detected** and **IR check unavailable** indicators remain available before adding devices. **Check IR** reports the installed version, phone model and carrier support. A hardware-free emulator normally reports no transmitter.

An update keeps the application ID and signing identity. Existing current Mi selections, including Astro, remain. Obsolete pre-1.2 IR selections are removed only after the replacement catalogue validates. Samsung SmartThings/LG Wi-Fi metadata and encrypted credentials remain. No phone import is needed.

## Prepared cloud environment

The managed cloud machine has a workspace-local toolchain:

```sh
source /workspace/toolchains/env.sh
cd /workspace/test-2
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Use this checkout directly. Tool caches and generated Android/Java settings use `/workspace/toolchains`. The environment script retains the session proxy and Java trust in the installed cloud CA certificates; TLS validation remains enabled. Existing NDK/CMake installations may remain in the cache but are not used by this version.

| Tool | Tested version | Verification |
| --- | --- | --- |
| Temurin JDK | 21.0.8+9 | Publisher SHA-256 sidecar |
| Gradle | 8.13 | SHA-256 pinned in wrapper |
| Android command-line tools | 23.0, build 16111833 | Google repository metadata |
| Android SDK | Platform 35 revision 2 | SDK manager metadata/download verification |
| Android Build Tools | 35.0.0 | SDK manager metadata/download verification |
| Android Platform Tools | 37.0.1 | SDK manager metadata/download verification |

The reusable `/workspace/toolchains/setup-android.sh` and `env.sh` files belong to cloud setup; they are not app runtime dependencies. No credential values are needed in the repository.

## Optional cloud emulator

The current machine has no `/dev/kvm`; software CPU emulation is slower. Android Emulator 37.2.12 with the default Android 29 x86_64 system image revision 8 is used for optional device checks. Neither is needed to build the APK.

```sh
source /workspace/toolchains/env.sh
sdkmanager 'emulator' 'system-images;android-29;default;x86_64'
emulator -avd PalmRemoteAPI29 -no-window -no-audio -no-boot-anim \
  -no-snapshot -accel off -gpu swiftshader -memory 2048 -cores 2 \
  -camera-back none -camera-front none -port 5554
```

Create the `PalmRemoteAPI29` AVD first with Android Studio or `avdmanager` if it does not exist. Platform Tools 37.0.1 may try to write `$HOME/.android` despite `ANDROID_USER_HOME`. In a managed cloud home that is read-only, smoke checks use a workspace-local Python `adb-shell` client connected to the emulator's `127.0.0.1:5555`. Normal development on a writable home can use standard `adb` commands.

## Mi Remote APK research

The original static-analysis tools are in [tools/apk_inspection](../tools/apk_inspection/README.md). The supplied exact APK was inspected, including a nested RAR/SQLite catalogue; see [findings and online Astro retrieval](MI_REMOTE_724M_INVESTIGATION.md) and the [TV/AC investigation](TV_AC_COMPATIBILITY_INVESTIGATION.md). It is a verified input for offline database regeneration, while an ordinary Android build uses the committed converted JSON. Palm Remote 1.4.0 retains the GitHub and Astro data, adds converted TV/AC records and Panasonic parameter encoding, and uses original interface code/artwork.

Verify the separate offline Astro supplement before building:

```sh
/workspace/toolchains/mi-catalogue-venv/bin/python tools/mi_stb/generate_stb.py --check
/workspace/toolchains/mi-catalogue-venv/bin/python -m unittest discover -s tools/mi_stb -p 'test_*.py'
```

The converter checks saved public-response hashes and preserves decoded microsecond timings. No live service access or Xiaomi APK is needed to rebuild these assets. See [source records and conversion](../tools/mi_stb/README.md).
