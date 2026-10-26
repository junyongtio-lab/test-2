# TV guided testing — Palm Remote 1.6.2

Built from the repository's 1.6.1 source. Version code 11 allows an update over
version 1.6.1 (code 10). Existing recorded TV signals and AC features are retained.

Choose **Home → Television → brand → Find a working remote · Guided testing**.
Every one of the **1,020 selectable TV brands** shows this as the first
connection option, covering **3,381 bundled remote candidates**. This includes
Sony, Philips, TCL, Toshiba, Daewoo, JVC, Haier, Hitachi, Skyworth, Akai, Sanyo
and Grundig, as well as the six brands listed below. The complete list is in
[TV-brand-coverage.csv](../downloads/TV-brand-coverage.csv). Samsung SmartThings and LG webOS remain additional options.

| TV brand | Bundled candidates |
| --- | ---: |
| Samsung | 69 |
| LG | 35 |
| Hisense | 60 |
| Panasonic | 30 |
| Sharp | 36 |
| Xiaomi | 1 |

1. Use an Android phone with a built-in IR emitter. Check that Palm shows **IR ready**.
2. Turn the TV on and aim the phone's emitter at the TV sensor from 1–3 metres.
3. Start with Volume up if available, or another recorded button. Tap **Send test signal**. If the TV responds correctly,
   tap **TV responded**. This confirms the button just sent, even if you change
   the dropdown selection afterwards.
4. Select a different recorded button, such as Power, Input or navigation. Send
   it and confirm the actual response. If Power turns the TV off, turn it back on
   before testing navigation or volume.
5. After confirming two different buttons, **Use this remote** becomes available.
   Test other important buttons before saving. Two buttons working does not prove
   every button or every TV model is supported.
6. If the TV does not respond, choose **Next remote**. Confirmations clear whenever
   the candidate changes, including returning with Previous. Xiaomi currently has
   only one bundled candidate; other brands may also have just one. No additional codes are invented.
7. Save only when the TV responds correctly. Reopen **Find a working remote** on
   a saved IR remote to test another candidate and replace that saved selection.

If no candidate works, check the emitter, aim and TV model. A Bluetooth-only TV
function cannot be supplied by these IR captures. An unsupported model may need
additional verified source captures. The app cannot detect appliance responses.
Without an emitter, sending and response confirmation stay disabled. A separately
labelled saved untested selection does not count as a working match.

## Validation scope

Unit tests load all 3,381 candidates across all 1,020 selectable brands through
the production catalogue and verify at least two recorded buttons are available
for each candidate. The original six-brand power/volume tests and response
confirmation tests remain. Included emulator tests exercise eighteen representative
brand entries, candidate navigation and no-emitter gates. See the delivered validation report for checks actually completed.
No physical TV response has been verified in this environment.
