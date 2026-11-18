# Third-party notices

SimpleTV itself is licensed under the MIT License (see `LICENSE`). Release
packages also contain third-party components with their own licenses.

## Bundled with Windows and Linux builds

- **mpv / libmpv** — LGPL-2.1-or-later. The bundled Linux build uses
  `-Dgpl=false` and ships `libmpv` dynamically, together with
  `licenses/mpv-LGPL.txt`.
- **FFmpeg** — LGPL-2.1-or-later. Built without `--enable-gpl`; the license text
  is shipped as `licenses/FFmpeg-LGPL.txt`.
- **libplacebo** — LGPL-2.1-or-later. Built from source because mpv 0.42 needs
  at least 7.387 while Ubuntu 24.04 ships 6.338; the license text is shipped as
  `licenses/libplacebo-LGPL.txt`.
- **GTK 3**, **GLib**, **GnuTLS**, **libxml2** and other system libraries —
  provided by the target distribution under their own licenses.

These components are separate libraries loaded at runtime. Their source code is
available from the upstream projects at the versions recorded in
`packaging/linux/build-linux-mpv.sh` and `packaging/linux/build-linux-packages.sh`.
If you redistribute the bundled packages, keep the `licenses/` directory intact.

## Application dependencies

- Jetpack Compose, AndroidX, Media3 — Apache License 2.0.
- Kotlin, kotlinx.coroutines — Apache License 2.0.
- Flutter and the Dart SDK — BSD 3-Clause.
- `media_kit`, `media_kit_video` — MIT.
- `http`, `shared_preferences`, `path_provider`, `window_manager`, `intl`,
  `crypto`, `xml`, `desktop_drop` — BSD/MIT/Apache 2.0 as declared by their
  upstream packages.

The full dependency graph and versions are pinned in `gradle/libs.versions.toml`
and `desktop/pubspec.lock`.
