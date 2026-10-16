# Known limitations

- Android APK only. An iPhone app requires a separate iOS implementation, Apple development/signing tools and testing; an APK cannot be installed on iOS.
- No all-model claim. The four TV manufacturers do not share a universal public remote API. See the supported-device matrix.
- Samsung SmartThings requires existing manufacturer authorization and internet access. Tokens can expire. Only capabilities actually exposed by a selected device are offered.
- LG uses the published Connect SDK's local webOS SSAP transport on port 3000. Encrypted-only models and TLS certificate pairing are not implemented. Use only a trusted private network; this legacy local protocol is unencrypted.
- Panasonic and Hisense Wi-Fi adapters are not shipped as functioning controls because an appropriate public, permitted manufacturer interface was not verified. Roku ECP is excluded from the mobile app.
- Astro/NJOI is supported through validated learned IR import/transmission only. A compatible decoder profile is not bundled; no Astro physical tests were performed.
- IR needs built-in Android Consumer IR hardware. There is no hub, USB, Bluetooth or generic RF transport in this build.
- No physical TV/decoder was available. Software tests and emulator launch do not prove hardware compatibility.
- The delivered APK uses Android development signing. Google Play release signing, AAB publishing, Apple distribution and store approval are separate tasks.
- UI is English. Bahasa Malaysia translation, custom layouts, rooms and routines are not included in this narrowed first build.
