# Infrared handset command facts

The catalogue records factual manufacturer/model labels, handset functions,
numeric protocol identifiers and parameters, and measured infrared durations
from [Lucaslhm/Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB), pinned to
commit `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d`. Each profile records its
precise source path, URL, commit and original-source SHA-256 hash. Original
capture files and their comments are not bundled.

The upstream README explicitly says: "Commits prior to 2319685 are not covered."
The eight original TV captures predate that 2025-08-07 licensing cutoff.
Consequently their source metadata uses `NOASSERTION`: this application does
not represent those captures as licensed under CC0 and does not assert a source
licence grant. The catalogue contains extracted numeric control facts and
function labels, rather than reproductions of the source file text. Application
code has its own repository licence.

These are community supplied handset parameters, not a manufacturer API or an
endorsement. No physical television testing was performed during this build.
Compatibility depends on handset/model families. An Android device with a
working infrared emitter is required to transmit them.

The original eight handset/model families remain:

* Samsung BN59-01315B
* LG AKB75375604
* Panasonic Viera (source contributor: emptythevoid)
* Panasonic TH-43HS550K / HS550K / GX650K
* Hisense EN3Y39H
* Hisense EN3AH39H
* Hisense U7G
* Hisense 55U6K (2023)

The additional PRISM+ and Daewoo catalogue has its own `tv_extra/SOURCE_NOTICE.md`.
Air conditioner facts are documented alongside the corresponding AC catalogue
and protocol engine. Numeric profiles do not imply support for every model.

No Malaysian Astro/NJOI handset recording has been independently established
for this release. The unrelated Astro ASR340 satellite receiver recordings
are not included as Malaysian Astro codes. Users can import genuine learned
commands for their own decoder. An imported brand label does not establish
decoder compatibility.

Parsed NEC, NECext, Samsung32 and Kaseikyo parameters are expanded to alternating
mark/space durations in microseconds by this app's `IrEncoder`. Raw parameters
retain their source carrier and timings. Protocol references:

* [Flipper infrared format](https://developer.flipper.net/flipperzero/doxygen/infrared_file_format.html)
* [Arduino-IRremote protocol descriptions](https://github.com/Arduino-IRremote/Arduino-IRremote)
* [Flipper Kaseikyo format](https://github.com/flipperdevices/flipperzero-firmware/tree/dev/lib/infrared/encoder_decoder/kaseikyo)

`python3 native_ir/tools/build_catalog.py` regenerates `profiles.json` offline
from `native_ir/data/base_tv_facts.json`, using the Java encoder and a JDK on
`PATH`. `--verify-upstream` optionally checks the pinned source hashes and
the complete numeric extraction over HTTPS, in memory, without saving original
capture text. Regeneration preserves every existing command ID and waveform.
