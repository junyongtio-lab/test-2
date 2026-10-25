# AC protocol candidates in Palm Remote 1.6.0

The supplied `downloads/com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22(arm64-v8a)(nodpi)_apkmirror.com.7z` is the source for the four new candidates' templates, carrier frequencies and timing. The existing bounded exporter validates the archive, extracted APK and nested database hashes. All 734 source records remain intact.

| Source brand | Source profile | Independent protocol candidate |
| --- | --- | --- |
| LG | `22_3577` | LG 28-bit |
| Mitsubishi Electric | `kk_3_123_12255` | Mitsubishi 144-bit |
| DaiKin | `kk_3_58_4871` | Daikin 160-bit |
| Samsung | `kk_3_125_4745` | Samsung AC, extended 21-byte state |

The Panasonic216 candidates from 1.5 remain available. No other AC source profile is automatically assigned one of these encoders based on its brand or signature. The complete database's original `control` links still identify only the existing 36 source-mapped controls; the four experimental candidates are separately allowlisted and presented as experimental. Of the earlier 698 profiles without source-mapped controls, four now offer these independent candidates and 694 still have no controls.

Each new candidate offers Cool/Heat, 16–30 °C, Auto/Low/Medium/High fan speed and explicit power. A Samsung message includes the extended middle section on every send; Mitsubishi transmits the complete message twice. These candidates offer no timers, sleep, swing, Auto mode, Dry mode or custom-hook controls. Their source hooks remain inert. Editing settings or saving a preview sends nothing. Sending uses the existing Android emitter, carrier and duration checks. A working-match flag can be saved only after sending a test and receiving the user's confirmation.

The 960 packets covering every exposed setting combination match actual IRremoteESP8266 encoders at commit `1e2f0f3ef0a93cbf2a8ddb2e95130f8f4c584b3f`. All 960 generated waveforms also pass that reference's strict decoders and reproduce the expected packet bytes. The comparison deliberately uses source timing for transmission. See [reproduction tooling](../tools/ac_families/README.md).

**Exact equivalence to Mi's proprietary native engine and physical appliance response have not been verified.** The independent setting mappings follow the public protocols. A source template/framing match and successful software decoding are insufficient to guarantee a retail appliance match. Test power and every offered setting on the actual AC. There is no claim that these four candidates enable the other 694 profiles or every appliance of these brands.

Open **Home → Browse all 734 AC profiles**, search a source ID above, and select **Try protocol candidate**. The appropriate AC brand page also links to its candidate. No external file import, account or subscription is needed.
