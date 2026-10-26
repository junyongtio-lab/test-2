# Fan guided testing — Palm Remote 1.6.4

All **133 bundled fan brands** offer **Find a working remote · Guided testing**
as their first setup option, covering **422 candidate remotes** and **4,181 recorded
command associations**. See the [complete fan brand list](../downloads/Fan-brand-coverage.csv).

1. Install the update on an Android phone with a built-in IR emitter. Confirm
   Palm reports **IR ready**.
2. Choose **Home → Fan → your brand → Find a working remote · Guided testing**.
3. Aim at the fan's IR sensor and observe the fan. Choose Power, Speed,
   Oscillation or another available recorded button, then tap **Send test signal**.
4. Tap **fan responded** only when the fan reacts correctly. With a multi-button
   candidate, select and test a different button, and confirm that response too.
5. After two different buttons are confirmed, **Use this remote** becomes available.
   Test other important controls, then confirm saving. If a power test turns the
   fan off, turn it on before testing speed or oscillation.
6. If there is no response, choose **Next remote**. Changing candidates clears
   all confirmations. A brand with only one candidate has no next candidate.
7. On a saved fan remote, **Find a working remote** tests replacements and updates
   the saved entry only after confirmation.

## Six limited candidates have only one recorded button

These candidates are labelled **One recorded button only**. Confirming that one
function enables saving only that limited selection; it does not supply missing
controls or establish a complete remote match.

| Brand | Source candidate | Only recorded button |
| --- | --- | --- |
| Aoda | mx_6_41_12 | Oscillation |
| Camel | mx_6_103_25 | Wind type |
| Rongsheng | mx_6_118_26 | Power |
| Wahson | mx_6_80_20 | Power |
| canbo | mx_6_89_22 | Power |
| sanyuan | mx_6_127_34 | Timer |

Other candidates for those brands, where present, remain separately available.
No missing power, speed, oscillation or timer commands are invented. A timer-only
capture may not have an immediately visible response; save only after observing
the actual intended effect.

The app cannot detect a fan response. Brand names and source IDs do not prove a
retail-model match. If no candidate works, check the emitter, aim and fan model;
the catalogue may not cover it. RF- or Bluetooth-only controls cannot be supplied
by these IR captures. Without an emitter, sending, response confirmation and
saving a working match remain disabled. Explicit untested selections are available
separately under **Browse recorded remotes**.

Version 1.6.4 (code 13) updates 1.6.3 (code 12) with the same signing certificate.
All existing TV, decoder, AC and fan signals are retained. Unit tests load all
422 fan candidates and verify the six one-button limits and confirmation resets.
Physical fan responses and emulator UI results remain unverified for this update.
