#!/usr/bin/env python3
"""Find out what themeparks.wiki actually has for resort hotels, before anything is built.

    python3 tools/resort-survey.py
    python3 tools/resort-survey.py --dump /tmp/resort-dump    # also save the raw JSON

Phase 4's "Resorts" is the only item on the plan with no verified data behind it. Every
other table in this app was measured first: the lands survey found the wrong answer on its
first pass and had to be redone, and the park boundaries replaced a centre-distance
heuristic only after it was measured at 80.7% for Universal Studios. Hardcoding thirty
hotel UUIDs on the assumption they exist would fail the same way a stale park id fails —
the entry quietly drops out and nothing says why. So: measure, then build.

This answers six questions, and prints a verdict on each:

  1. Does either destination's `/children` carry HOTEL entities at all?
  2. Do those hotels have coordinates? (Nothing location-shaped works without them.)
  3. What else is in the destination feed? The app already downloads the whole Walt Disney
     World list for Disney Springs and throws most of it away — this says what is in there.
  4. How many restaurants sit OUTSIDE the Disney Springs boundary? Those are the candidates
     for resort dining, and they cost nothing new to fetch.
  5. Does a hotel's own `/children` return its restaurants? If it does, the feature is
     clean. If it does not, the fallback is matching restaurants to hotels by coordinate,
     which is messier and worth knowing about in advance.
  6. Do hotels answer `/live` and `/schedule`? That decides whether there is any status or
     opening hours to show, or whether a resort screen is a name and a dining list.

themeparks.wiki is volunteer-run, so this is deliberately gentle: it fetches two
destination lists, then probes at most PROBE_LIMIT hotels, with a pause between requests.
It is a survey you run once, not something to put on a loop.

No Gradle plugin and no build dependency — like check-versions.py, it cannot break the
build and works even when the build is broken. Python 3 and network access, nothing else.
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from osm_geometry import contains, meters_per_degree_lon  # noqa: E402

BASE = "https://api.themeparks.wiki/v1"
USER_AGENT = "Parks-app/dev (one-off data survey; github.com/Zatara214/Parks)"

# The two destinations by name, so no UUID is guessed. Walt Disney World's id is already
# hardcoded in ParksRepository; Universal's is not known here and is looked up.
WANTED_DESTINATIONS = ["Walt Disney World", "Universal Orlando"]

# Gentle on a volunteer service. Raising these is not worth whatever it buys.
PROBE_LIMIT = 3
PAUSE_SECONDS = 1.0

BOUNDARIES = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/kotlin/contact/kaufman/parks/domain/ParkBoundaries.kt"
)


def get(path):
    """One GET, or None with the reason printed. A survey that dies on the first 404 tells
    you less than one that carries on and reports what it could not reach."""
    url = f"{BASE}{path}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        print(f"    ! {path} -> HTTP {error.code}")
    except Exception as error:  # noqa: BLE001 - a survey reports, it does not raise
        print(f"    ! {path} -> {error}")
    return None


def disney_springs_rings():
    """The mapped Disney Springs outline, read from the generated table rather than
    refetched, so this survey and the app agree about where it is."""
    if not BOUNDARIES.exists():
        return []
    pattern = r'park = ([^,\n]+),\s*osmName = "([^"]+)",\s*ring = doubleArrayOf\(([^)]*)\)'
    rings = []
    for match in re.finditer(pattern, BOUNDARIES.read_text()):
        if match.group(1).strip() != "Park.DISNEY_SPRINGS":
            continue
        values = [float(v) for v in match.group(3).split(",")]
        rings.append(list(zip(values[0::2], values[1::2])))
    return rings


def meters_between(a, b):
    lat0 = (a[0] + b[0]) / 2
    dy = (a[0] - b[0]) * 111_320.0
    dx = (a[1] - b[1]) * meters_per_degree_lon(lat0)
    return (dx * dx + dy * dy) ** 0.5


def coords_of(entity):
    location = entity.get("location") or {}
    lat, lon = location.get("latitude"), location.get("longitude")
    return (lat, lon) if lat is not None and lon is not None else None


def survey_destination(name, destination_id, springs, dump_dir):
    print(f"\n=== {name} ===")
    print(f"    id {destination_id}")

    payload = get(f"/entity/{destination_id}/children")
    if payload is None:
        print("    could not read the destination's children — nothing else to say")
        return
    children = payload.get("children", [])
    if dump_dir:
        (dump_dir / f"{name.replace(' ', '-').lower()}-children.json").write_text(
            json.dumps(payload, indent=1)
        )

    # Q3: what is actually in here.
    kinds = {}
    for child in children:
        kinds[child.get("entityType") or "(none)"] = kinds.get(child.get("entityType") or "(none)", 0) + 1
    print(f"\n    {len(children)} children, by entityType:")
    for kind, count in sorted(kinds.items(), key=lambda kv: -kv[1]):
        print(f"      {count:5d}  {kind}")

    # Q1 and Q2: hotels, and whether they are placeable.
    hotels = [c for c in children if c.get("entityType") == "HOTEL"]
    print(f"\n    Q1  HOTEL entities: {len(hotels)}")
    if not hotels:
        print("        -> no hotels in this feed. A resorts feature cannot come from here;")
        print("           it would need OSM for the places and nothing for the contents.")
    else:
        placed = [h for h in hotels if coords_of(h)]
        print(f"    Q2  with coordinates: {len(placed)} of {len(hotels)}")
        for hotel in sorted(hotels, key=lambda h: h.get("name", ""))[:40]:
            mark = " " if coords_of(hotel) else "?"
            print(f"      {mark} {hotel.get('name')}  [{hotel.get('id')}]")
        if len(hotels) > 40:
            print(f"        ... and {len(hotels) - 40} more")

    # Q4: restaurants the app already downloads and discards.
    restaurants = [c for c in children if c.get("entityType") == "RESTAURANT"]
    with_coords = [r for r in restaurants if coords_of(r)]
    inside = [r for r in with_coords if springs and contains(coords_of(r), springs)]
    print(f"\n    Q4  RESTAURANT entities: {len(restaurants)}"
          f" ({len(with_coords)} placed, {len(inside)} inside Disney Springs)")
    outside = [r for r in with_coords if r not in inside]
    noun = "restaurant" if len(outside) == 1 else "restaurants"
    print(f"        {len(outside)} placed {noun} outside Disney Springs —")
    print(f"        already fetched every day, and currently discarded.")

    # How close do those sit to a hotel? If resort dining is really in here, most of the
    # leftovers should land within a few hundred metres of one.
    if hotels and outside:
        placed_hotels = [(h, coords_of(h)) for h in hotels if coords_of(h)]
        if placed_hotels:
            near = 0
            for restaurant in outside:
                point = coords_of(restaurant)
                best = min(meters_between(point, hc) for _, hc in placed_hotels)
                if best <= 400:
                    near += 1
            verb = "sits" if near == 1 else "sit"
            print(f"        {near} of them {verb} within 400 m of a hotel"
                  f" ({100 * near // max(len(outside), 1)}%).")

    # Q5 and Q6: what a hotel itself answers.
    for hotel in hotels[:PROBE_LIMIT]:
        hotel_id, hotel_name = hotel.get("id"), hotel.get("name")
        print(f"\n    probing {hotel_name}")
        time.sleep(PAUSE_SECONDS)
        kids = get(f"/entity/{hotel_id}/children")
        if kids is not None:
            kid_list = kids.get("children", [])
            kinds = {}
            for k in kid_list:
                kinds[k.get("entityType")] = kinds.get(k.get("entityType"), 0) + 1
            print(f"    Q5  /children -> {len(kid_list)}: {kinds or 'nothing'}")
            if dump_dir:
                (dump_dir / f"hotel-{hotel_id}-children.json").write_text(json.dumps(kids, indent=1))

        time.sleep(PAUSE_SECONDS)
        live = get(f"/entity/{hotel_id}/live")
        if live is not None:
            rows = live.get("liveData", [])
            print(f"    Q6  /live -> {len(rows)} rows")
            if dump_dir:
                (dump_dir / f"hotel-{hotel_id}-live.json").write_text(json.dumps(live, indent=1))

        time.sleep(PAUSE_SECONDS)
        schedule = get(f"/entity/{hotel_id}/schedule")
        if schedule is not None:
            rows = schedule.get("schedule", [])
            print(f"    Q6  /schedule -> {len(rows)} rows")
            if dump_dir:
                (dump_dir / f"hotel-{hotel_id}-schedule.json").write_text(json.dumps(schedule, indent=1))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dump",
        metavar="DIR",
        help="also write the raw JSON here, so it can be read (or sent on) afterwards",
    )
    args = parser.parse_args()

    dump_dir = None
    if args.dump:
        dump_dir = Path(args.dump)
        dump_dir.mkdir(parents=True, exist_ok=True)
        print(f"raw JSON -> {dump_dir}")

    springs = disney_springs_rings()
    if springs:
        print(f"Disney Springs boundary loaded ({sum(len(r) for r in springs)} points)")
    else:
        print("! no Disney Springs boundary found; Q4 will not split inside from outside")

    destinations = get("/destinations")
    if destinations is None:
        print("\ncould not reach api.themeparks.wiki — nothing surveyed", file=sys.stderr)
        return 1
    if dump_dir:
        (dump_dir / "destinations.json").write_text(json.dumps(destinations, indent=1))

    found = {}
    for destination in destinations.get("destinations", []):
        for wanted in WANTED_DESTINATIONS:
            if wanted.lower() in (destination.get("name") or "").lower():
                found[wanted] = destination

    missing = [w for w in WANTED_DESTINATIONS if w not in found]
    if missing:
        print(f"! not found in /destinations: {', '.join(missing)}", file=sys.stderr)

    for wanted in WANTED_DESTINATIONS:
        destination = found.get(wanted)
        if destination:
            survey_destination(destination.get("name"), destination.get("id"), springs, dump_dir)

    print("\ndone. The question this answers is whether resorts are a real feature or a")
    print("list of names — see the plan's Resorts section for what each verdict implies.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
