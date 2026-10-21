# Panasonic216 state controls

The independent Java encoder in [PanasonicAcEncoder.java](../../app/src/main/java/com/universalremote/app/PanasonicAcEncoder.java) implements six
Panasonic216 parameter sets. It creates complete two-section IR packets for
requested settings, rather than replaying a captured temperature button as if
it could increment every unit. These are source keyset identifiers, not retail
model numbers or a claim of support for every Panasonic air conditioner.

| Source keyset | Numeric parameter source | Power behavior | Fan speed |
| --- | --- | --- | --- |
| `137_2587` | Uploaded Mi Remote 7.2.4M database | Explicit on/off | Auto/low/medium/high |
| `kk_3_137_4646` | Uploaded Mi Remote 7.2.4M database | Explicit on/off | Auto/low/medium/high |
| `kk_3_137_7406` | Uploaded Mi Remote 7.2.4M database | Explicit on/off | Auto/low/medium/high |
| `kk_3_137_7790` | Pinned GitHub Mi database v2.1 | Explicit on/off | Preserves source default |
| `kk_3_137_7791` | Pinned GitHub Mi database v2.1 | Explicit on/off | Preserves source default |
| `kk_3_137_7793` | Pinned GitHub Mi database v2.1 | Explicit power-toggle action | Preserves source default |

All six provide cool, heat and dry modes, temperatures from 16–30 °C and the
source's vertical-swing table. Fan-only mode exists for `137_2587` and
`kk_3_137_7793`. Auto mode is omitted because its temperature composition has
not been established. Unsupported controls are not invented. The toggle family
sets its power-action bit only for an explicit power press; sending ordinary
settings does not assert that the unit is on or off.

`source/parameters.json` contains only selected numeric fields and provenance.
The first three records identify actual SQLite model/source rows, APK/database
hashes and a canonical combined-row SHA-256. The other three identify complete
model JSON hashes within the pinned GitHub archive. Lua programs, vendor Java
implementation, native libraries and executable model scripts are omitted.
They are never run by the generator or Android encoder.

The packet is 27 bytes, split after byte 8. Both section checksums are rebuilt
after applying the explicitly mapped mode, temperature and supported fan/swing
fields. Framing comes from each source record and bytes are transmitted least
significant bit first. The exact source carrier is preserved, including the
37,900-Hz profile. Android's existing emitter/carrier checks still apply.

## Reproduce and audit offline

From the repository root:

```sh
python3 -m pip install -r tools/panasonic_ac/requirements.txt
python3 tools/panasonic_ac/generate.py --check
python3 -m unittest discover -s tools/panasonic_ac -p 'test_*.py'
python3 tools/panasonic_ac/verify_sources.py
```

The last command temporarily unpacks the exact tracked 7z archive using the
bounded pinned-APK extractor, reads the nested database without executing SQL
scripts, and verifies every selected numeric field against its original row.
It also verifies the full GitHub archive hash and all three model hashes before
comparing their fields. A pre-extracted exact APK can be supplied with `--apk`.
System `libarchive` is required for the APK's nested RAR resource, as documented
by [the raw importer](../mi_apk_catalogue/README.md).

Python checks cover source-snapshot integrity, reproducibility, numeric maps,
caps and malformed parameter parsing. Java checks include the independent
off/cool-25/auto-fan packet published in
[IRremoteESP8266 issue 525](https://github.com/crankyoldgit/IRremoteESP8266/issues/525),
section checksums, complete LSB framing, all mapped temperatures/modes, explicit
power and toggle semantics, state copying, invalid requests and the actual
Android sending gate through a fake emitter. No physical Panasonic response
has been verified in the cloud.

Original tooling and encoder code use the repository's MIT license. Source
parameter data retains its separate Xiaomi/Kookong or upstream GitHub notices;
no additional vendor-data rights or manufacturer endorsement are asserted.
