#!/usr/bin/env bash
set -euo pipefail

apt-get update -qq
apt-get install -y -qq --no-install-recommends \
  ca-certificates libgtk-3-0t64 libegl1 libgles2 libgl1 libgl1-mesa-dri \
  libasound2t64 libpulse0 xvfb xauth openbox wmctrl python3
cd "$(mktemp -d)"
tar -xzf "/packages/SimpleTV-$STV_VERSION-linux-x64.tar.gz"
appimage="/packages/SimpleTV-$STV_VERSION-x86_64.AppImage"
"$appimage" --appimage-extract >/dev/null

for bundle in SimpleTV squashfs-root/usr/bin; do
  dependencies=$(ldd "$bundle/SimpleTV")
  if [[ "$dependencies" == *"not found"* ]]; then
    printf '%s\n' "$dependencies" >&2
    exit 1
  fi
  grep -F 'libmpv.so.2 => ' <<< "$dependencies" | grep -F "$bundle/lib/libmpv.so.2"
  grep -F 'libavcodec.so.62 => ' <<< "$dependencies" | grep -F "$bundle/lib/libavcodec.so.62"
done

python3 /checks/smoke-libmpv.py SimpleTV/lib/libmpv.so.2

check_launch() {
  local status=0
  LIBGL_ALWAYS_SOFTWARE=1 SIMPLETV_MPV_DIAGNOSTICS=1 timeout 30s xvfb-run -a bash -c '
    set -eu
    openbox &
    wm=$!
    trap "kill $wm 2>/dev/null || true" EXIT
    "$@" &
    app=$!
    for _ in $(seq 1 80); do
      if grep -Eq "Linux video renderer: (OpenGL|Software)" launch.log && wmctrl -c SimpleTV; then
        wait "$app"
        exit
      fi
      kill -0 "$app"
      sleep .25
    done
    kill "$app"
    exit 1
  ' bash "$@" > launch.log 2>&1 || status=$?
  cat launch.log
  if [[ "$status" != 0 ]] || ! grep -Fq 'player disposed before closing the window' launch.log; then
    echo "Launch smoke test failed: $* ($status)" >&2
    exit 1
  fi
}

check_launch ./SimpleTV/SimpleTV
check_launch "$appimage" --appimage-extract-and-run
