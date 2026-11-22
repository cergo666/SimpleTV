"""Refresh the shared fallback logo catalog from iptv-org/database."""

import csv
import io
import json
from collections import defaultdict
from pathlib import Path
import re
from urllib.parse import urlsplit
from urllib.request import urlopen


PINNED_COMMIT = "3b84328ec46b6ca04d750d4722f7be524318b94a"
BASE = f"https://raw.githubusercontent.com/iptv-org/database/{PINNED_COMMIT}/data/"
OUTPUT = Path(__file__).resolve().parents[1] / "desktop/assets/catalog/logos.json"
REQUIRED_FIELDS = {
    "channels.csv": {"id", "name", "alt_names", "country"},
    "logos.csv": {"channel", "in_use", "feed", "format", "url"},
}


def rows(name):
    with urlopen(BASE + name, timeout=45) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status for {name}: {response.status}")
        reader = csv.DictReader(io.TextIOWrapper(response, encoding="utf-8-sig"))
        missing = REQUIRED_FIELDS.get(name, set()) - set(reader.fieldnames or ())
        if missing:
            raise RuntimeError(f"Unexpected columns in {name}: missing {sorted(missing)}")
        return list(reader)


def channel_key(name):
    name = re.sub(r"[^a-zа-я0-9+]", " ", name.lower().replace("ё", "е"))
    name = re.sub(r"(?<=\S)hd$", " hd", name)
    name = re.sub(r"(?<=[a-zа-я])(?=\d)|(?<=\d)(?=[a-zа-я])", " ", name)
    return " ".join(name.split())


def main():
    channels = {row["id"]: row for row in rows("channels.csv") if row["country"] == "RU"}
    logos = {}
    for row in rows("logos.csv"):
        if row["channel"] not in channels or row["in_use"] != "TRUE" or row["feed"]:
            continue
        url = urlsplit(row["url"])
        if row["format"] not in {"PNG", "WEBP", "JPEG"} or url.scheme != "https" or not url.hostname:
            continue
        logos.setdefault(row["channel"], row["url"])

    candidates = defaultdict(set)
    for channel_id, url in logos.items():
        channel = channels[channel_id]
        for name in [channel["name"], *channel["alt_names"].split(";")]:
            if key := channel_key(name):
                candidates[key].add(url)

    catalog = {key: urls.pop() for key, urls in candidates.items() if len(urls) == 1}
    if len(catalog) < 300 or "amedia premium" not in catalog:
        raise RuntimeError("Upstream logo catalog is unexpectedly incomplete")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    print(f"Generated {len(catalog)} fallback logo entries")


if __name__ == "__main__":
    main()
