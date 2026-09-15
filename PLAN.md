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

### Phase 2 — depth (complete apart from Lightning Lane)
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
      **Verified live 2026-09-15**: Magic Kingdom's schedule carries purchases on 31 of 78
      entries — per-attraction Single Pass with an `available` flag and a formatted price,
      plus Multi Pass and Premier Pass packages. `PurchaseDto` is already written and
      parsed, and nothing reads it, so this is domain and UI work only.
- [x] **Hand-off to the official apps** — an action on every park screen, plus an explicit
      card on the Dining tab where mobile order actually lives. Launches the installed app,
      or its Play Store listing. Only the launcher entry point is used: both apps surely
      have internal deep links, but none are documented, and an undocumented scheme that
      silently breaks is worse than one extra tap.
- [x] **"Which park am I in"** — `Geo.parkAt()` matches a GPS fix against park centres and
      pins that park to the top of the dashboard (shipped in v0.3.0). Centres are crude:
      USF and IOA share a wall and sit under 1km apart, so a poor fix near that boundary can
      pick the wrong one. A test pins the distance so the tightness stays visible.
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
