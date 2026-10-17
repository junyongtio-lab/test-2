# Known limitations

- Android APK only: Android 6.0+, ARM64/ARMv7/x86_64. No iPhone app or external IR hub.
- Most phones have no IR emitter. The app detects this; installing software cannot add hardware.
- Catalogue coverage is broad, not every brand/model ever sold. Exact appliance/remote matching matters; physical TVs, decoders and ACs were not available for testing.
- Samsung SmartThings needs existing manufacturer authorization, an appropriate token and internet. Tokens expire. There is no Palm account or subscription.
- LG local pairing uses the published legacy port-3000 SSAP interface on a trusted LAN. Secure-only sets need a different certificate-pairing implementation. Power turns the TV off; wake behavior is model dependent.
- Panasonic, Hisense, PRISM+ and Daewoo TV controls here use IR profiles, not an invented brand-wide network API. Voice/Bluetooth features are not implemented.
- Astro Ultra Box / Ulti Box now has a bundled user-supplied 29-command profile, with no smartphone file import required. The file omits bit order, so default classic raw NEC32 and alternate per-byte LSB interpretations are offered. Neither has been tested on a physical decoder; other Astro/NJOI models still need a compatible profile.
- The supplied Astro file assigns identical codes to Power/Blue and Mute/Right. Each pair transmits the same signal. The app preserves this data; distinct button behavior requires corrected codes from a tested source. The profile is not a manufacturer-published compatibility claim.
- Adjustable AC controls send requested full-state frames. Unsupported modes, fan/swing options and temperature ranges may be ignored/clamped by the selected upstream protocol. The app cannot read the actual AC state.
- Last-sent state is only an estimate. A physical remote, missed IR frame or manual control can desynchronize toggle protocols; reset and check the unit.
- Recorded AC buttons replay captures. Names such as Temp+, Mode or Fan may set several properties together, not apply relative changes. Some source-category devices are air coolers/ventilation devices.
- One Botti Power capture (5.59 seconds) is excluded intact. IR frames exceeding 1.9 seconds, 2,000 timings or supported carrier bounds are rejected rather than truncated.
- Upstream York stateful sending is unfinished and excluded. TEKNOPOINT uses its dedicated encoder to correct the upstream generic timing dispatch. Common-API KELON168/Hisense encoding is not claimed.
- Development signing only; production Play signing, store publishing and an iOS build are separate tasks. English UI; Bahasa Malaysia translation is not included.
