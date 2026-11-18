#!/usr/bin/env bash
set -euo pipefail

PREFIX="$1"
JOBS="${SIMPLETV_BUILD_JOBS:-$(nproc)}"

# Component recipes: bump the revision when build flags change. Versions live
# in the fetch URLs below; comments and unrelated script edits deliberately do
# not invalidate the cached media prefix.
FFMPEG_RECIPE="1:ffmpeg-8.1.3:shared,gnutls,libxml2,zlib"
LIBPLACEBO_RECIPE="1:libplacebo-7.387.0:opengl,shaderc,no-vulkan"
MPV_RECIPE="1:mpv-0.42.0:libmpv,plain-gl,alsa,pulse:$FFMPEG_RECIPE:$LIBPLACEBO_RECIPE"

needs_build() {
  local stamp="$1" recipe="$2" artifact="$3"
  [[ ! -f "$stamp" || "$(cat "$stamp")" != "$recipe" ]] || ! compgen -G "$artifact" >/dev/null
}

need_ffmpeg=false
need_libplacebo=false
need_mpv=false

needs_build "$PREFIX/.recipe-ffmpeg" "$FFMPEG_RECIPE" "$PREFIX/lib/libavcodec.so*" && need_ffmpeg=true
needs_build "$PREFIX/.recipe-libplacebo" "$LIBPLACEBO_RECIPE" "$PREFIX/lib/libplacebo.so*" && need_libplacebo=true
needs_build "$PREFIX/.recipe-mpv" "$MPV_RECIPE" "$PREFIX/lib/libmpv.so.2" && need_mpv=true
if [[ "$need_ffmpeg" == true || "$need_libplacebo" == true ]]; then
  need_mpv=true
fi

if [[ "$need_ffmpeg" == false && "$need_libplacebo" == false && "$need_mpv" == false ]]; then
  echo "Media prefix is up to date: $PREFIX"
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fetch_source() {
  local url="$1" sha="$2" name="$3"
  curl --fail --silent --show-error --location --retry 3 "$url" -o "$WORK/$name.tar.gz"
  printf '%s  %s\n' "$sha" "$WORK/$name.tar.gz" | sha256sum --check
  mkdir "$WORK/$name"
  tar -xzf "$WORK/$name.tar.gz" --strip-components=1 -C "$WORK/$name"
}

mkdir -p "$PREFIX/licenses"

if [[ "$need_ffmpeg" == true ]]; then
  echo "Building FFmpeg 8.1.3..."
  fetch_source https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7 \
    a406fe831cbb605f5fe50e43eb566e502ae63ff3b3206bfa744e4b31b8bb73ac ffmpeg
  (
    cd "$WORK/ffmpeg"
    ./configure --prefix="$PREFIX" --disable-static --enable-shared \
      --disable-autodetect --enable-gnutls --enable-libxml2 --enable-zlib \
      --disable-programs --disable-doc --disable-debug
    make -j"$JOBS" install >"$WORK/ffmpeg-build.log" 2>&1 || { tail -n 100 "$WORK/ffmpeg-build.log"; exit 1; }
  )
  cp "$WORK/ffmpeg/COPYING.LGPLv2.1" "$PREFIX/licenses/FFmpeg-LGPL.txt"
  printf '%s\n' "$FFMPEG_RECIPE" > "$PREFIX/.recipe-ffmpeg"
fi

export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
export LD_LIBRARY_PATH="$PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

if [[ "$need_libplacebo" == true ]]; then
  echo "Building libplacebo 7.387.0..."
  fetch_source https://codeload.github.com/haasn/libplacebo/tar.gz/275f7dea27924cdd34ab2e6c20e448b6bc4fab65 \
    adbca47395410ada01414bc527e0103c716b9b758be857464c40e5ff0ec695be libplacebo

  # libplacebo generates its OpenGL loader with glad and expects glad, Jinja2
  # and MarkupSafe inside 3rdparty/ (the tarball has empty submodule dirs).
  python3 -m venv "$WORK/python-deps-venv"
  "$WORK/python-deps-venv/bin/python" -m pip install --disable-pip-version-check --no-cache-dir \
    --target "$WORK/python-deps" 'glad2==2.0.8'
  for sub in glad jinja/src markupsafe/src; do
    mkdir -p "$WORK/libplacebo/3rdparty/$sub"
  done
  cp -a "$WORK/python-deps/glad" "$WORK/libplacebo/3rdparty/glad/"
  cp -a "$WORK/python-deps/jinja2" "$WORK/libplacebo/3rdparty/jinja/src/"
  cp -a "$WORK/python-deps/markupsafe" "$WORK/libplacebo/3rdparty/markupsafe/src/"

  # mpv 0.42 requires libplacebo >= 7.387, while Ubuntu 24.04 ships 6.338.
  meson setup "$WORK/libplacebo-build" "$WORK/libplacebo" --prefix "$PREFIX" --libdir lib \
    --buildtype release --wrap-mode nodownload \
    -Ddemos=false -Dtests=false -Dvulkan=disabled -Dd3d11=disabled \
    -Dopengl=enabled -Dglslang=disabled -Dshaderc=enabled \
    -Dlcms=disabled -Dlibdovi=disabled -Dxxhash=disabled
  meson compile -C "$WORK/libplacebo-build" -j "$JOBS"
  meson install -C "$WORK/libplacebo-build"
  cp "$WORK/libplacebo/LICENSE" "$PREFIX/licenses/libplacebo-LGPL.txt"
  printf '%s\n' "$LIBPLACEBO_RECIPE" > "$PREFIX/.recipe-libplacebo"
fi

if [[ "$need_mpv" == true ]]; then
  echo "Building mpv 0.42.0..."
  fetch_source https://codeload.github.com/mpv-player/mpv/tar.gz/b0841e8c4d156b8403dc1876c4cbd9011eb583f3 \
    a4a79fa6ca728762ac407734b0f93dbcbba250c3bb3bf97b77a342042be802e4 mpv
  meson setup "$WORK/mpv-build" "$WORK/mpv" --prefix "$PREFIX" --libdir lib \
    --buildtype release --wrap-mode nodownload -Dauto_features=disabled \
    -Dgpl=false -Dcplayer=false -Dlibmpv=true -Dplain-gl=enabled -Dalsa=enabled -Dpulse=enabled
  meson compile -C "$WORK/mpv-build" -j "$JOBS"
  meson install -C "$WORK/mpv-build"
  cp "$WORK/mpv/LICENSE.LGPL" "$PREFIX/licenses/mpv-LGPL.txt"
  printf '%s\n' "$MPV_RECIPE" > "$PREFIX/.recipe-mpv"
fi

rm -f "$PREFIX/.recipe"
echo "Media prefix is ready: $PREFIX"
