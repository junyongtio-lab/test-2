# Supported-device scope, version 1.1.0

This is an Android APK for Android 6.0 or newer, with ARM64, ARMv7 and x86_64 native builds. It cannot run on iPhone. No physical appliance was attached; implemented protocols and source labels are not hardware certification.

## Televisions and decoders

| Family | Route | Coverage and requirements |
| --- | --- | --- |
| Samsung | Official SmartThings API; IR | Existing manufacturer authorization for the API; only returned capabilities. IR profile BN59-01315B or matching learned profile. |
| LG | Published webOS SSAP subset; IR | Private LAN, TV approval, legacy port 3000. IR handset AKB75375604. Secure-only and pre-webOS sets are not covered by this Wi-Fi transport. |
| Panasonic | IR | Viera and TH-43HS550K / HS550K / GX650K source profiles. |
| Hisense | IR | EN3Y39H, EN3AH39H, U7G and 55U6K (2023) source profiles. No VIDAA, Google TV or Roku network adapter. |
| PRISM+ | IR | Manufacturer-labelled community capture, exact TV model unspecified; 14 commands include power, volume, mute and navigation. Voice/Bluetooth features are not IR commands. |
| Daewoo | IR | Community generic (6 commands), R-40A15 (15) and R-43A08 (21) handsets. R-40A15 has no identified power command; it is not invented. |
| Astro/NJOI | Learned IR import | Compatible owner-supplied decoder profile required. No verified Astro decoder codes are bundled. |

The 12 bundled TV/handset profiles contain 383 commands. Select the correct profile and test the specific model. The old eight profile IDs are preserved so saved devices remain valid after an upgrade.

## Air conditioners

The searchable picker combines 147 distinct brand labels from two different sources:

- **Adjustable:** 141 documented remote/model profiles, 79 manufacturer/protocol labels and 65 stateful sending families. Full-state generation comes from the genuine IRremoteESP8266 v2.9.0 implementation, including protocol checksums and repeats. See [exact encoded profile list](../app/src/main/java/com/universalremote/app/ac/AcProfiles.java).
- **Recorded:** 158 community handset profiles, 96 source brand labels and 1,656 exact signals. See [full brand/model CSV](../app/src/main/assets/ir/ac_captures/coverage.csv) and [brand summary](../app/src/main/assets/ir/ac_captures/summary.json). Some source files describe air coolers, ventilation devices or humidifiers; model identifiers are retained.

The encoded and recorded lists overlap; their brand counts must not be added. A catalogue label is not evidence that every appliance sold under that brand uses the same protocol. For unsupported brands, import a learned profile with category `air_conditioner`; the importer accepts any AC manufacturer name. This is extensibility for learned signals, not automatic compatibility with every brand/model.

Encoded profiles include documented families/models for Daikin, Panasonic, Mitsubishi Electric and Heavy Industries, Midea, Gree, Haier, LG, Samsung, Sharp, Toshiba, Hitachi, Fujitsu, Carrier, AUX, Daewoo and many others. Hisense and York recorded profiles remain available; the incomplete upstream stateful York dispatcher and unsupported common-API KELON168/Hisense mapping are deliberately excluded. No PRISM+ AC mapping is invented.

Use **+ → Air conditioner**, search a brand, then select an **Adjustable** or **Recorded** entry. Adjustable controls send the requested power, temperature, mode, fan and vertical swing when Send is pressed. Unsupported settings can be clamped/ignored by that protocol. Recorded commands preserve source labels and may set several properties together; a captured Temp+ button is not assumed to calculate a new temperature.

The app stores a last-sent request, not measured appliance state. Other remotes can desynchronize toggle protocols. Reset the last-sent state when needed and check the actual unit. No external USB, Bluetooth, Wi-Fi IR or RF hub is implemented.

## Hardware and source facts

All direct IR paths require an Android phone with a built-in consumer IR transmitter, a supported carrier and line of sight. Most Android phones and all iPhones lack this hardware. Buttons remain disabled when no emitter is detected.

TV and AC numerical capture facts come from pinned community source revision `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d`. Per-profile URLs, hashes and source rights notes are bundled. Legacy source licenses are **NOASSERTION**, not CC0; original legacy capture text is excluded. Encoders are separately licensed source code, with the LGPL native library and relink instructions included.

Record exact appliance model, region, firmware, handset, phone/Android version, profile and commands before reporting physical compatibility. See [validation](VALIDATION.md), [API sources](API_INTEGRATIONS.md) and [limits](KNOWN_LIMITATIONS.md).
