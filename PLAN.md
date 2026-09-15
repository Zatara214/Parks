# Parks — plan

## Decisions made (2026-09-13)

| Question | Decision | Why |
| --- | --- | --- |
| Home screen | Today dashboard, all seven parks | The question asked from the couch in Celebration is "should I go, and where?" |
| Crowds | Derived locally, WDW Passport's published method | No third-party crowd API exists — see below |
| Crowd baseline | Shipped seed, self-correcting from recorded history | Gives "vs. usual" on day one, converges on truth within a season |
| Weather | Open-Meteo, on demand | Free, keyless, no tracking; Zak has a dedicated weather app so this is a glance, not a feature |
| Repo | Public, GPLv3, `Zatara214/Parks` | Matches the Grassfed workflow |
| Networking | Ktor + kotlinx.serialization | Two plain-GET JSON hosts; matches Grassfed |
| Navigation | Navigation 3 | Matches LubeLogger, the newer of the two projects |

### Why crowds are computed rather than fetched
Checked on 2026-09-13:
- **Thrill Data** — no public API (a developer's forum request went unanswered), and the
  site returns **403 to any programmatic request**.
- **TouringPlans** — crowd calendar is behind a $14.95/yr subscription, no developer API.
- **Queue-Times** — free and keyless, but wait times only, no crowd index, and using it
  obligates a "Powered by Queue-Times.com" badge.
- **wdwpassport.com** — Laravel Livewire, CSRF-locked internal endpoints; `/past-crowds`
  sits behind a JS/cookie challenge.
- **wdwstats.com** — plain server-rendered HTML, no API.

WDW Passport does publish its **method**, though, and that is reproducible from
themeparks.wiki. See the crowd model section of `CLAUDE.md`.

## Status

### Phase 0 — scaffold — **done**
- Gradle 9.7.1 / AGP 9.4 / Kotlin 2.4.20, compose-bom-alpha 2026.09.00 (material3 1.5.0-alpha28).
- Hilt, Room (schema v1, exported), DataStore, Ktor, Navigation 3, Coil.
- themeparks.wiki + Open-Meteo clients, full DTO coverage for live/children/schedule.
- Crowd model + seed table + self-correcting baselines, 7 unit tests.
- Today dashboard with real live data, verified on-device.
- GPLv3, GitHub Actions build + signed tagged releases.

### Phase 1 — the core loop — done
- [x] **Navigation 3** — dashboard → park detail → parking, with predictive-back handled
      by `predictivePopTransitionSpec` (the plain pop spec does not drive the gesture).
- [x] **Park detail screen** — Rides/Shows/Dining tabs, sort by wait or name, "open only"
      filter. Rides with no posted wait sort to the bottom rather than as a zero.
- [x] **Parking** — record-a-spot flow with a pinned dashboard card and history. Verified
      end to end on-device, survives a restart.
- [x] **Weather on the park screen** — behind a "Check" tap, never fetched automatically.
      Shows feels-like (the number that matters in Orlando) and the next hours of rain chance.
- [x] **Settings** — dynamic color, light/dark/system, temperature unit, and which parks
      appear on the dashboard. Plus an About screen with the generated licence list.
      Hiding every park is refused: it would leave a blank dashboard with no way back.
- [ ] Filter rides by land — needs land data, which `/children` does not carry. Would have
      to be derived from each ride's lat/long.

- [x] **Parking resets overnight** — a spot retires at 4AM park time, not midnight: hard
      ticket nights run to midnight and Extended Evening to 11PM, so a car parked at 9PM is
      still parked at 12:30AM. Evaluated on read rather than scheduled, so there is no
      background job and it stays correct if the phone was off at 4AM.

**Phase 1 complete.**

### Phase 2 — depth (complete apart from nearest-ride)
- [x] **"Best time to ride today"** — tapping a Disney ride expands an hourly forecast bar
      chart with the quietest hour still ahead called out. Universal sends no forecast, so
      those rows are not expandable at all rather than opening an empty chart.
      Advice is withheld below a 10-minute saving, and when the queue is already shorter
      than anything forecast.
- [x] **Wait-time history chart** — an expanded ride shows the app's own recorded midday
      averages beneath Disney's forecast, so measurement and projection sit together but
      read as different things. Days with fewer than three samples are dropped: one glance
      recorded while walking past is not a day's average. Works at Universal too, which has
      no forecast — those rows become expandable once there is history to show.
- [ ] Lightning Lane pricing and availability from the schedule `purchases` array.
- [x] **Hand-off to the official apps** — an action on every park screen, plus an explicit
      card on the Dining tab where mobile order actually lives. Launches the installed app,
      or its Play Store listing. Only the launcher entry point is used: both apps surely
      have internal deep links, but none are documented, and an undocumented scheme that
      silently breaks is worse than one extra tap.
- [x] **"Which park am I in"** — `Geo.parkAt()` matches a GPS fix against park centres and
      pins that park to the top of the dashboard (shipped in v0.3.0). Centres are crude:
      USF and IOA share a wall and sit under 1km apart, so a poor fix near that boundary can
      pick the wrong one. A test pins the distance so the tightness stays visible.
- [ ] Nearest *ride* using the lat/long every entity carries — still to do.

### Phase 2.5 — parking by geofence (Zak's request, 2026-09-14)
Once "which park am I in" works, the same trick should fill in the parking section, leaving
only the row to enter by hand.

- Lots do not move, so hardcoded polygons are fine. Zak has offered to trace them from
  OpenStreetMap if nothing usable exists; worth checking OSM first, since Disney and
  Universal lots are mapped as `amenity=parking` areas and some carry section names already.
- Point-in-polygon is a dozen lines (ray casting), so the work is entirely in sourcing and
  checking the shapes, not the maths.
- **A geofence cannot give you the garage level.** Universal's number encodes level in its
  first digit ("Cat in the Hat 457" = level 4, row 57), and GPS has no usable vertical
  resolution inside a concrete deck. Level stays a manual pick there.
- Section polygons at EPCOT and Magic Kingdom are large and well separated, so this should
  be reliable at those. The tighter question is Universal's two garages, which sit almost
  on top of each other.
- **This is now testable without going to Orlando** (2026-09-15). Simulating a position on
  the emulator was previously believed impossible; `adb emu geo fix` does work, as long as
  it is sent while the app is holding an open GPS request. See the Location section of
  `CLAUDE.md`. That matters most for the Universal garages, which are exactly the case
  worth probing with a few points either side of the boundary.

### Phase 3 — nice to have
- [ ] Home screen widget (Glance): current park crowd + parking spot.
- [ ] Notify when a watched ride drops below a wait threshold.
- [ ] Trip history — what was ridden, and when.
- [ ] Resorts, if it ever seems worth it.

## Known nuances
- A park can read "6 · Above average" while its sub-line says "About usual". These are two
  different aggregations: the 1-10 level is the mean of the *ride ranks* (the WDW Passport
  method), while the "vs usual" line is the mean of the per-ride *ratios*. Both are honest;
  worth revisiting if the pairing reads as contradictory in practice.

## Open questions
- **Epic Universe baselines are provisional.** The park opened May 2025 and has not settled
  into a normal year, so its seeds are the least trustworthy in the table and should be the
  first replaced by recorded history.

## Deferred
- **Universal dining is out for now** (decided 2026-09-13). Universal's `/live` feed omits
  restaurants entirely, so USF/IOA/EU dining could only be a static name-and-location list
  with no "open now". Zak visits Universal much less than Disney, so a half-feature is not
  worth shipping. Revisit once the app is real and a secondary source has been looked for —
  Zak intends to hunt for one himself. Disney dining is unaffected and ships normally.
