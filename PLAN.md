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

### Phase 1 — the core loop — next
- [ ] **Park detail screen** — rides sorted by wait, shows with showtimes, dining. Filter
      chips for open/closed and by land. This is the biggest remaining gap.
- [ ] **Parking** — the record-a-spot flow. Repository and Room table already exist;
      needs the UI and a dashboard pin. Free-form lot + row on purpose: Disney rows are
      "Heroes 12", Universal's are "Jaws, Level 4", Epic is different again.
- [ ] **Weather on the park screen** — current, feels-like, and the next few hours of rain
      chance, which is the thing that decides whether to bring a poncho.
- [ ] Settings: dynamic colour toggle, temperature units, which parks to show.

### Phase 2 — depth
- [ ] Wait-time history chart per ride, from the samples already being recorded.
- [ ] Disney's hourly `forecast` array as a "best time to ride today" hint (Disney-only —
      Universal sends no forecast).
- [ ] Lightning Lane pricing and availability from the schedule `purchases` array.
- [ ] Hand-off to the official apps: deep-link to Disney Parks / Universal FL for anything
      that needs a real account — mobile order, ticket scanning, virtual queues. Manifest
      `<queries>` entries are already in place.
- [ ] Nearest-ride / "which park am I in" using the lat/long every entity carries.

### Phase 3 — nice to have
- [ ] Home screen widget (Glance): current park crowd + parking spot.
- [ ] Notify when a watched ride drops below a wait threshold.
- [ ] Trip history — what was ridden, and when.
- [ ] Resorts, if it ever seems worth it.

## Open questions
- **Epic Universe baselines are provisional.** The park opened May 2025 and has not settled
  into a normal year, so its seeds are the least trustworthy in the table and should be the
  first replaced by recorded history.
- **Universal dining has no live status.** Worth showing the `/children` list anyway, or
  leave Universal dining out until upstream covers it?
