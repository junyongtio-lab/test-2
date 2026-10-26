# Decoder guided testing — Palm Remote 1.6.3

All **182 bundled decoder brands** now offers **Find a working remote · Guided
testing** as its first connection option. There are **411 candidate remotes** and
**12,416 recorded command associations**. See the [complete brand list](../downloads/Decoder-brand-coverage.csv).
The five existing Astro profiles and all TV, AC and fan signals are retained.

1. Install the update on an Android phone with a built-in IR emitter. Check that
   Palm reports **IR ready**.
2. Choose **Home → Decoder → Browse brands → your brand → Find a working remote · Guided testing**.
3. Turn the decoder and TV on. Aim the phone at the **decoder's IR sensor**,
   not the TV. Start with navigation, OK or channel buttons where available.
4. Choose a recorded button and tap **Send test signal**. Tap **decoder responded**
   only if the box responds correctly. Select a different button, send it and
   confirm its response too. Power may turn the box off; turn it back on before
   testing more buttons.
5. After confirming two different buttons, **Use this remote** becomes available.
   Test any other important buttons and confirm **Yes, save remote** to save.
6. If the box does not respond, try **Next remote**. Previous/Next clears all
   response confirmations. A brand with one candidate has no next candidate.
7. On a saved decoder remote, **Find a working remote** tests replacement
   candidates and updates the saved selection after confirmation.

The app cannot detect a decoder response. Confirming two buttons does not prove
all buttons work. Brand labels and source IDs do not identify every retail box
model; even Astro Ultra Box, Ulti Box and NJOI matches require actual testing.
If no candidate responds, check the emitter, aim and box model. Bluetooth-only
functions cannot be supplied by these recorded IR signals. No missing codes or
physical matches are invented. Without an emitter, sending, response confirmation
and saving a working match stay disabled. Explicit untested selections remain
available separately through **Browse recorded remotes**.

Version 1.6.3 (code 12) updates version 1.6.2 (code 11) with the same signing
certificate. All 411 candidates are validated by the production catalogue loader;
each has at least two recorded buttons. The shared response-state tests and TV
regression tests remain. Physical decoder response and emulator UI results are
not verified for this update; see the validation report for completed checks.
