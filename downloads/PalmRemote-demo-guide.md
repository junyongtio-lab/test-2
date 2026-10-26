# Palm Remote Demo

Install **palmremote demo.apk** on Android 6.0 or newer. It appears as **Palm Remote
Demo** and installs separately from the full Palm Remote app.

Only these brand/category combinations are included:

- TV: Samsung
- Air conditioner: Panasonic and Midea
- Fan: KDK and Panasonic
- Decoder: Astro

All other brands' remote assets are excluded. The demo provides offline infrared
controls and retains **Find a working remote · Guided testing**. It does not offer
network pairing or cloud connections.

Choose a category and brand, then use guided testing to find a candidate. Aim at
the appliance, send the test and confirm the intended response before saving.
TV, fan and decoder candidates require the available distinct-button confirmations;
AC candidates require an explicitly confirmed AC response. Saving a preview does
not mark the candidate as working.

Real sending requires a compatible built-in Android IR emitter. Without an emitter,
you can browse and save an untested preview. Brand inclusion and automated packet
checks do not guarantee compatibility with every model. Test each important control.
AC fan, swing, timer and special functions depend on the selected candidate; the app
cannot read the appliance state.

The demo contains 69 Samsung TV candidates, 14 Panasonic/Midea AC candidates,
six KDK/Panasonic fan candidates and five Astro decoder candidates (94 total).
Its 2,811 recorded commands and retained settings candidates passed loader/encoder
tests. All packaged profile assets match the retained 1.6.6 originals byte for byte.
No physical appliance or emulator UI test result is claimed.

See `palmremote-demo-validation.json` and `palmremote-demo-SHA256SUMS` for validation
and hashes. `palmremote demo source.zip` contains the restricted standalone source
project. Reproduction scripts and build details are under `tools/demo/`.
