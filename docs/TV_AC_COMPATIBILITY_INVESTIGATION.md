# TV and air-conditioner compatibility investigation

User report received **21 October 2026, Asia/Kuala_Lumpur**. The release under investigation is Palm Remote **1.3.0 / code 6**. The supplied Xiaomi archive contains Mi Remote **7.2.4M / code 6500**; references to “7.4.2M” in the follow-up request do not identify a different uploaded release. APK integrity, database hashes and extraction limits are recorded in the [original investigation](MI_REMOTE_724M_INVESTIGATION.md).

## What the user tested

| Appliance | Palm Remote 1.3.0 | Mi Remote 7.2.4M on the same phone |
| --- | --- | --- |
| Astro Ultra Box | Works, according to the user | Works |
| Daewoo TV | The tried candidates did not work | Works |
| Panasonic air conditioner | The tried remote did not work | Works |

The user does not know the TV or AC model numbers and tried only one or some of the 13 Daewoo candidates. The exact working Mi keysets and Palm Astro keyset have not been identified. These are user-reported appliance results, not cloud hardware tests. Astro's response establishes that this phone can transmit a useful IR signal; it does not establish that other keysets or carriers match the TV and AC.

## The gap in version 1.3.0

Version 1.3.0 added five Astro keysets retrieved from the regional public service identified during APK inspection. Its TV and AC signals still came from the older [GitHub v2.1 JSON archive](../tools/mi_catalogue/README.md). The APK's complete TV/AC support and stateful AC encoders had not been transferred into Palm Remote.

| Brand/category in 1.3.0 | Included source keysets | Recorded command associations | Finding |
| --- | ---: | ---: | --- |
| Daewoo TV | 13 | 446 | Source IDs were sorted lexicographically, rather than offered in the brand's matching-tree order. Selecting a numbered remote on an IR phone could save it without testing. |
| Panasonic AC | 3 | 29 | The source brand references 24 keysets. The other 21 use stateful definitions excluded by the raw-only converter. |

The first Daewoo candidate was `kk_1_195_2995`, from the brand's `others` section. The source matching tree instead starts with `xm_1_3399` and `xm_1_3390`. The [ordering generator](../tools/mi_matching/generate_order.py) retains all 2,810 original TV profiles across 1,023 source brand groups and preserves tree priority before regional `others` priority. Daewoo's `kk_1_195_3001` and `kk_1_195_2995` follow the tree candidates. Flattened candidate testing is not an implementation of Mi's conditional matching tree and does not identify the user's retail TV. No named Daewoo TV waveform set was found in the uploaded APK's bundled database, so importing that database alone does not establish a Daewoo repair.

The Panasonic records included in 1.3.0 were `137_9387`, `kk_3_137_7953` and `kk_3_137_8034`. Replaying those fixed captures does not implement the temperature, mode, power and fan state generation used by the omitted definitions. This is a confirmed coverage gap; it does not prove which untested replacement will match the user's AC.

## What is available from the uploaded APK

The raw resource `res/Jy4`, mapped to `raw/irlocaldata`, contains a RAR archive and a SQLite database. It includes brand mappings, direct raw captures, encrypted raw captures and stateful AC definitions. These representations require separate handling. The decoded raw arrays traced through Mi's native raw branch are already microseconds; the separate carrier-cycle encoder path must not be applied to them.

The [pinned-APK importer](../tools/mi_apk_catalogue/generate_apk.py) recovers every attributable raw TV/AC record in the supported database tables. All recovered raw signals passed validation, with no raw-format exclusions:

| Imported category | Profiles | Command associations | Source brand labels |
| --- | ---: | ---: | ---: |
| TV | 571 | 23,885 | 19 |
| Air conditioner | 33 | 319 | 19 |
| Combined | 604 | 24,204 | 32 distinct |

It retains source table/row identities, brand associations, command keys, carriers, complete timings and hashes. The [import summary](../tools/mi_apk_catalogue/import-summary.json) records the exact APK, RAR and SQLite hashes, schemas and row counts. The inspected TV/AC tables contain another **701 stateful AC definitions**. These are explicitly excluded by the raw importer and need separate encoders; they are not 701 failed raw decodes. Raw-profile import therefore does not implement all proprietary AC engines or reproduce the complete online catalogue.

Version 1.4.0 adds an independent Panasonic216 encoder using six non-executable framing, packet, field and checksum parameter records. Three (`137_2587`, `kk_3_137_4646`, `kk_3_137_7406`) are derived from the uploaded APK. The other three (`kk_3_137_7790`, `kk_3_137_7791`, `kk_3_137_7793`) come from the existing pinned GitHub Mi archive and are attributed separately. They are six additional parameter profiles, not recorded-command entries in the raw count.

The encoder covers 16–30 °C, Cool/Heat/Dry, Fan operating mode only where mapped, and each family's supported fan/swing fields. Auto operating mode remains excluded. Five profiles have explicit on/off semantics; `kk_3_137_7793` uses a power-toggle action. Unsupported controls remain unavailable rather than inheriting another family's field map. The new controls edit requested state and send only on an explicit settings/power action; the app does not read appliance state. No vendor Lua, native library or decompiled implementation is executed or copied into the app. See the [parameter tools](../tools/panasonic_ac/), [profile scope](SUPPORTED_DEVICES.md) and [release validation](VALIDATION.md).

## Online retrieval results

The current environment reported unrestricted HTTP policy **enforced**. Normal HTTPS requests preserved the managed proxy, TLS verification and the independent research user agent. No embedded credentials, opaque signing material, account tokens or private app cache were used.

| Read-only request | Result |
| --- | --- |
| Generic TV/AC brand route `/controller/brand/list/1`, device IDs 1 and 3; minimal and public-metadata variants | Four HTTP 200 responses, each with service status `19` and no brand data. Each body was 51 bytes with SHA-256 `7cc973a467a8e99d3a5393244ad1e149cc8426fd09fea0eb2f7ef868ba5407aa`. |
| Existing public `/app/public/get_stb_keyset`, with five actual published TV/AC IDs | Five HTTP 200 responses, each with code `-6`, message `invalid req` and `result: null`. No profile accepted. |

The public-keyset comparison used `kk_1_195_2995`, `kk_1_195_3001`, `xm_1_1697`, `kk_3_137_7406` and `kk_3_137_7953`. No IDs were invented and no STB brand was relabelled as a TV or AC. The generic client in the inspected APK uses a separate signed request path; these results do not establish usable anonymous TV/AC access or prove the precise reason for status `19`.

These are **application-service rejections**, not the cloud network-policy denial encountered earlier during Astro research. HTTP 200 alone is not a successful catalogue response. Request URLs, timestamps, response bodies and hashes remain in the cloud research directories `tv-ac-anonymous-20261020/` and `tv-ac-public-keyset-20261020/`; their UTC filenames precede the user's local date.

## Version 1.4.0 behavior

The raw import expands the combined catalogue to **4,315 profiles / 165,369 recorded associations / 1,291 distinct brand labels**. The original GitHub and Astro datasets remain unchanged; uploaded-APK records are a separately hashed source. The six Panasonic parameter profiles are additional to that raw count.

For Daewoo, the first candidate now follows the source matching-tree order instead of the lexicographic ID order. On a phone with a detected emitter, a numbered TV choice opens guided testing rather than saving a presumed match. **Find a working remote** on a saved recorded remote restarts source-prioritized testing and replaces that record only after confirmation. All 13 Daewoo candidates remain available. These changes address source-order and selection behavior; they are not evidence that one candidate has already controlled the user's TV.

For Panasonic, **Air conditioner → Panasonic → Find a Panasonic AC remote** offers six parameter candidates and a test at Cool 24 °C with power-on or the model's power-toggle action. An accepted send permits a user-response confirmation; it is not automatic appliance detection. A separately labelled untested preview can be saved without claiming a match. The earlier recorded Panasonic choices remain available. Exact supported fields differ by profile, and actual response on the user's AC still requires testing.

## Compatibility and validation boundary

The working Astro source IDs and command data remain unchanged while TV/AC support expands. The user confirmed one Palm Ultra Box match but did not identify its source keyset; individual profile flags therefore remain unverified. That report does not establish every Astro candidate or Ulti Box support.

A matching brand name, valid raw array, passing software check or accepted Android transmit cannot establish an appliance response. The user must confirm a response before a candidate is described as a working match. No further model-number request or phone-side JSON import is needed to browse the supported candidates. The complete online Mi catalogue and the remaining unsupported AC engines are not claimed as copied.

The original [APK inspection tools](../tools/apk_inspection/README.md), source archive and data provenance remain separate from independently written Android code. Vendor data is attributed without assigning the application's MIT license or the GitHub archive's AGPL license to it. Artifact hashes, actual executed checks and remaining exclusions are recorded in [release validation](VALIDATION.md).
