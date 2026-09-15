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

### Phase 2 — depth — **complete**
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
- Lightning Lane **moved to Phase 4** (Zak, 2026-09-15): he is a local and hardly ever
      buys them, so the value does not match its position. The research is kept there so
      nobody redoes it.
- [x] **Hand-off to the official apps** — an action on every park screen, plus an explicit
      card on the Dining tab where mobile order actually lives. Launches the installed app,
      or its Play Store listing. Only the launcher entry point is used: both apps surely
      have internal deep links, but none are documented, and an undocumented scheme that
      silently breaks is worse than one extra tap.
- [x] **"Which park am I in"** — shipped in v0.3.0 against park centres, **rebuilt on real
      mapped boundaries 2026-09-15** (`domain/ParkBoundaries.kt`, generated by
      `tools/park-boundaries.py`). The centre test was worse than it looked: measured
      against the footprints it placed only **80.7% of Universal Studios** in the right
      park, and reported **100% of CityWalk** as being inside a park. USF and Islands of
      Adventure share a wall, so no arrangement of centres separates them.
      CityWalk is now an explicit "not a park" shape rather than a gap between polygons.
      A ride-position heuristic was measured first and rejected: good at which-park
      (99.7% USF, 96.7% IOA) but it cannot exclude CityWalk, which comes within 31m of a
      ride while in-park points reach 285m from one — no distance cap separates those.
      Also corrected three wrong coordinates in `domain/Parks.kt`; **Epic Universe's was
      3.6km out, near SeaWorld**, so Epic was undetectable and Universal's weather point
      was dragged a kilometre south.
- [x] **Nearest ride** — a third "Nearby" sort on the Rides tab, ordering by straight-line
      distance from one fix and showing each ride's distance while that order is active.
      The chip **only appears when the fix is inside that park** (`Geo.parkAt`): measured
      from home it would order every ride by a gradient pointing at the front gate, which
      looks like a working feature and is not one. Rides with no coordinates sink to the
      bottom, as rides with no posted wait already do. Verified on the emulator from
      Cinderella Castle — Main Street Vehicles 320 ft, the Railroad 420 ft, Laugh Floor
      530 ft, ascending correctly — and confirmed absent from downtown Orlando.

### Phase 2.5 — parking by geofence (Zak's request, 2026-09-14) — **done 2026-09-15**
Opening the parking screen now takes a single fix and fills in what it can, leaving the row
to type. `domain/ParkingAreas.kt` holds the polygons, `Geo.ringContains` the ray casting.

- **OpenStreetMap had the lots already**, so none had to be traced by hand. Disney maps
  every guest lot individually and by name — all 12 at Magic Kingdom, all 8 at EPCOT, the
  4 Animal Kingdom lots — and the names match the signage the app already lists. Outlines
  were simplified to about 5m (1,700 points down to 316), which is finer than the GPS fix
  testing against them. ODbL, so the credit is in NOTICE.md and on the settings screen.
- **Universal's two garages are 426m apart, not on top of each other** — the worry recorded
  here was wrong. They are cleanly separable, and OSM tags them `Structure North` (5 levels)
  and `Structure South` (6 levels), which matches the signage.
- **But the garages cannot tell you the park**, which is the real Universal limit and a
  different one than expected: USF and Islands of Adventure are reached from the same two
  structures. Those areas carry a garage name and a null park, and the screen asks
  "You're in the South Garage — which park?" rather than guessing.
- **A geofence cannot give you the garage level**, as expected — the number encodes it
  ("Cat in the Hat 457" = level 4, row 57) and GPS has no vertical resolution inside a
  deck. Level stays a manual pick, as does the row everywhere.
- Verified on the emulator, both cases: a fix in Magic Kingdom's Ursula lot selected the
  park and the section with no taps, and a fix in the South Garage asked which park.
  `adb emu geo fix` works as long as the app is holding an open GPS request — see the
  Location section of `CLAUDE.md`.

Not mapped in OSM, so they prefill nothing: Hollywood Studios' **BB-8** lot, and four of
Epic Universe's five sections (**Monster**, **Viking**, **Gamer**, **Hero**). Epic's two
big lots are mapped unnamed, so a fix there still fills in the park. These are worth adding
to OSM upstream rather than hand-tracing into the app — the table is generated from a
query, so an upstream fix flows straight in.

### Phase 3 — trip history (active, Zak's pick 2026-09-15)
What was ridden, and when. **Local storage is the default and must stay fully functional
on its own**; Dawarich is an optional enrichment for the one person in a thousand who runs
one, and the feature cannot depend on it.

#### The problem, and why Dawarich changes it
Parks **never tracks location in the background** — that is a deliberate, load-bearing
decision, not an oversight. It takes single fixes when a screen asks. So on its own the app
can only know what it directly witnessed: a park screen opened, a parking spot recorded, a
refresh performed. That is a thin trip history.

Zak self-hosts **Dawarich**, which already records his location continuously via the Colota
app. That is precisely the background tracking Parks refuses to do — except it is on his own
server, collected with his own consent, for his own reasons. Reading it lets the app
reconstruct a real trip without becoming a tracker itself.

#### Recommended shape: Dawarich as a *source*, not a store
- Trip history lives in **local Room tables**. Dawarich is read, never written.
- Reasons: Dawarich's model is points, visits and places — there is nowhere natural to put
  "queued 38 minutes for Space Mountain", and inventing custom visits would pollute a
  location history that exists for other purposes. The local store has to exist anyway for
  the default case, so making Dawarich a second backing store means two write paths and a
  sync question for no gain. Read-only also means an **API key that only needs read scope**,
  and no possibility of this app corrupting his location archive.

#### API facts, read from the Dawarich source 2026-09-15 (Freika/dawarich)
- `GET /api/v1/points` — auth by `api_key` query parameter **or** `Authorization: Bearer`.
- Parameters: `start_at`, `end_at` (unix seconds), `order` (`asc`/`desc`), `page`,
  `per_page` (default 100, max 10,000), and a **bounding box**:
  `min_latitude`, `max_latitude`, `min_longitude`, `max_longitude`.
- `slim=true` returns exactly what is needed and little else:
  `{id, latitude, longitude, timestamp, velocity, country_name, tracker_id}` — coordinates
  as strings, timestamp as unix seconds. Paging is reported in `X-Current-Page` and
  `X-Total-Pages` headers.
- **The bounding box is the important one.** Parks can ask only for points inside a park's
  own bbox for a single day, so it never requests, receives or stores where Zak was the
  rest of the time. Privacy-minimal by construction rather than by promise.
- `GET /api/v1/visits` also exists (`name, status, latitude, longitude, started_at,
  ended_at`) — Dawarich's own visit detection. Place-level, so useful for "was I at Magic
  Kingdom that day" but too coarse to name a ride. Points plus our own dwell detection is
  what gets ride-level detail.
- Self-hosted instances are exempt from Dawarich's rate limiting, but this should still
  fetch **on request per day viewed**, not on a schedule — consistent with the rest of the app.

#### Turning points into a trip — the machinery already exists
- `ParkBoundaries` says which park a point is in, and excludes CityWalk.
- Every attraction carries a lat/long, and `distanceMetersFrom` already measures to it.
- **The app's own `wait_samples` are the trick for confidence.** A dwell near a ride only
  proves you stood there — eating nearby looks the same. But if the dwell length is close to
  the standby wait posted at that hour, you almost certainly queued. Parks already records
  those samples every refresh, so it can distinguish "near Space Mountain" from "rode
  Space Mountain" without guessing.

#### Known limits, to be stated in the UI rather than papered over
- A dwell is not a ride. Where confidence is low the entry should say "near", not "rode".
- Attraction coordinates are single points, not queue polygons. Queues run hundreds of
  metres and an entrance can sit well away from the marker.
- GPS is poor in a switchback queue surrounded by steel, and worse indoors — the exact
  places this feature cares about.
- Colota's sampling interval sets the floor on everything. Sparse points mean short rides
  vanish entirely.

#### Steps
- [x] **Local trip history — done 2026-09-15.** `park_sightings` (park id + timestamp, no
      coordinates), written where a fix already resolves to a park, debounced to one row per
      park per 10 minutes. `groupSightings` builds per-day visits on the shared 4AM
      rollover. A Trips screen off the dashboard top bar, with a per-day Forget and an
      empty state that explains what fills it. Room migration 1 → 2, verified by installing
      over the previous build and confirming the parking record survived.
      One rule was reversed by testing: visits are **not** split on gaps. A three-hour
      threshold turned one realistic day at Magic Kingdom into three visits, and no
      threshold works, because the app cannot tell a pocket from a drive home.
- [ ] Settings: optional Dawarich base URL and API key, off by default, with an explicit
      "test connection" so a typo fails visibly rather than silently.
- [ ] Dawarich client: one day, one park bbox, `slim=true`, paged.
- [ ] Dwell detection, then the wait-sample cross-check for confidence.
- [ ] Let it be wrong gracefully — everything editable or deletable by hand.

### Phase 4 — nice to have
- [ ] Home screen widget (Glance): current park crowd + parking spot.
- [ ] Notify when a watched ride drops below a wait threshold.
- [ ] **Lightning Lane pricing and availability** from the schedule `purchases` array.
      Demoted from Phase 2 on 2026-09-15: Zak is a local and rarely buys them.
      **Verified live that day**: Magic Kingdom's schedule carries purchases on 31 of 78
      entries — per-attraction Single Pass with an `available` flag and a formatted price,
      plus Multi Pass ($23) and Premier Pass ($299) packages. `PurchaseDto` is already
      written and parsed and nothing reads it, so this is domain and UI work only.
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
