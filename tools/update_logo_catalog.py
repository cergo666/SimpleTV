"""Refresh the shared fallback logo catalog from iptv-org/database."""

import csv
import io
import json
from collections import defaultdict
from pathlib import Path
import re
from urllib.parse import urlsplit
from urllib.request import urlopen


BASE = "https://raw.githubusercontent.com/iptv-org/database/master/data/"
OUTPUT = Path(__file__).resolve().parents[1] / "desktop/assets/catalog/logos.json"


def rows(name):
    with urlopen(BASE + name, timeout=45) as response:
        return list(csv.DictReader(io.TextIOWrapper(response, encoding="utf-8-sig")))


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
