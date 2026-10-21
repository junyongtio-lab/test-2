# Complete AC source data in Palm Remote 1.5.0

All **734 air-conditioner source profiles** from the supplied Mi Remote **7.2.4M / code 6500** archive are included in the Android APK as JSON. There are **45 source brand labels**, **33 recorded-signal profiles** and **701 stateful definitions**, with **zero source records omitted**. This is 100% inclusion by bundled AC source-record count.

Each exported JSON preserves the complete original model row, referenced definition row, decoded definition, original brand row and decoded brand data. Original encrypted strings, numeric fields, parameter tables and hook/script text are retained. The [exporter](../tools/mi_ac_database/README.md) verifies the exact archive/APK/RAR/database hashes and compares every record against its source. Generated per-file hashes are in [the manifest](../tools/mi_ac_database/generated-manifest.json).

Open **Home → Browse all 734 AC profiles** or the same action in Help. Search by brand or source ID. AC brand lists also include the complete database's brands, and each relevant brand links to its included source records. **View included data** opens the stored complete JSON without importing a file, saving a device or transmitting IR.

**Included data and usable controls are separate.** Existing controls cover **36 of the 734 source records**: 33 recorded remotes and three Panasonic216 parameter sets. Their **Open controls** action opens the exact recorded keyset or Panasonic candidate and retains IR hardware/confirmation checks. The remaining **698 profiles** are labelled **Data included; controls unavailable** and have no working-control action. Their source text is not evaluated, compiled or passed to a vendor engine. Palm does not include a Lua runtime or Mi native AC engine.

The six existing adjustable Panasonic candidates remain available: three derive from this APK and three from the older pinned GitHub archive. Those three GitHub candidates are outside the 734-source count. The 734 stored records are not added to raw command counts or represented as 734 working remotes. The existing raw catalogue remains 4,315 profiles / 165,369 command associations, and six adjustable Panasonic profiles are counted separately.

Astro, TV and fan signals and current saved-device metadata are unchanged. All three IR detection states remain. No Palm account, payment or smartphone JSON import is required. This update includes the complete bundled AC data, not Mi Remote's online-only catalogue, and does not establish physical compatibility with every appliance.
