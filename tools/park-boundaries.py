#!/usr/bin/env python3
"""Regenerate app/src/main/kotlin/.../domain/ParkBoundaries.kt from OpenStreetMap.

Run this whenever a park's footprint changes. Magic Kingdom is expanding, so this will be
needed — that is the entire reason this script is in the repo rather than being a thing
somebody did once by hand:

    python3 tools/park-boundaries.py

It fetches by OSM element id, so the shapes it returns are the same ones every time unless
an editor has actually changed them. If a park is re-mapped under a new element (a
relation replacing a way, say), update PARKS below; the script fails loudly rather than
silently emitting a short table.

Requires only Python 3 and network access. OSM data is ODbL — see NOTICE.md.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from osm_geometry import area_m2, contains, fetch, rings_of, simplify  # noqa: E402

# Several mirrors, because the main one returns 504 under load often enough to be annoying
# for a script whose whole point is being re-runnable.
# OSM element ids, checked 2026-09-15. `centre` is the coordinate Park.kt declares, used
# only to sanity-check that the shape we fetched is the park we meant.
PARKS = [
    ("MAGIC_KINGDOM", "way", 297428432, "Magic Kingdom", (28.4189, -81.5810)),
    ("EPCOT", "way", 805298160, "EPCOT", (28.3720, -81.5490)),
    ("HOLLYWOOD_STUDIOS", "way", 806728401, "Disney's Hollywood Studios", (28.3576, -81.5592)),
    ("ANIMAL_KINGDOM", "way", 297441457, "Disney's Animal Kingdom", (28.3582, -81.5909)),
    ("UNIVERSAL_STUDIOS_FLORIDA", "way", 449808332, "Universal Studios Florida", (28.4757, -81.4686)),
    ("ISLANDS_OF_ADVENTURE", "relation", 1124085, "Universal Islands of Adventure", (28.4718, -81.4698)),
    ("EPIC_UNIVERSE", "way", 1372221500, "Universal Epic Universe", (28.4425, -81.4484)),
    # Not a theme park — tagged tourism=attraction, landuse=retail — but the app treats it
    # as a place you can be in, so it needs a footprint like the rest.
    ("DISNEY_SPRINGS", "relation", 7932667, "Disney Springs", (28.3709, -81.5177)),
]

# Not a park, and the reason this file exists at all: CityWalk abuts both Universal parks,
# so without it a fix there resolves to whichever park is nearer and the app claims you are
# inside a park you are standing outside of.
NOT_A_PARK = [
    (None, "way", 449651135, "Universal CityWalk Orlando", None),
]

# Metres. The GPS fix is good to 5-10 m, so finer than this is false precision. Chosen to
# keep the whole table a few hundred points rather than a few thousand.
SIMPLIFY_TOLERANCE_M = 8.0

OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "app/src/main/kotlin/contact/kaufman/parks/domain/ParkBoundaries.kt"
)


def main():
    wanted = PARKS + NOT_A_PARK
    fetched = fetch([(kind, ident) for _, kind, ident, _, _ in wanted])
    by_id = {(e["type"], e["id"]): e for e in fetched}

    shapes, failures = {}, []
    for park, kind, ident, name, centre in wanted:
        element = by_id.get((kind, ident))
        if element is None:
            failures.append(f"{name}: OSM {kind} {ident} was not returned")
            continue
        got = element.get("tags", {}).get("name")
        if got != name:
            failures.append(f"{kind} {ident} is now named {got!r}, expected {name!r}")
            continue
        rings = rings_of(element)
        simplified = [
            simplify(r, SIMPLIFY_TOLERANCE_M) for r in rings if len(r) >= 4
        ]
        simplified = [s for s in simplified if len(s) >= 3 and area_m2(s) > 2_000]
        if not simplified:
            failures.append(f"{name}: nothing usable survived simplification")
            continue
        if centre and not contains(centre, simplified):
            failures.append(
                f"{name}: the centre Parks.kt declares ({centre[0]}, {centre[1]}) is not "
                f"inside the shape fetched — one of the two is wrong"
            )
        shapes[name] = (park, simplified)
        raw = sum(len(r) for r in rings)
        new = sum(len(s) for s in simplified)
        hectares = sum(area_m2(s) for s in simplified) / 1e4
        print(f"  {name:34} {raw:4d} -> {new:3d} pts  {hectares:6.1f} ha")

    # CityWalk has to stay disjoint from both parks or the exclusion is meaningless.
    citywalk = shapes.get("Universal CityWalk Orlando")
    if citywalk:
        for name, (_, rings) in shapes.items():
            if name == "Universal CityWalk Orlando":
                continue
            bleed = sum(1 for r in citywalk[1] for p in r if contains(p, rings))
            # Shared boundary nodes are expected; a real overlap is not.
            if bleed > len(citywalk[1][0]) // 4:
                failures.append(f"CityWalk overlaps {name} ({bleed} vertices inside it)")

    if failures:
        print("\nFAILED — not writing the table:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1

    lines = []
    for name, (park, rings) in sorted(shapes.items(), key=lambda kv: kv[1][0] or "zz"):
        for ring in rings:
            coords = ", ".join(f"{lat:.6f}, {lon:.6f}" for lat, lon in ring)
            lines.append(
                "        Boundary(\n"
                f"            park = {f'Park.{park}' if park else 'null'},\n"
                f'            osmName = "{name}",\n'
                f"            ring = doubleArrayOf({coords}),\n"
                "        ),"
            )

    total = sum(len(r) for _, rings in shapes.values() for r in rings)
    OUTPUT.write_text(HEADER.format(count=total) + "\n".join(lines) + FOOTER)
    print(f"\nWrote {OUTPUT.relative_to(Path.cwd()) if OUTPUT.is_relative_to(Path.cwd()) else OUTPUT}")
    print(f"{len(lines)} boundaries, {total} points")
    return 0


HEADER = '''package contact.kaufman.parks.domain

/**
 * Where each park actually is, as a boundary rather than a centre and a radius.
 *
 * **Generated — do not hand-edit.** Run `python3 tools/park-boundaries.py` to refresh from
 * OpenStreetMap; the script carries the element ids and fails loudly if a park has been
 * re-mapped. Magic Kingdom is expanding, so this table will need regenerating: a park that
 * grows outside its old shape simply stops being detected in the new bit, which is a
 * staleness bug and not a mystery.
 *
 * Traced from OpenStreetMap (© OpenStreetMap contributors, ODbL — see NOTICE.md) and
 * simplified to about 8 metres, which is finer than the GPS fix tested against it.
 *
 * This replaced a nearest-park-centre test with a 1,200m radius. That was worse than it
 * looked: measured against these boundaries it placed only **80.7%** of Universal Studios
 * in the right park, and claimed **100%** of CityWalk was inside a park.
 *
 * CityWalk is in the table with a **null [park]** for exactly that reason. It abuts both
 * Universal parks, so it has to be an explicit answer of "not a park" rather than a gap
 * between two polygons.
 */
object ParkBoundaries {{

    /** A mapped footprint. [park] is null for a place that is deliberately *not* a park. */
    data class Boundary(
        val park: Park?,
        /** The OpenStreetMap name, so a shape can be traced back to its source. */
        val osmName: String,
        val ring: DoubleArray,
    ) {{
        // DoubleArray gives a data class identity equals/hashCode, which is wrong for a
        // value type — compare the contents instead.
        override fun equals(other: Any?): Boolean = this === other ||
            (other is Boundary && osmName == other.osmName && ring.contentEquals(other.ring))

        override fun hashCode(): Int = 31 * osmName.hashCode() + ring.contentHashCode()
    }}

    /**
     * The footprint a position is in, or null when it is in none of them.
     *
     * Order matters. A **not-a-park** shape is tested first, so standing in CityWalk
     * answers CityWalk even though it touches two parks. Exact containment is tried next.
     * Only then does [EXPANSION_TOLERANCE_METERS] come into play, and only when a single
     * park is in range — near the wall USF and Islands of Adventure share, both qualify
     * and the honest answer is neither.
     */
    fun at(latitude: Double, longitude: Double): Boundary? {{
        notAPark.firstOrNull {{ Geo.ringContains(it.ring, latitude, longitude) }}
            ?.let {{ return it }}
        parks.firstOrNull {{ Geo.ringContains(it.ring, latitude, longitude) }}
            ?.let {{ return it }}
        return parks
            .filter {{ Geo.distanceToRingMeters(it.ring, latitude, longitude) <= EXPANSION_TOLERANCE_METERS }}
            .singleOrNull()
    }}

    /** The park a position is in, or null when it is not in one — CityWalk included. */
    fun parkAt(latitude: Double, longitude: Double): Park? = at(latitude, longitude)?.park

    /**
     * How far outside a boundary still counts as being in that park.
     *
     * Two jobs. It absorbs a fix that has drifted a few metres past a fence, and it gives
     * this table a little slack when a park grows before anyone regenerates it. Kept modest
     * because the shapes abut real places that are not the park: go much wider and a hotel
     * walkway starts reporting as being inside.
     */
    const val EXPANSION_TOLERANCE_METERS = 150.0

    val parks: List<Boundary> get() = all.filter {{ it.park != null }}

    val notAPark: List<Boundary> get() = all.filter {{ it.park == null }}

    /** {count} points across every shape. */
    val all: List<Boundary> = listOf(
'''

FOOTER = """    )
}
"""

if __name__ == "__main__":
    sys.exit(main())
