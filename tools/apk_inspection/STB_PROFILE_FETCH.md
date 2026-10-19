# Anonymous regional STB catalogue research

`fetch_stb_profile.py` is an independently written, development-only helper. It
requests the three anonymous regional STB JSON endpoints identified statically in
Mi Remote 7.2.4M. It contains no Xiaomi code, embedded credentials, account access,
legacy signing client, native libraries, or executable model scripts. It does not
modify Palm Remote or claim that a fetched keyset controls any appliance.

**Live retrieval succeeded on 19 October 2026**, after the resumed environment
reported unrestricted networking as enforced. Both ID and MY brand queries
returned HTTP 200/code 0 and 1,262 brands, including Astro ID 3833. The Malaysian
Astro match query returned five keysets; all five full responses were retrieved.
Their native `key` representation independently decoded into 216 raw commands.
Exact response evidence and the offline generator are in
[`../mi_stb/`](../mi_stb/README.md). Physical compatibility remains unverified.

The first full-keyset fetches were correctly retained as rejected evidence
because this helper initially required `rc`; the actual live shape is `key`.
The schema check now recognizes these representations explicitly. On 18 October,
earlier attempts failed at the managed boundary with proxy CONNECT HTTP 403.
Saving a draft alone had not applied that earlier configuration; the next day's
current runtime and actual requests established access.

The helper uses Python 3.11+ and its standard library. Default urllib proxy
handling, TLS verification, and CA trust remain enabled. Its user agent identifies
it honestly as `PalmRemoteResearch`; it does not impersonate Mi Remote.

## Fetch one stage at a time

Keep downloaded responses outside the source repository. Every output directory
must be new; an existing directory is rejected. The hostname and three paths are
fixed, redirects are rejected, countries/identifiers are validated, responses are
limited to 8 MiB, and each invocation has a 30-second wall-clock deadline.

```sh
python3 tools/apk_inspection/fetch_stb_profile.py --help

# The APK's brand-list builder sends ID (Indonesia), even for its international path.
python3 tools/apk_inspection/fetch_stb_profile.py brands --country ID \
  --output /workspace/research/stb-brands-id

# MY is an explicit comparison query; this exact APK does not send MY for this list.
python3 tools/apk_inspection/fetch_stb_profile.py brands --country MY \
  --output /workspace/research/stb-brands-my
```

Inspect the returned `payload.json` for an actual Astro brand and its returned
numeric identifier. Use that identifier, replacing the illustrative `123` below:

```sh
python3 tools/apk_inspection/fetch_stb_profile.py matches --country MY --brand-id 123 \
  --output /workspace/research/stb-astro-matches
```

Inspect `payload.json`'s `matchs` array, then use one actual returned match ID.
`RETURNED_MATCH_ID` is a placeholder, not a known Astro profile:

```sh
python3 tools/apk_inspection/fetch_stb_profile.py keyset --id RETURNED_MATCH_ID \
  --output /workspace/research/stb-selected-keyset
```

Requests are ordinary GETs on `https://sg-urc.io.mi.com`:

| Command | Path | JSON in `data` query parameter |
| --- | --- | --- |
| `brands` | `/app/public/get_stb_brand` | `{"country":"ID"}` or explicit `MY` comparison |
| `matches` | `/app/public/get_stb_match` | `{"country":"MY","brandid":123}` |
| `keyset` | `/app/public/get_stb_keyset` | `{"id":"RETURNED_MATCH_ID"}` |

Each directory records the bounded raw `response.bin`, plus `metadata.json` with
request URL, retrieval timestamp, status, response size/SHA-256, truncation and
validation result. A successful structural check also writes `payload.json`.
HTTP errors, redirects, oversized responses, malformed/ambiguous JSON, nonzero
service codes and unexpected payload shapes return a nonzero exit code without
accepting a payload. A truncated raw body is explicitly labelled as a prefix.

Success requires HTTP 200, integer `code == 0`, and the expected response shape:
`result.data` as a brand array, `result.matchs` as a match array, or
`result.data` as a keyset with positive integer `frequency` and exactly one
nonempty `key` or `rc` object. Native `key` entries require nonempty string names
and string payloads. `commandRepresentation` records which form was accepted;
their encodings must be reviewed separately. Empty catalogue arrays can be valid responses and do not establish that
Astro is present. `payloadValidated` means the JSON structure passed these checks;
IR timings remain undecoded and physical compatibility remains unverified.

The pinned Astro `key` payloads now have provenance review, independent bounded
timing decoding and button/alternate-code preservation in `tools/mi_stb`.
Testing with the user's actual Ultra Box or Ulti Box remains necessary before a
physical compatibility claim. Do not
invent IDs, treat a brand match as a retail-model match, add credentials, disable
TLS, or route around a network policy denial.

## Offline checks

```sh
python3 -m unittest discover -s tools/apk_inspection -p 'test_fetch_stb_profile.py' -v
```

Fixtures verify fixed request paths/data, identifier injection rejection, bounded
response reads, strict JSON/result validation, raw hashes, HTTP/truncation error
handling, preserving previous output, the wall-clock deadline, and the explicit
unsigned research user agent. They make no network requests.
