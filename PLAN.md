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

### Phase 2 — depth
- [x] **"Best time to ride today"** — tapping a Disney ride expands an hourly forecast bar
      chart with the quietest hour still ahead called out. Universal sends no forecast, so
      those rows are not expandable at all rather than opening an empty chart.
      Advice is withheld below a 10-minute saving, and when the queue is already shorter
      than anything forecast.
- [ ] Wait-time history chart per ride, from the samples already being recorded.
- [ ] Lightning Lane pricing and availability from the schedule `purchases` array.
- [x] **Hand-off to the official apps** — an action on every park screen, plus an explicit
      card on the Dining tab where mobile order actually lives. Launches the installed app,
      or its Play Store listing. Only the launcher entry point is used: both apps surely
      have internal deep links, but none are documented, and an undocumented scheme that
      silently breaks is worse than one extra tap.
- [ ] Nearest-ride / "which park am I in" using the lat/long every entity carries.

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
