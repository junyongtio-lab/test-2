# Static inspection of the pinned local IR database

`inspect_local_ir.py` is original code covered by the repository's [MIT license](../../LICENSE).
It reads the exact Mi Remote 7.2.4M APK as data, verifies its SHA-256, locates
the RAR resource by its pinned APK path and SHA-256, extracts its one expected
SQLite member in memory, verifies that member's SHA-256, and checks every table's
schema and count. It independently decodes the observed AES/gzip JSON format
using standard primitives. It does not install or execute the APK, native
libraries, embedded Lua, DEX, or SQL scripts.

The output contains hashes, schemas, counts and search results. No extracted
brand, provider, device, command, waveform or proprietary implementation is
written. The APK and decoded vendor database stay outside this repository.
The APK/database license and redistribution rights are not established; the
MIT license covers this helper only.

Requirements are Python 3.11 or later with `sqlite3.Connection.deserialize`,
the system **libarchive** shared library with RAR4 support (Debian/Ubuntu package
`libarchive13` or `libarchive13t64`), and the separately declared optional Python
dependency in `requirements-local-ir.txt`. Libarchive uses its own BSD-style
license; PyCryptodome has BSD and public-domain components. Neither dependency
is included in Palm Remote's Android runtime.

```sh
python3 -m venv /path/to/local-ir-venv
/path/to/local-ir-venv/bin/pip install -r tools/apk_inspection/requirements-local-ir.txt
/path/to/local-ir-venv/bin/python tools/apk_inspection/inspect_local_ir.py \
  /path/to/MiRemote-7.2.4M.apk /path/to/new-summary.json
/path/to/local-ir-venv/bin/python -m unittest discover \
  -s tools/apk_inspection -p 'test_inspect_local_ir.py' -v
```

The eight optional tests exercise decompression limits, unexpected trailing
members/padding, malformed encodings, pinned hash failures and read bounds.
They skip when the optional crypto package is unavailable; inventory tests
remain usable with the standard library alone.

The output path must be new. Inputs are bounded at the exact 34,320,753-byte APK,
the exact 3,438,991-byte RAR resource and the exact 5,878,784-byte SQLite file.
The helper allows exactly one regular RAR entry named `irlocaldata.db` and
rejects links, extra entries, size changes and checksum failures. JSON records
have a 20 MB decompression limit and a 150 MB aggregate limit. SQLite is opened
in memory with queries restricted to reads and trusted schemas disabled.

The checked report is [mi724-local-summary.json](mi724-local-summary.json).
It was regenerated directly from the verified APK with the script above;
JADX output is not an input. Resource mapping evidence from static APK inspection
identifies `res/Jy4` as `raw/irlocaldata`. The database has 19 application tables
and one SQLite internal table. All 3,616 encrypted JSON records decoded without
error. Plain and decoded records contain no Astro, NJOI, Ultra Box or Ulti Box
identity term.

This result means no attributable named Astro profile was recovered from this
local database. Some numeric brand IDs have no bundled name. It does not rule
out an unnamed compatible capture, newer online records, or the user's report
that Mi Remote controls their Ultra Box. Native DAT decoding and online services
need separate investigation. No data is imported into Palm Remote on the basis
of numerical similarity or a name guess.

The decoded format is also publicly described by
[ysard/mi_remote_database](https://github.com/ysard/mi_remote_database).
That project's code/data notices remain separate; this helper does not copy
its implementation or include its exported database. The shared format key is
public format information, not a user account credential.
