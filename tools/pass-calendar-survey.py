#!/usr/bin/env python3
"""Describe what Disney's Annual Pass calendar feed actually returns, before the app reads it.

    python3 tools/pass-calendar-survey.py              # prints a short structural summary
    python3 tools/pass-calendar-survey.py --dump DIR   # also saves the raw responses

Runs anywhere with Python 3 and no extra packages — a Mac's built-in python3 is enough.
It does not need the Bazzite box, Gradle, or the app.

Why this exists: the calendar's blockout dates and Good-to-Go days come from
`disneyworld.disney.go.com/passes/blockout-dates/api/get-calendars/?months=13`, the feed
behind Disney's public per-pass calendar pages. Its address was found through a search
engine's index; its **shape** was not, and cannot be seen from the Claude Code container,
which cannot reach Disney at all. Guessing that shape would be the worst kind of failure:
a parser that reads plausibly and tells a friend on a Pixie Dust pass they are blocked on
a day they are not. So the first request is a survey, the same way resorts began.

What it prints, for each feed it asks:
  - the HTTP status, and whether Disney answered with JSON or with a bot-check page
  - the structure: every key path, its type, how many items a list holds, one sample value
  - which pass names (disney-incredi-pass, …) appear, and where
  - how many ISO dates (2026-10-02) appear, and the earliest and latest
  - any keys or values mentioning block, good, go, avail or reserv

The summary is short enough to paste into a chat. Nothing identifying is sent or printed:
two plain GET requests, no cookies, no account.

It asks two feeds: the pass calendar (the one the app needs), and the reservation
availability calendar with the passholder segment (a possible later feature — which parks
still have reservations on a day that is not Good-to-Go).
"""

import argparse
import datetime
import json
import re
import sys
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

PASS_CALENDAR = "https://disneyworld.disney.go.com/passes/blockout-dates/api/get-calendars/?months=13"
AVAILABILITY = "https://disneyworld.disney.go.com/availability-calendar/api/calendar"

# The same browser identity the app's menu requests use; Disney answers anything else with
# a bot-check page. See CLAUDE.md, Restaurant menus.
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/137.0.0.0 Mobile Safari/537.36",
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}

PASS_SLUGS = ["disney-incredi-pass", "disney-sorcerer-pass", "disney-pirate-pass", "disney-pixie-dust-pass"]
KEYWORDS = re.compile(r"block|good|go|avail|reserv|pass|park", re.I)
ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}")
MAX_PATHS = 60


def fetch(url):
    request = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except Exception as error:  # noqa: BLE001 - a survey reports, it does not raise
        return None, f"{type(error).__name__}: {error}"


def walk(node, path, paths, dates, slugs, hits):
    """Collect one line per distinct key path (list indices collapsed to [])."""
    kind = type(node).__name__
    if isinstance(node, dict):
        paths.setdefault(path or "(root)", ["object", 0, f"keys: {', '.join(list(node)[:12])}"])
        paths[path or "(root)"][1] += 1
        for key, value in node.items():
            if key in PASS_SLUGS:
                slugs[key].append(f"{path}.{key}  (as a key)")
            if KEYWORDS.search(key):
                hits.add(f"{path}.{key}")
            if ISO_DATE.match(key):
                dates.append(key[:10])
            walk(value, f"{path}.{key}" if not ISO_DATE.match(key) else f"{path}.<date>", paths, dates, slugs, hits)
    elif isinstance(node, list):
        # The list itself sits at its own path; its items at path[]. Sharing one line made a
        # single two-item list read as "x3".
        entry = paths.setdefault(path or "(root)", ["list", 0, 0])
        entry[1] += 1
        entry[2] = max(entry[2], len(node))
        for item in node:
            walk(item, path + "[]", paths, dates, slugs, hits)
    else:
        text = "" if node is None else str(node)
        entry = paths.setdefault(path, [kind, 0, ""])
        entry[1] += 1
        if not entry[2]:
            entry[2] = f"e.g. {text[:60]!r}"
        if isinstance(node, str):
            if ISO_DATE.match(text):
                dates.append(text[:10])
            if text in PASS_SLUGS:
                slugs[text].append(f"{path}  (as a value)")
            if KEYWORDS.search(text) and len(text) < 40:
                hits.add(f"{path} = {text!r}")


def summarize(name, status, body):
    print(f"\n=== {name} ===")
    print(f"HTTP {status}, {len(body)} bytes")
    stripped = body.lstrip()
    if status is None:
        print(f"could not connect: {body}")
        return
    if stripped.startswith("<"):
        print("Disney answered with a WEB PAGE, not data — most likely its bot check.")
        print("first 200 characters:", stripped[:200].replace("\n", " "))
        return
    try:
        data = json.loads(body)
    except json.JSONDecodeError as error:
        print(f"not JSON ({error}); first 200 characters: {stripped[:200]!r}")
        return

    paths, dates, hits = {}, [], set()
    slugs = {slug: [] for slug in PASS_SLUGS}
    walk(data, "", paths, dates, slugs, hits)

    print(f"\nstructure ({len(paths)} distinct paths{', first ' + str(MAX_PATHS) if len(paths) > MAX_PATHS else ''}):")
    for path, (kind, count, sample) in list(paths.items())[:MAX_PATHS]:
        detail = f"longest {sample}" if kind == "list" else sample
        print(f"  {path:55} {kind:6} x{count:<5} {detail}")

    print("\npass names found:")
    for slug, where in slugs.items():
        print(f"  {slug:24} {len(where)} times" + (f", first at {where[0]}" if where else ""))

    if dates:
        print(f"\nISO dates: {len(dates)} ({len(set(dates))} distinct), {min(dates)} .. {max(dates)}")
    else:
        print("\nISO dates: none found — dates may be in another format; see sample values above")

    if hits:
        print("\nkeys/values mentioning block/good/go/avail/reserv/pass/park (first 25):")
        for hit in sorted(hits)[:25]:
            print(f"  {hit}")

    counts = Counter(v for v in _string_values(data) if len(v) < 25)
    common = [f"{v!r}x{n}" for v, n in counts.most_common(12) if n > 3]
    if common:
        print("\nmost repeated short values (often the status words):", ", ".join(common))


def _string_values(node):
    if isinstance(node, dict):
        for value in node.values():
            yield from _string_values(value)
    elif isinstance(node, list):
        for item in node:
            yield from _string_values(item)
    elif isinstance(node, str):
        yield node


def main():
    parser = argparse.ArgumentParser(description="Survey Disney's Annual Pass calendar feed.")
    parser.add_argument("--dump", metavar="DIR", help="also save the raw responses here")
    args = parser.parse_args()

    today = datetime.date.today()
    availability_url = (f"{AVAILABILITY}?segment=passholder&startDate={today}"
                        f"&endDate={today + datetime.timedelta(days=30)}")

    for name, url in [("pass calendar (blockouts + Good-to-Go)", PASS_CALENDAR),
                      ("reservation availability, passholder segment", availability_url)]:
        status, body = fetch(url)
        summarize(name, status, body)
        if args.dump:
            out = Path(args.dump)
            out.mkdir(parents=True, exist_ok=True)
            filename = "pass-calendar.json" if url == PASS_CALENDAR else "availability.json"
            (out / filename).write_text(body)
            print(f"\nraw response saved to {out / filename}")

    print("\nDone. Paste everything above into the Claude Code session.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
