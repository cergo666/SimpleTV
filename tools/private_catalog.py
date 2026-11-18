"""Seal or restore the private source adapters and built-in playlist snapshots."""

import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import zlib

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "private/catalog-files.json"
ARCHIVE = ROOT / "private/catalog.enc"
HEADER = b"SimpleTV private catalog v1\n"
KEY_FILE = Path.home() / ".simpletv-private/catalog.key"


def read_key():
    value = os.environ.get("SIMPLETV_CATALOG_KEY")
    if not value and KEY_FILE.is_file():
        value = KEY_FILE.read_text(encoding="ascii").strip()
    if not value:
        raise ValueError("Set SIMPLETV_CATALOG_KEY or provide ~/.simpletv-private/catalog.key")
    key = base64.b64decode(value, validate=True)
    if len(key) != 32:
        raise ValueError("Catalog key must contain 32 bytes encoded as base64")
    return key


def paths():
    names = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if not names or len(set(names)) != len(names):
        raise ValueError("Invalid private file manifest")
    for name in names:
        target = (ROOT / name).resolve()
        if not target.is_relative_to(ROOT) or target == ROOT:
            raise ValueError("Private path escapes the repository")
    return names


def seal():
    key = read_key()
    payload = {
        name: base64.b64encode((ROOT / name).read_bytes()).decode("ascii")
        for name in paths()
    }
    compressed = zlib.compress(json.dumps(payload).encode("utf-8"), level=9)
    nonce = secrets.token_bytes(12)
    encrypted = HEADER + nonce + AESGCM(key).encrypt(nonce, compressed, HEADER)
    temporary = ARCHIVE.with_suffix(".tmp")
    temporary.write_bytes(encrypted)
    temporary.replace(ARCHIVE)


def restore(check=False):
    data = ARCHIVE.read_bytes()
    if not data.startswith(HEADER):
        raise ValueError("Unsupported private catalog format")
    nonce = data[len(HEADER):len(HEADER) + 12]
    ciphertext = data[len(HEADER) + 12:]
    payload = json.loads(zlib.decompress(AESGCM(read_key()).decrypt(nonce, ciphertext, HEADER)))
    if set(payload) != set(paths()):
        raise ValueError("Private catalog does not match its manifest")
    decoded = {name: base64.b64decode(value, validate=True) for name, value in payload.items()}
    for name, value in decoded.items():
        target = ROOT / name
        if check:
            if target.read_bytes() != value:
                raise ValueError(f"Private file differs from sealed snapshot: {name}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_suffix(target.suffix + ".private-tmp")
            temporary.write_bytes(value)
            temporary.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["seal", "restore", "check"])
    args = parser.parse_args()
    try:
        if args.command == "seal":
            seal()
        else:
            restore(check=args.command == "check")
    except Exception as error:
        raise SystemExit(f"Private catalog operation failed ({type(error).__name__}). Check the key and bundle.") from None
    print(f"Private catalog {args.command} completed ({len(paths())} files).")


if __name__ == "__main__":
    main()
