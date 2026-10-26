# Palm Remote crowdfunding demo

This generates a separate **Palm Remote Demo** Android app from the published
Palm Remote 1.6.6 sources. Its output APK is named **palmremote demo.apk**.

| Category | Included brands | Sendable candidates |
| --- | --- | ---: |
| TV | Samsung | 69 recorded |
| Air conditioner | Panasonic, Midea | 4 recorded + 10 settings |
| Fan | KDK, Panasonic | 6 recorded |
| Decoder | Astro | 5 recorded |

No other brand/category profile assets are copied into the generated project or
APK. Per-category brand selection and loader guards enforce the same allowlist.
The AC source browser contains only the 46 Panasonic/Midea source records, with
accurate limited-catalogue counts. Unsupported definitions within those two brands
remain data only, not sendable controls. The two retained AC112 definitions use
neutral timing A/B labels; the waveforms and packet controls are unchanged.

The demo retains guided testing, explicit response confirmation, saved previews,
IR hardware gates, Panasonic controls and the mapped AC112 additional functions.
It requires a supported built-in Android IR emitter for real transmissions. It
cannot establish physical compatibility through catalogue or automated tests.

The demo installs independently as `com.universalremote.app.demo`, label
**Palm Remote Demo**, version **1.6.6-demo** (code 15). It uses offline infrared
controls; network setup options and Internet/Wi-Fi permissions are excluded.
It has separate local storage and does not replace the full app. No pledge or
payment flow is added. The full app's source, assets and APK remain unchanged.

## Build

Run from the repository version containing the demo generator and 1.6.6 app:

```sh
python3 tools/demo/create_demo.py --output /tmp/palmremote-demo
cd /tmp/palmremote-demo
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk 'palmremote demo.apk'
```

Choose a new output directory: the generator refuses to overwrite an existing
project. Use Java 21, Android SDK 35 and the configured Gradle wrapper.
The generated source ZIP includes the restricted catalogue and its three focused
loader/encoder tests. It builds without access to the full application's assets.

To verify the actual packaged contents against the published baseline:

```sh
python3 tools/demo/verify_demo.py --project /tmp/palmremote-demo \
  --apk '/tmp/palmremote-demo/palmremote demo.apk' \
  --baseline-apk downloads/PalmRemote-1.6.6.apk
```

The checker pins baseline SHA-256
`fef11c68d420ef4658884b3babe646802bb247618573b8b4d30f86ca7201bd08`,
checks all 130 profile assets, rejects extra/missing profiles and excluded brands,
checks every packaged asset against the demo project and verifies retained signal
bytes against 1.6.6. Unit tests exercise all 2,811 recorded commands, every settings
candidate and the retained AC112 packet fixtures. Android lint and APK signature /
manifest checks supplement that verification. No emulator UI or physical appliance
results are claimed.

Attribution and applicable third-party notices remain in the APK and source ZIP.
