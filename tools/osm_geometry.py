"""Shared OpenStreetMap plumbing for the generators in this directory.

Fetching with mirror fallback, multipolygon stitching, Douglas-Peucker simplification,
area, and point-in-polygon. One copy, used by both `park-boundaries.py` and
`park-lands.py`, so the geometry cannot drift apart between them.

The generators import this with a plain `import osm_geometry` after adding this directory
to `sys.path` — their own filenames contain hyphens and so cannot be imported directly.
"""

import json
import math
import sys
import time
import urllib.parse
import urllib.request

# Several mirrors, because the main one returns 504 under load often enough to be annoying
# for scripts whose whole point is being re-runnable.
OVERPASS_MIRRORS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.osm.jp/api/interpreter",
]
USER_AGENT = "Parks-app/dev (map data regeneration; github.com/Zatara214/Parks)"
ATTEMPTS_PER_MIRROR = 2

METERS_PER_DEGREE_LAT = 111_320.0


def meters_per_degree_lon(lat):
    return METERS_PER_DEGREE_LAT * math.cos(math.radians(lat))


def fetch_query(query):
    """POST a raw Overpass QL query, trying each mirror in turn."""
    payload = urllib.parse.urlencode({"data": query}).encode()
    last = None
    for mirror in OVERPASS_MIRRORS:
        for attempt in range(1, ATTEMPTS_PER_MIRROR + 1):
            try:
                request = urllib.request.Request(
                    mirror, data=payload, headers={"User-Agent": USER_AGENT}
                )
                with urllib.request.urlopen(request, timeout=240) as response:
                    return json.load(response)["elements"]
            except Exception as error:  # noqa: BLE001 — any failure means try elsewhere
                last = error
                print(f"  {mirror} attempt {attempt}: {error}", file=sys.stderr)
                time.sleep(5 * attempt)
    raise SystemExit(f"every Overpass mirror failed; last error: {last}")


def fetch(elements):
    """Fetch specific elements by id, as (kind, id) pairs."""
    ways = [str(i) for kind, i in elements if kind == "way"]
    rels = [str(i) for kind, i in elements if kind == "relation"]
    parts = []
    if ways:
        parts.append(f"way(id:{','.join(ways)});")
    if rels:
        parts.append(f"relation(id:{','.join(rels)});")
    return fetch_query(f"[out:json][timeout:180];\n({chr(10).join(parts)}\n);\nout geom;")


def stitch(members):
    """Join a multipolygon's outer member ways into closed rings by matching endpoints."""
    segments = [
        [(g["lat"], g["lon"]) for g in m["geometry"]]
        for m in members
        if m.get("role") == "outer" and m.get("geometry")
    ]
    rings, pending = [], list(segments)
    while pending:
        current = pending.pop(0)
        joined = True
        while joined and current[0] != current[-1]:
            joined = False
            for i, segment in enumerate(pending):
                if segment[0] == current[-1]:
                    current = current + segment[1:]
                elif segment[-1] == current[-1]:
                    current = current + segment[::-1][1:]
                elif segment[-1] == current[0]:
                    current = segment[:-1] + current
                elif segment[0] == current[0]:
                    current = segment[::-1][:-1] + current
                else:
                    continue
                pending.pop(i)
                joined = True
                break
        rings.append(current)
    return rings


def perpendicular_m(point, a, b, lat0):
    scale = meters_per_degree_lon(lat0)
    px, py = (point[1] - a[1]) * scale, (point[0] - a[0]) * METERS_PER_DEGREE_LAT
    bx, by = (b[1] - a[1]) * scale, (b[0] - a[0]) * METERS_PER_DEGREE_LAT
    length_squared = bx * bx + by * by
    if length_squared == 0:
        return math.hypot(px, py)
    t = max(0.0, min(1.0, (px * bx + py * by) / length_squared))
    return math.hypot(px - t * bx, py - t * by)


def douglas_peucker(points, tolerance, lat0):
    if len(points) < 3:
        return points
    worst, index = 0.0, 0
    for i in range(1, len(points) - 1):
        d = perpendicular_m(points[i], points[0], points[-1], lat0)
        if d > worst:
            worst, index = d, i
    if worst <= tolerance:
        return [points[0], points[-1]]
    left = douglas_peucker(points[: index + 1], tolerance, lat0)
    right = douglas_peucker(points[index:], tolerance, lat0)
    return left[:-1] + right


def simplify(ring, tolerance):
    if ring[0] == ring[-1]:
        ring = ring[:-1]
    lat0 = sum(p[0] for p in ring) / len(ring)
    # Split the closed ring in half first: Douglas-Peucker anchors its two endpoints, and
    # on a closed loop those coincide, which collapses the whole shape to nothing.
    half = len(ring) // 2
    first = douglas_peucker(ring[: half + 1], tolerance, lat0)
    second = douglas_peucker(ring[half:] + [ring[0]], tolerance, lat0)
    return first[:-1] + second[:-1]


def area_m2(ring):
    lat0 = sum(p[0] for p in ring) / len(ring)
    scale = meters_per_degree_lon(lat0)
    total = 0.0
    for i in range(len(ring)):
        x1, y1 = ring[i][1] * scale, ring[i][0] * METERS_PER_DEGREE_LAT
        j = (i + 1) % len(ring)
        x2, y2 = ring[j][1] * scale, ring[j][0] * METERS_PER_DEGREE_LAT
        total += x1 * y2 - x2 * y1
    return abs(total) / 2


def contains(point, rings):
    for ring in rings:
        x, y = point[1], point[0]
        inside = False
        for i in range(len(ring)):
            x1, y1 = ring[i][1], ring[i][0]
            x2, y2 = ring[i - 1][1], ring[i - 1][0]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                inside = not inside
        if inside:
            return True
    return False


def rings_of(element):
    """Closed rings for a way or a relation, without caring which it is."""
    if element["type"] == "way":
        return [[(g["lat"], g["lon"]) for g in element["geometry"]]]
    return stitch(element["members"])
