# Complete 734-profile Mi APK AC source database

This independent offline exporter copies **every one of the 734 AC records** in the exact user-supplied Mi Remote **7.2.4M / code 6500** APK into bundled JSON. No source AC record is omitted. The 45 brand labels and source IDs come from that archive, not guessed appliance model names.

Each profile preserves:

- Every original source-row column and value, including the original encrypted data or reference.
- The complete referenced `kk_remote_ircode_src` row, where present.
- The complete decoded IR definition, including numeric parameters and original hook/script text.
- The original brand row and decoded brand data.
- Original carrier, version, source identities and source/output SHA-256 hashes.

The verified inputs are the same pinned 7z, APK, RAR and SQLite database documented by [the raw importer](../mi_apk_catalogue/README.md). Default extraction is bounded and temporary. The database is read-only with trusted schemas disabled; no SQL script, APK, native library or source hook is executed.

## Included data versus implemented controls

| Source data | Included profiles | Existing controls |
| --- | ---: | ---: |
| Recorded signals | 33 | 33 |
| Stateful definitions | 701 | 3 Panasonic216 |
| Total | **734** | **36** |

The other **698 definitions are included as data with controls unavailable**. Copying their data does not implement their encoders. Source hook text is stored verbatim as inert JSON strings; Palm has no script interpreter or vendor native AC engine and never evaluates those strings. The in-app browser displays this distinction and offers control links only for implemented profiles. Original raw/Panasonic controls retain their emitter and carrier gates. Data browsing sends no IR and does not save a working remote.

Three additional GitHub Panasonic parameter profiles remain available separately; they are outside this source's 734-profile count. Existing TV/Astro/fan data and source associations are unchanged. Complete inclusion of this bundled AC database is not coverage of Mi Remote's online-only database or proof of physical compatibility.

## Reproduce and check

From the repository root, use Python 3.11 or newer, system libarchive and the existing pinned dependencies:

```sh
python3 -m pip install -r tools/mi_apk_catalogue/requirements.txt
python3 tools/mi_ac_database/generate.py --check
python3 -m unittest discover -s tools/mi_ac_database -p 'test_*.py' -v
```

Run without `--check` to generate `app/src/main/assets/ir/mi_ac_database/` and `generated-manifest.json`. Optional `--apk` accepts the exact already extracted APK; its pinned hash is still verified. Checks compare every source row, referenced row, decoded definition and brand record, verify all 734 identities and all asset hashes, preserve hook text without evaluating it, validate implemented-control links and reject missing/extra/changed outputs.

Original exporter/app code is MIT. The copied data and hook text retain separate Xiaomi/Kookong/source-brand attribution; the app's MIT license and the older GitHub dataset's AGPL license are not assigned to this vendor data. See [packaged attribution](../../app/src/main/assets/ir/ATTRIBUTION.md).
