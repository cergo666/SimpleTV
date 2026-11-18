#!/usr/bin/env bash
set -e
cd -- "$(dirname -- "$0")/desktop"
exec flutter run -d linux
