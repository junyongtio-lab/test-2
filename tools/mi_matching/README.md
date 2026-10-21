# Source-based TV candidate order

This original, offline helper reads the exact pinned GitHub v2.1 archive already
retained under `tools/mi_catalogue/source/` and the unchanged accepted Mi index.
It writes only `app/src/main/assets/ir/matching_order.json` and its own
`generated-manifest.json`. It makes no network requests and never reads,
rewrites, synthesizes or transmits a command waveform.

The previous catalogue sorted remote model IDs lexicographically. That placed
Daewoo's `kk_1_195_2995` first, even though the source's tree prioritizes
`xm_1_3399` / `xm_1_3390` and lists KK captures separately as `others`.
The new metadata preserves candidate preference from that source:

1. Follow matching-tree child links from its declared root, remembering source
   keyset IDs in their original order, once each.
2. Visit remaining/detached nodes in source-array order so incomplete graph
   references never discard an accepted model.
3. Append other captures by ascending `locales.MY` priority, falling back to
   their declared `order`. Ties retain source-array order.
4. Append any accepted association still absent in its existing index order.

The regional-priority rule agrees with the independently inspected Mi 7.2.4M
local matching-data flow. It does not assert identical ordering for tied records
in Mi's map implementation. The original tree can be labelled for another
country; `sourceCountry` preserves that label rather than claiming Malaysian
physical compatibility.

**A flat candidate list is not Mi Remote's conditional matching tree.** This
metadata improves the order of guided tests without recreating its decision
questions or declaring the first remote compatible. Users must test the actual
appliance. No new Daewoo waveform, retail-model alias or physical match is
claimed.

All **2,810** accepted TV associations appear exactly once across **1,023** source
brand-file groups. Some display brands occur in multiple source files. Each group
records the original brand-file SHA-256 and each unchanged catalogue ID/model ID.
The archive and original accepted index have hard-pinned hashes. Other device
categories and independently sourced Astro profiles are outside this metadata.
Original index/profile assets remain byte for byte unchanged.

```sh
python3 tools/mi_matching/generate_order.py --check
python3 -m unittest discover -s tools/mi_matching -p 'test_*.py' -v
```

Run without `--check` to regenerate only the helper's two output files. Tests
cover actual Daewoo order, all accepted associations, cyclic/missing/detached
graph edges, regional ordering, unchanged signal data, tampered inputs and
deterministic checks. The standard Python library is sufficient.

The helper's independently written code uses the repository MIT license.
Generated ordering metadata derives from `ysard/mi_remote_database` data and
retains its separate AGPL-3.0-or-later notices. That source archive, license and
attribution remain included in the project; no additional vendor-data rights or
manufacturer endorsement are asserted.
