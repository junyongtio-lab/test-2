# AC guided testing — Palm Remote 1.6.5

All **64 included AC brand labels** have **Find a working remote · Guided testing**
as their first setup option. **This does not mean all 64 brands work.**

The update adds experimental basic controls for **70 of the previously unsupported
694 source definitions (10.1%)**. **624 definitions remain unsupported.**

- These 70 definitions span 45 source brand labels. Each offers two timing
  variants: 140 experimental candidates, not 140 definitions.
- Implemented controls: power on/off, Cool/Heat, and 16–30 °C.
- Fan speed, swing, timers and special functions are not implemented for these
  new candidates. Unexposed fields retain source template values.
- All 64 selectable AC brand labels now have at least one recorded or experimental
  candidate: 101 recorded, six Panasonic, four existing experimental and 140 new
  experimental candidates, **251 total**. This does not establish compatibility
  with every model or confirm any new candidate on physical hardware.

See the [exact 70 definitions](../downloads/AC-expanded-covered-profiles.csv),
[brand coverage](../downloads/AC-brand-coverage.csv), and
[encoder validation details](../tools/ac_expanded/README.md).

## Testing available candidates

1. Use an Android phone with a built-in IR emitter and confirm **IR ready**.
2. Choose **Home → Air conditioner → brand → Find a working remote · Guided testing**.
3. The overview lists only sendable controls and reports how many additional
   source definitions lack an encoder. Choose a settings candidate or the
   recorded-remotes group. Experimental settings candidates are labelled.
4. Aim at the AC. Settings tests request power on (or the stated toggle), Cool
   24 °C. A recorded AC signal can change several settings at once; its label
   describes the source capture, not the appliance's current settings.
5. Send a test and confirm the AC's actual intended response. In recorded testing,
   tap **AC responded** after observing it. Then **Use this remote** and the final
   confirmation save the selected candidate. Test other important settings too.
6. Previous/Next clears test confirmations. Settings candidates in the new brand
   guide remain within that brand; they do not cycle through other brands.
7. If no candidate responds, check the emitter, aim and AC model. The catalogue
   may not support that model. Source data browsing never sends a signal or saves
   a working match. No IR transmitter means no test transmission or confirmed save.

The app cannot read or synchronize AC state. A beep alone may not prove all
requested settings took effect. Verify the actual temperature/mode/fan behavior.
Untested previews remain explicitly labelled and are separate from working matches.

## Why most remaining profiles need encoders

An AC command commonly contains a whole state packet: power, operating mode,
temperature, fan speed, swing, checksums and sometimes multiple frames. A profile
template alone does not implement those rules. One brand can require several
protocols, and multiple brands can share a protocol family. A reliable encoder
needs documented packet layouts or verified captures plus packet and waveform
validation. Physical compatibility still needs testing on the appliance.

The [encoder grouping report](../downloads/AC-encoder-groups.csv) groups the 624
unsupported definitions by template payload length and their first three payload
bytes. **These are investigation leads, not verified protocol identifications or
supported matches.** Shared signatures do not establish identical fields,
checksums, framing or timing. Source hooks remain inert and are never executed.
This report helps prioritize encoder research; it does not add signal generation.

Version 1.6.5 (code 14) updates 1.6.4 (code 13) using the same signing certificate.
Inventory tests load or encode all 251 offered candidates. Public reference
checks compare 4,200 new packets and strictly decode 8,400 Java waveforms. Physical AC responses and emulator UI tests are unverified for this
update. See the validation report for completed build and test results.

## Panasonic source profile 137_2587

This profile already has a Panasonic216 encoder; no new encoder is needed for
that source candidate. In the AC guided overview, select **Settings candidate ·
137_2587**, then test **power on · Cool 24 °C** while aiming at the AC.
The ID is a source keyset, not a retail appliance model. Confirm the real AC
response before saving; successful packet generation does not prove a model match.
