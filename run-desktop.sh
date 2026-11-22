#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/desktop"
exec flutter run -d linux
