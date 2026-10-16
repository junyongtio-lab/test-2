Additional TV command facts
===========================

This catalogue contains factual handset button names, manufacturer and model
labels, numeric protocol identifiers, command values, and measured infrared
durations. The original Flipper-IRDB capture file text is not included.

Sources are pinned to Lucaslhm/Flipper-IRDB commit
`d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d`. Each profile records the original
source path, URL, and SHA-256 hash. The catalogue generator can verify the hashes
and numeric extraction in memory using `--verify-upstream`.

The upstream README says: "Commits prior to 2319685 are not covered." The cited
PRISM+ and Daewoo captures predate that 2025-08-07 licensing cutoff. Consequently
the source metadata uses `NOASSERTION`; this app does not represent the captures
as licensed under CC0. No source licence grant is asserted. The application
contains extracted numeric control facts, rather than copies of those capture
files or their comments. Application code has its own repository licence.

PRISM+: `TVs/PrismPlus/PrismPlus.ir`, 14 buttons. The source calls this a PRISM+
TV but does not identify a TV or handset model. It uses Samsung32 framing with
address 0x0e; this is the actual PRISM+ source, rather than a relabelled Samsung
TV profile.

Daewoo: `TVs/Daewoo/Daewood_Parsed.ir`, 6 buttons, model unspecified;
`_Converted_/IR_Plus/D/DAEWOO/R-40A15.ir`, 15 identified buttons; and
`_Converted_/IR_Plus/D/DAEWOO/R-43A08.ir`, 21 identified buttons. The latter two
record raw timings and cite their IR Plus model names. An unidentified button
in each raw source is deliberately omitted rather than assigned a guessed
function. These handset numbers do not establish compatibility with every
Daewoo television.

All profiles require an Android phone with an infrared transmitter. Physical
compatibility is untested. These are model-dependent community command facts,
not manufacturer APIs, Wi-Fi control or a promise to support every model.
