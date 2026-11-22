#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${1:-}"
OUTPUT_DIR="${2:-out}"
BUILD_NUMBER="${GITHUB_RUN_NUMBER:-1}"

if [[ -z "$VERSION" ]]; then
  VERSION="$(sed -n 's/^versionName=//p' "$PROJECT_ROOT/version.properties" | head -n 1)"
fi

if [[ ! "$VERSION" =~ ^([0-9]+\.[0-9]+\.[0-9]+|dev-[0-9]+)$ || ! "$BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "Error: invalid package version or build number" >&2
  exit 1
fi

echo "=== Building SimpleTV Linux Packages (Version: $VERSION) ==="

DIST_DIR="$PROJECT_ROOT/desktop/build/linux/x64/release/bundle"
mkdir -p "$OUTPUT_DIR"

SYMBOLS_DIR="$PROJECT_ROOT/desktop/build/release-symbols/linux/$VERSION-$BUILD_NUMBER"
BUILD_ARGS=(--obfuscate "--split-debug-info=$SYMBOLS_DIR" --build-number "$BUILD_NUMBER")
if [[ "$VERSION" != dev-* ]]; then
  BUILD_ARGS+=(--build-name "$VERSION")
fi
echo "Building Flutter Linux application in release mode..."
MEDIA_PREFIX="$PROJECT_ROOT/build/linux-media"
bash "$PROJECT_ROOT/packaging/linux/build-linux-mpv.sh" "$MEDIA_PREFIX"
(
  cd "$PROJECT_ROOT/desktop"
  export PKG_CONFIG_PATH="$MEDIA_PREFIX/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
  export LD_LIBRARY_PATH="$MEDIA_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  flutter build linux --release "${BUILD_ARGS[@]}"
)

if ! compgen -G "$SYMBOLS_DIR/*.symbols" > /dev/null; then
  echo "Error: Flutter did not produce split debug information" >&2
  exit 1
fi

if [ ! -d "$DIST_DIR" ]; then
  echo "Error: Distributable directory not found: $DIST_DIR"
  exit 1
fi

if [[ -n "$(find "$DIST_DIR" -type f \( -iname '*.symbols' -o -iname '*.debug' -o -iname '*.map' \) -print -quit)" ]]; then
  echo "Error: debug symbols or maps found inside the Linux distribution" >&2
  exit 1
fi

dart "$PROJECT_ROOT/desktop/tool/pack_release_playlists.dart" "$DIST_DIR/data/flutter_assets"
strip --strip-unneeded "$DIST_DIR/SimpleTV"

# Package the media dependency closure, leaving the system loader, GTK and
# graphics drivers to the host. Both release formats use this same bundle.
dependencies=$(LD_LIBRARY_PATH="$MEDIA_PREFIX/lib" ldd "$MEDIA_PREFIX/lib/libmpv.so.2")
if [[ "$dependencies" == *"not found"* ]]; then
  printf '%s\n' "$dependencies" >&2
  exit 1
fi
cp -L "$MEDIA_PREFIX/lib/libmpv.so.2" "$DIST_DIR/lib/libmpv.so.2"
while read -r soname source; do
  case "$soname" in
    libc.so.*|libm.so.*|libpthread.so.*|libdl.so.*|librt.so.*|libgcc_s.so.*|libstdc++.so.*|\
    libGL*|libEGL*|libgbm*|libdrm*|libvulkan*|libX*|libxcb*|libwayland*|\
    libglib*|libgio*|libgobject*|libgmodule*|libpango*|libcairo*|libfontconfig*|libfreetype*|libharfbuzz*|\
    libasound*|libpulse*|libsystemd*|libdbus*) continue ;;
  esac
  cp -L "$source" "$DIST_DIR/lib/$soname"
  patchelf --set-rpath '$ORIGIN' "$DIST_DIR/lib/$soname"
done < <(awk '$2 == "=>" && $3 ~ /^\// { print $1, $3 }' <<< "$dependencies")
patchelf --set-rpath '$ORIGIN' "$DIST_DIR/lib/libmpv.so.2"
patchelf --set-rpath '$ORIGIN' "$DIST_DIR/lib/libmedia_kit_video_plugin.so"
cp -a "$MEDIA_PREFIX/licenses" "$DIST_DIR/licenses"

# 1. Create Standalone Binary Archive (.tar.gz)
echo "[1/3] Creating standalone binary archive (.tar.gz)..."
TARBALL="$OUTPUT_DIR/SimpleTV-${VERSION}-linux-x64.tar.gz"
TEMP_TAR_DIR="$PROJECT_ROOT/build/SimpleTV-tar"
rm -rf "$TEMP_TAR_DIR"
mkdir -p "$TEMP_TAR_DIR"
cp -r "$DIST_DIR" "$TEMP_TAR_DIR/SimpleTV"
tar -czvf "$TARBALL" -C "$TEMP_TAR_DIR" SimpleTV
rm -rf "$TEMP_TAR_DIR"
echo "Binary archive created: $TARBALL"

# 2. Prepare AppDir for AppImage
echo "[2/3] Preparing AppDir structure..."
APP_DIR="$PROJECT_ROOT/build/SimpleTV.AppDir"
rm -rf "$APP_DIR"
mkdir -p "$APP_DIR/usr/bin" \
         "$APP_DIR/usr/share/applications" \
         "$APP_DIR/usr/share/icons/hicolor/256x256/apps"

# Preserve the same binary, data and library layout as the tarball.
cp -a "$DIST_DIR/." "$APP_DIR/usr/bin/"

# Copy Icon
ICON_SRC="$PROJECT_ROOT/packaging/icon.png"
if [ -f "$ICON_SRC" ]; then
  cp "$ICON_SRC" "$APP_DIR/simpletv.png"
  cp "$ICON_SRC" "$APP_DIR/.DirIcon"
  cp "$ICON_SRC" "$APP_DIR/usr/share/icons/hicolor/256x256/apps/simpletv.png"
fi

# Create Desktop entry
cat << 'EOF' > "$APP_DIR/SimpleTV.desktop"
[Desktop Entry]
Type=Application
Name=SimpleTV
GenericName=IPTV Player
Comment=IPTV player with archive, EPG and multi-source streaming
Exec=SimpleTV %U
Icon=simpletv
Terminal=false
Categories=AudioVideo;Video;Player;TV;
MimeType=application/x-mpegurl;audio/x-mpegurl;application/vnd.apple.mpegurl;video/mp2t;
StartupWMClass=io.mediacast.simpletv
EOF
cp "$APP_DIR/SimpleTV.desktop" "$APP_DIR/usr/share/applications/simpletv.desktop"

# Create AppRun entrypoint
cat << 'EOF' > "$APP_DIR/AppRun"
#!/bin/bash
HERE="$(dirname "$(readlink -f "${0}")")"
export PATH="${HERE}/usr/bin:${PATH}"

if [ -f "${HERE}/usr/bin/SimpleTV" ]; then
  exec "${HERE}/usr/bin/SimpleTV" "$@"
else
  echo "Error: SimpleTV executable not found in AppDir" >&2
  exit 1
fi
EOF
chmod +x "$APP_DIR/AppRun"

# 3. Compile AppImage using appimagetool
echo "[3/3] Compiling AppImage..."
APPIMAGE_TOOL="$PROJECT_ROOT/build/appimagetool"
if [ ! -f "$APPIMAGE_TOOL" ]; then
  mkdir -p "$PROJECT_ROOT/build"
  echo "Downloading appimagetool..."
  curl -sSL -o "$APPIMAGE_TOOL" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
  chmod +x "$APPIMAGE_TOOL"
fi

APPIMAGE_OUT="$OUTPUT_DIR/SimpleTV-${VERSION}-x86_64.AppImage"
ARCH=x86_64 "$APPIMAGE_TOOL" --appimage-extract-and-run "$APP_DIR" "$APPIMAGE_OUT"
chmod +x "$APPIMAGE_OUT"

echo "AppImage created successfully: $APPIMAGE_OUT"
ls -la "$OUTPUT_DIR"
