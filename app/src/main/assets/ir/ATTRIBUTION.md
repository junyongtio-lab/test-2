# Infrared handset profiles

The bundled `.ir` recordings are unmodified files from
[Lucaslhm/Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB), pinned to commit
`d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d`. The repository dedicates its database
under **CC0 1.0 Universal**; the complete upstream license is `LICENSE-CC0.txt`.
Original files are included in `originals/`. Each `profiles.json` entry records
its precise source path, URL, commit and original-file SHA-256.

These are community supplied handset recordings, not a manufacturer API or an
endorsement. None has been tested against physical televisions during this build.
Compatibility is limited to compatible handset/model families. An Android device
with a working infrared emitter is required to transmit them.

Bundled handset/model families:

* Samsung BN59-01315B
* LG AKB75375604
* Panasonic Viera (upstream contributor: emptythevoid)
* Panasonic TH-43HS550K / HS550K / GX650K (see the original source header for the
  contributor's complete model list)
* Hisense EN3Y39H
* Hisense EN3AH39H
* Hisense U7G
* Hisense 55U6K (2023)

No Malaysian Astro/NJOI handset recording has been independently established for
this release. The database's unrelated Astro ASR340 satellite receiver recordings
have deliberately not been included as Malaysian Astro codes. Users can import
genuine learned commands for their own decoder. No decoder compatibility is
claimed merely because an imported profile is labelled Astro.

Parsed NEC, NECext, Samsung32 and Kaseikyo codes are expanded to alternating
mark/space durations in microseconds by this app's `IrEncoder`. Raw recordings
retain their original carrier and timings. Protocol layout/timing references:

* [Flipper infrared format](https://developer.flipper.net/flipperzero/doxygen/infrared_file_format.html)
* [Arduino-IRremote protocol descriptions](https://github.com/Arduino-IRremote/Arduino-IRremote)
* [Flipper Kaseikyo format](https://github.com/flipperdevices/flipperzero-firmware/tree/dev/lib/infrared/encoder_decoder/kaseikyo)

`python3 native_ir/tools/build_catalog.py` regenerates `profiles.json` offline
from the bundled originals using the Java encoder. It needs a JDK on `PATH`.
