# AC guided testing — Palm Remote 1.6.6

All **64 included AC brand labels** offer **Find a working remote · Guided testing**.
The catalogue contains 251 candidates: 101 recorded remotes, six existing Panasonic
parameter candidates, four original experimental candidates and 140 AC112 timing
candidates from 70 source definitions. A candidate is not a confirmed model match.
**624 of the original 694 unsupported source definitions still lack controls.**

## Additional controls for the 70 AC112 definitions

| Control | Definitions with a reviewed mapping |
| --- | ---: |
| Power, Cool/Heat, 16–30 °C | 70 |
| Fan: Auto, Low, Medium, High | 70 |
| Vertical swing: source-specific positions / on / off | 70 |
| Appliance power-on/off countdown and cancellation | 56 |
| At least one special function | 56 |

See [the exact per-profile controls](../downloads/AC-additional-controls.csv) and
[the 70 source IDs](../downloads/AC-expanded-covered-profiles.csv). Each definition
has two timing candidates; capabilities are identical between those two candidates.
These counts are **not whole-brand support or physical compatibility evidence**.

Special functions vary. Most mapped profiles provide Sleep / night fan. Some
provide Turbo, Eco / Energy saver, health/filter, display light, auxiliary heat,
horizontal swing or I Feel / body sensor. A label alone in source data does not
provide an encoder: declared but unmapped functions remain unavailable. The 14
profiles without timer mappings and 14 without special mappings show unavailable
buttons. No generic timer/special command is substituted for a missing mapping.

## Find and test a candidate

1. Use an Android phone with a built-in IR emitter and confirm **IR ready**.
2. Open **Home → Air conditioner → brand → Find a working remote · Guided testing**.
3. Choose a settings candidate or recorded-remotes group. Try the settings test
   **power on · Cool 24 °C**, aim at the AC, and observe the actual requested response.
4. Use Previous/Next to try other candidates within the selected brand. Each new
   candidate clears test confirmation. AC112 timing candidates are explicitly named.
5. Confirm the AC responded correctly before saving **Use this remote**. A saved
   preview is untested. Recorded AC tests require a send plus explicit **AC responded**.
6. Open the saved remote and separately test every important function. A power-test
   match does not prove fan, swing, timers or special functions also work.

The app cannot read the AC's state. A beep alone does not establish correct mode,
temperature, fan, vane movement or countdown behavior. No emitter means no sending
or confirmed match; browsing source data never sends IR or saves a working match.

## Fan, swing and special buttons

Choose a fan speed or vertical-swing option, then tap **Send fan speed** or
**Send swing**. The source-specific swing menu may offer fewer positions than
another definition. **Keep captured setting** preserves the source-template field;
it does not retain or read the appliance's live setting.

Under **Special functions**, select the mapped options and tap **Send special
functions**. The message includes the current power/mode/temperature/fan/swing
request. Sleep mappings on many profiles force night fan; turn Sleep off before
expecting another fan speed. Some features are limited to Cool or Heat; incompatible
requests are rejected. Auxiliary heat can be requested only in Heat mode. Source
options for horizontal swing keep their source option numbers where the exact
physical direction is unknown. Health and I Feel labels are mapped flags, not
phone sensing or filter-status readings.

Controls prepare a full packet and send only after a send or power button. Special
settings persist as requested state. **Keep captured setting** restores that field's
source-template value on the next send; it does not send a separate cancel command.

## Appliance timers

The **AC timers** section offers mapped power-on/off countdowns. Most profiles
allow 10–1,440 minutes in 10-minute steps. Three allow 0–1,440 in 30-minute steps,
and one allows 60–480 in 60-minute steps; cancellation is a separate button.
The app sends the source's native 10-minute count, not the public TCL API's
20-minute count. The ranges shown in each profile are enforced.

Select a countdown and press **Set power-on timer** or **Set power-off timer**.
**Cancel AC timers** clears both encoded countdowns and enable flags. The requested
packet supports one countdown at a time; setting one clears the other. Timer packets
can change the power/settings fields as specified by the source mapping. Check the
AC timer indicator and actual countdown behavior on your appliance.

Timer commands are **one-shot IR requests**: they run on the AC, not as phone
background schedules, and are removed from saved requested state. Ordinary settings
or power sends do not automatically repeat a previous timer request. The app cannot
read whether a timer was accepted, expired or remains active on the AC. Explicitly
cancel it on the AC if needed; closing the app does not cancel appliance timers.

## Evidence and remaining gaps

The basic packets match pinned public Mitsubishi112/TCL112 encoders. An independent
source-bit-table generator supplies 14,832 additional control packet fixtures;
7,120 fan/swing combinations also match the public TCL encoder. Both timing variants
are checked with strict upstream decoding. Timer and special-function mappings can
differ from public TCL fields, so strict decoding proves framing/checksum/payload,
not the physical meaning or correct operation on an appliance. Source hooks are
translated only for three exact reviewed hashes and are never executed.

See [encoder validation and reproduction](../tools/ac_expanded/README.md),
[build results](../downloads/validation.json), [brand coverage](../downloads/AC-brand-coverage.csv)
and [remaining unverified groups](../downloads/AC-encoder-groups.csv). Grouping the
624 unsupported definitions by packet length/prefix is an investigation aid, not
verified protocol identification or a sendable match.

Version 1.6.6 (code 15) updates 1.6.5 (code 14) with the same signing certificate.
The previously user-confirmed Panasonic source profile **137_2587** keeps its
existing Panasonic216 encoder. That source ID is not a retail model number, and
its confirmed power-on/Cool-24 test does not validate the 70 AC112 definitions.
