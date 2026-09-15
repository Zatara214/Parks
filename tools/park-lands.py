#!/usr/bin/env python3
"""Regenerate app/src/main/kotlin/.../domain/ParkLands.kt from OpenStreetMap.

    python3 tools/park-lands.py

themeparks.wiki does not say which land a ride is in — `/children` carries names and
coordinates and nothing else. OSM maps the lands as `place=locality` areas inside each
park, so the land can be derived from the coordinate every ride already has.

Coverage is uneven and that is the point of the report this prints. Measured 2026-09-15
against the live attraction lists, 147 of 158 rides place into a land — 93%. Magic Kingdom,
Hollywood Studios, Epic Universe and Islands of Adventure are at 100%; EPCOT 97%; Animal
Kingdom 73%, because Discovery Island is not mapped as a locality; and Universal Studios
79%, the misses being back-lot spaces that belong to no themed land — the Hogwarts Express
platforms and the Halloween Horror Nights houses.

Read relations, not just ways. A first pass at this survey only looked at each element's
own `geometry`, which relations do not have, and so concluded Islands of Adventure had no
lands at all. It has eight.

Re-run this after a park re-themes an area. Piston Peak is mapped as
`landuse=construction` today and will want picking up once it opens.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from osm_geometry import area_m2, contains, fetch_query, rings_of, simplify  # noqa: E402

# Everything inside this box; each locality is then assigned to whichever park's mapped
# boundary contains it, so the query does not need a box per park.
SEARCH_BOX = (28.33, -81.62, 28.49, -81.43)

BOUNDARIES = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/kotlin/contact/kaufman/parks/domain/ParkBoundaries.kt"
)
OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/kotlin/contact/kaufman/parks/domain/ParkLands.kt"
)

# Lands are small, and a ride sits well inside one, so this can be looser than the park
# boundaries without any risk of putting a ride in the wrong land.
SIMPLIFY_TOLERANCE_M = 10.0

# OSM names that are not lands. Kept as an explicit list rather than an area threshold:
# Pixar Place is a real land whose polygon is only 0.12 ha, so anything size-based would
# throw it away along with the noise.
NOT_A_LAND = {
    "Tumble Weed",              # a few market stalls in Frontierland
    "Caribe Bazaar",            # likewise, in Adventureland
    "Closed for construction",  # a state, not a place
}

# OSM typos, fixed on the way through rather than in OSM, so a regeneration cannot silently
# reintroduce them. Worth correcting upstream too.
RENAME = {
    "Commissionary Lane": "Commissary Lane",
    "Grand Avenure": "Grand Avenue",
}


def load_park_boundaries():
    """Read the generated boundary table rather than re-fetching it — it is the same data,
    and reusing it guarantees the two tables agree about where a park is."""
    import re

    text = BOUNDARIES.read_text()
    parks = {}
    pattern = r'park = ([^,\n]+),\s*osmName = "([^"]+)",\s*ring = doubleArrayOf\(([^)]*)\)'
    for match in re.finditer(pattern, text):
        park = match.group(1).strip()
        if park == "null":
            continue
        values = [float(v) for v in match.group(3).split(",")]
        parks.setdefault(park.removeprefix("Park."), []).append(
            list(zip(values[0::2], values[1::2]))
        )
    return parks


def main():
    parks = load_park_boundaries()
    if not parks:
        print("could not read park boundaries; run park-boundaries.py first", file=sys.stderr)
        return 1

    south, west, north, east = SEARCH_BOX
    box = f"{south},{west},{north},{east}"
    query = (
        f'[out:json][timeout:180];\n(\n'
        f'  way["place"="locality"]["name"]({box});\n'
        f'  relation["place"="locality"]["name"]({box});\n'
        f');\nout geom;'
    )
    elements = fetch_query(query)

    lands = []
    for element in elements:
        tags = element.get("tags", {})
        name = tags.get("name")
        if not name or name in NOT_A_LAND:
            continue
        rings = [r for r in rings_of(element) if len(r) >= 4]
        if not rings:
            continue
        biggest = max(rings, key=area_m2)
        centre = (
            sum(p[0] for p in biggest) / len(biggest),
            sum(p[1] for p in biggest) / len(biggest),
        )
        owner = next((p for p, r in parks.items() if contains(centre, r)), None)
        if owner is None:
            continue  # a locality somewhere else in Orlando
        simplified = [simplify(r, SIMPLIFY_TOLERANCE_M) for r in rings]
        simplified = [s for s in simplified if len(s) >= 3 and area_m2(s) > 500]
        if not simplified:
            continue
        lands.append((owner, RENAME.get(name, name), simplified))

    by_park = {}
    for park, name, rings in lands:
        by_park.setdefault(park, []).append((name, rings))

    print(f"{'park':28} lands")
    for park in sorted(by_park):
        names = sorted(n for n, _ in by_park[park])
        print(f"  {park:26} {len(names):2}  {', '.join(names)}")
    missing = sorted(set(parks) - set(by_park))
    for park in missing:
        print(f"  {park:26}  0  (no mapped lands — the app offers no filter there)")

    lines = []
    for park in sorted(by_park):
        for name, rings in sorted(by_park[park], key=lambda item: item[0]):
            for ring in rings:
                coords = ", ".join(f"{lat:.6f}, {lon:.6f}" for lat, lon in ring)
                lines.append(
                    "        Land(\n"
                    f"            park = Park.{park},\n"
                    f'            name = "{name}",\n'
                    f"            ring = doubleArrayOf({coords}),\n"
                    "        ),"
                )

    total = sum(len(r) for _, _, rings in lands for r in rings)
    OUTPUT.write_text(HEADER.format(count=total) + "\n".join(lines) + FOOTER)
    print(f"\nWrote {OUTPUT.name}: {len(lines)} rings, {total} points")
    return 0


HEADER = '''package contact.kaufman.parks.domain

/**
 * The named lands inside each park.
 *
 * **Generated — do not hand-edit.** Run `python3 tools/park-lands.py` to refresh.
 *
 * themeparks.wiki does not carry a land for an attraction; `/children` gives a name and a
 * coordinate and nothing else. OSM maps lands as `place=locality` areas, so the land is
 * derived from the coordinate every ride already has, the same trick the parking and park
 * boundaries use.
 *
 * **Coverage is uneven, and the UI has to tolerate that.** Measured against the live
 * attraction lists on 2026-09-15, 147 of 158 rides place into a land — 93% overall:
 *
 * - Magic Kingdom, Hollywood Studios, Epic Universe, Islands of Adventure: **100%**
 * - EPCOT: 97% — one World Showcase pavilion falls just outside its simplified outline
 * - Animal Kingdom: 73% — Discovery Island is not mapped, so the Tree of Life and its
 *   trails have no land
 * - Universal Studios: 79% — the misses are real back-lot spaces rather than a mapping
 *   gap: the Hogwarts Express platforms, and the Halloween Horror Nights houses, which
 *   sit in no themed land at all
 *
 * So a park screen must handle rides with no land, and [landsIn] returning an empty list
 * for a park with none — the app shows no filter at all rather than an empty one, exactly
 * as Universal's missing forecast leaves those rows unexpandable.
 *
 * EPCOT's two localities are "Future World" and "World Showcase" — the pre-2021 naming.
 * That is what OSM has, and it is still the split most people navigate by, but it is not
 * what the signs say any more. Worth fixing upstream in OSM rather than hand-patching here.
 */
object ParkLands {{

    data class Land(
        val park: Park,
        val name: String,
        val ring: DoubleArray,
    ) {{
        // DoubleArray gives a data class identity equals/hashCode, which is wrong for a
        // value type — compare the contents instead.
        override fun equals(other: Any?): Boolean = this === other ||
            (other is Land && park == other.park && name == other.name &&
                ring.contentEquals(other.ring))

        override fun hashCode(): Int = 31 * (31 * park.hashCode() + name.hashCode()) +
            ring.contentHashCode()
    }}

    /** The distinct land names in a park, in the order the table lists them. Empty when
     *  nothing is mapped, which is the signal to offer no filter at all. */
    fun landsIn(park: Park): List<String> =
        all.filter {{ it.park == park }}.map {{ it.name }}.distinct()

    /**
     * Which land a position is in, or null when it is in none of them.
     *
     * The **smallest** containing land wins. Lands nest in places — Storybook Circus sits
     * inside Fantasyland's outline — and the tighter answer is the more useful one.
     */
    fun landAt(park: Park, latitude: Double, longitude: Double): String? = all
        .asSequence()
        .filter {{ it.park == park && Geo.ringContains(it.ring, latitude, longitude) }}
        .minByOrNull {{ ringArea(it.ring) }}
        ?.name

    /** Shoelace area in square degrees — only ever compared against another ring nearby,
     *  so it needs no metre conversion to pick the smaller of two. */
    private fun ringArea(ring: DoubleArray): Double {{
        var total = 0.0
        var j = ring.size - 2
        for (i in ring.indices step 2) {{
            total += ring[j] * ring[i + 1] - ring[i] * ring[j + 1]
            j = i
        }}
        return kotlin.math.abs(total) / 2
    }}

    /** {count} points across every land. */
    val all: List<Land> = listOf(
'''

FOOTER = """    )
}
"""

if __name__ == "__main__":
    sys.exit(main())
