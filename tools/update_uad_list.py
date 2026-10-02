#!/usr/bin/env python3
"""Fetches UAD-ng's community package list into app/src/main/assets/uad_lists.json.

The upstream file has a few duplicate keys (same package listed twice). Parsers disagree
on which copy wins, so duplicates are merged here: the first entry's ratings are kept and
the descriptions of all copies are joined.

Usage: tools/update_uad_list.py [path-or-url]
"""
import json
import sys
import urllib.request
from pathlib import Path

URL = ("https://raw.githubusercontent.com/Universal-Debloater-Alliance/"
       "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json")
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/uad_lists.json"


def main() -> None:
    src = sys.argv[1] if len(sys.argv) > 1 else URL
    if src.startswith("http"):
        with urllib.request.urlopen(src) as r:
            text = r.read().decode("utf-8")
    else:
        text = Path(src).read_text(encoding="utf-8")

    merged: dict = {}
    dupes = []
    for pkg, entry in json.loads(text, object_pairs_hook=lambda pairs: pairs):
        entry = dict(entry)
        if pkg in merged:
            dupes.append(pkg)
            first = merged[pkg]
            extra = entry.get("description", "").strip()
            if extra and extra not in first.get("description", ""):
                first["description"] = first.get("description", "").rstrip() + "\n\n" + extra
        else:
            merged[pkg] = entry

    OUT.write_text(json.dumps(merged, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(merged)} packages written to {OUT.relative_to(Path.cwd()) if OUT.is_relative_to(Path.cwd()) else OUT}")
    if dupes:
        print(f"merged duplicates: {', '.join(dupes)}")


if __name__ == "__main__":
    main()
