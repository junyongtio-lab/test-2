# Reproduce extraction of the supplied Mi Remote archive

The repository's original MIT-licensed `extract_mi724_archive.py` handles only
the exact 7z archive supplied by the user. It never installs or executes the APK.
The archive and extracted Xiaomi application remain third-party research inputs,
not Palm Remote source or reusable IR data.

Install the optional pinned parser into a dedicated virtual environment:

```sh
python3 -m venv /workspace/toolchains/apk-analysis/venv
/workspace/toolchains/apk-analysis/venv/bin/pip install -r tools/apk_inspection/requirements-archive.txt
mkdir -p /workspace/research/mi724
/workspace/toolchains/apk-analysis/venv/bin/python tools/apk_inspection/extract_mi724_archive.py \
  'downloads/com.duokan.phone.remotecontroller_7.2.4M-6500_minAPI22(arm64-v8a)(nodpi)_apkmirror.com.7z' \
  /workspace/research/mi724/MiRemote-7.2.4M.apk
```

The output must be a new filename whose parent exists. If an APK already exists,
choose a different explicit output name; the helper refuses to overwrite files
or follow an existing final symlink. The supplied archive is also retained in the
research workspace as `/workspace/research/mi724/source.7z`.

Before decompression, the helper verifies all of these fixed facts:

- Archive size: **24,137,558 bytes**.
- Archive SHA-256:
  `72fc2fc859255e19963370ecd3252889af3f8a2e609d2c74c2d995e03b4267a6`.
- Exactly one member with the expected APK basename and a declared size of
  **34,320,753 bytes**.

It then uses py7zr **1.0.0** `BytesIOFactory(limit=40 * 1024 * 1024)` for
in-memory extraction. No member-controlled filesystem path is created. Before
writing, it requires the actual APK size above and SHA-256
`4c20655f2b636d86152202994da2a1b88f4ebf5bddf58b7ef6c2bf8e42fa5c73`.
The verified bytes are written only to the caller's output filename with
exclusive creation and mode `0600`; this gives no execute permission.

These hashes identify the exact supplied bytes. They are not an independent
publisher certificate endorsement or a claim that Astro signal records exist.
Continue with [static inspection](README.md), and preserve verification and JADX
limitations in the [investigation record](../../docs/MI_REMOTE_724M_INVESTIGATION.md).
