# Build and validate Palm Remote

The project builds a native Java Android app. It requires JDK 17 or newer,
Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.13. The checked-in
Gradle wrapper downloads the correct Gradle release and verifies its SHA256.
No API keys, account registration, database, or hosted backend are required.

## Build using Android Studio or an existing SDK

Open the repository in Android Studio and let it install SDK Platform 35 and
Build Tools 35.0.0. Alternatively, set `ANDROID_HOME` to an existing SDK and run:

```sh
./gradlew --no-daemon assembleDebug lint test
```

On Windows, use `gradlew.bat` instead. The installable debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk` and signed with a development key.
Preserve the same signing key when distributing future updates; use a private
release signing key for publication. Never commit signing keys or passwords.

On a connected Android device, install with Android Studio or:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.universalremote.app/.MainActivity
```

The app supports Android 6.0 (API 23) and newer. A successful emulator launch checks
Android app startup and navigation. Real TV pairing and infrared transmission
require compatible physical hardware on the user's network.

## Prepared cloud environment

The current cloud machine has a workspace-local toolchain:

```sh
source /workspace/toolchains/env.sh
cd /workspace/test-2
./gradlew --no-daemon assembleDebug lint test
```

The source checkout already belongs to an isolated cloud task; use this checkout
directly. Do not create another Git worktree unless specifically requested.
All build caches and generated Java/Android settings use `/workspace/toolchains`.
The environment script retains the inherited network proxy and configures Java
to trust the cloud environment's installed CA certificates. TLS validation remains
enabled. No secrets are stored in the toolchain or required for the build.

The setup helper `/workspace/toolchains/setup-android.sh` verifies pinned archives
before extraction, installs the SDK through Google's SDK manager, and prints the
installed versions. It expects `/workspace/toolchains/env.sh` alongside it; both
files are reusable cloud setup artifacts rather than app runtime dependencies.

| Tool | Tested version | Artifact verification |
| --- | --- | --- |
| Temurin JDK | 21.0.8+9 | Publisher SHA256 sidecar |
| Gradle | 8.13 | Publisher SHA256 sidecar, pinned in wrapper |
| Android command-line tools | 23.0, build 16111833 | Google's repository XML SHA1 |
| Android SDK | Platform 35 revision 2 | SDK manager verified metadata/download |
| Android Build Tools | 35.0.0 | SDK manager verified metadata/download |
| Android Platform Tools | 37.0.1 | SDK manager verified metadata/download |

Archive sources and checksums used during setup:

* JDK: [Temurin 21.0.8+9 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.8%2B9),
  `OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz` SHA256
  `f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219`.
* Gradle: [gradle-8.13-bin.zip](https://downloads.gradle.org/distributions/gradle-8.13-bin.zip),
  SHA256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
* Android tools: [commandlinetools-linux-16111833_latest.zip](https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip),
  publisher SHA1 `e025545c62a8e64c7559119566a569fb1dec5f60` from
  [Google repository metadata](https://dl.google.com/android/repository/repository2-1.xml).
  The verified archive's additional SHA256 is
  `0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8`.

## Optional cloud emulator

The machine has no `/dev/kvm`; Android can run with slower software CPU emulation.
Optional validation uses Android Emulator 37.2.12 and the default Android 29
x86_64 system image revision 8. Neither is required to build the APK.

```sh
source /workspace/toolchains/env.sh
sdkmanager 'emulator' 'system-images;android-29;default;x86_64'
printf 'no\n' | avdmanager create avd --name PalmRemoteAPI29 \
  --package 'system-images;android-29;default;x86_64' \
  --path "$ANDROID_AVD_HOME/PalmRemoteAPI29.avd" --device pixel
emulator -avd PalmRemoteAPI29 -no-window -no-audio -no-boot-anim \
  -no-snapshot -accel off -gpu swiftshader -memory 1536 -cores 2 \
  -camera-back none -camera-front none -port 5554
```

Platform Tools 37.0.1 tries to create `$HOME/.android` even when
`ANDROID_USER_HOME` is set. The managed cloud home is read-only. Therefore the
cloud smoke validation uses a workspace-local Python `adb-shell` client connected
directly to the emulator's `127.0.0.1:5555` ADB endpoint. Normal Android Studio and
device development on a writable user home can use the regular `adb` commands.
