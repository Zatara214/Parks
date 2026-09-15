# Parks — notes for Claude

Android client for Walt Disney World and Universal Orlando wait times, hours, crowds and
weather. Kotlin, Compose, Material 3 Expressive, GPLv3.

The owner (Zak, GitHub Zatara214) has no engineering background: explain choices in plain
language and proactively suggest workflow improvements. Roadmap and open decisions live in
`PLAN.md`.

## Start of every session
1. `gh issue list` — Zak files bugs and ideas from his phone as GitHub Issues. Run this
   **on the host**: `gh` is not installed in the build container (see Build & verify).
2. Read `PLAN.md` for the current milestone.

## Build & verify
**The development machine is the Bazzite HTPC** (since 2026-09-14, re-verified 2026-09-15).
It is the only machine that builds. Zak types at the session from his phone or his Mac over
Claude Code remote control, but those are terminals — no toolchain lives on them, and
nothing below should be re-read as advice about macOS.

There was a brief spell in between where an arm64 Mac was the documented machine, and its
notes replaced the Linux ones wholesale. Anything that still smells like macOS advice
(`~/Library/Android/sdk`, `~/.zshenv`, `Pixel_9_Pro`, "no `timeout(1)`") is from that spell
and is wrong here.

### Everything Gradle runs inside a container
Bazzite is an immutable Fedora atomic image, so the toolchain lives in a **distrobox
container named `android-dev`** (Fedora 44). There is **no `java` on the host at all** —
a bare `./gradlew` from the host fails with `java: command not found`, which looks like a
broken project and is not. Wrap every Gradle command:

```
distrobox enter android-dev -- bash -lc 'cd /var/home/zak/Projects/Parks && ./gradlew :app:assembleDebug'
```

- Inside the container: Temurin JDK 17 at `/usr/lib/jvm/temurin-17-jdk`, `ANDROID_HOME`
  set to `/var/home/zak/Android/Sdk`. Both are already on the container's login-shell
  environment, which is why the `-lc` matters — use a login shell, not a bare `bash -c`.
- `gh` is the **opposite way round**: installed on the host, absent from the container.
  Run issue and release commands from the host.
- The container shares the home directory and the network, so the repo, the SDK, the
  keystore and the adb server are all the same ones the host sees. `adb` works from either
  side and talks to the same server.
- Tests: `./gradlew :app:testDebugUnitTest` (same wrapper). The crowd model is the part
  with real logic in it, so it has real tests — keep them passing.

### The emulator
Run it **from the host** — the host has `~/Android/Sdk/emulator` and no JDK is needed to
boot an AVD. The AVD is **`android37`** (a pixel_7 profile on android-37.0 google_apis
x86_64). Not `Pixel_9_Pro` (the Mac's) and not `Pixel_9_Pro_CLI` (the old Linux box's).

```
~/Android/Sdk/emulator/emulator -avd android37 -no-window -no-audio -no-boot-anim -gpu host &
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n contact.kaufman.parks.debug/contact.kaufman.parks.MainActivity
adb exec-out screencap -p > /tmp/shot.png
```

- **`-gpu host` is required, not optional** (re-confirmed 2026-09-15). Without it the
  emulator dies in about 20 seconds with a `SIGSEGV` in
  `qemu-system-x86_64-headless` — a core lands in `coredumpctl`, no device ever appears in
  `adb devices`, and the emulator log's last line is the innocuous "cold boot without a
  saved state". It reads like a hang or a bad AVD; it is the swiftshader crash. With
  `-gpu host` it boots in **30 seconds** and authorizes adb **without a tap**.
- `/dev/kvm` is `crw-rw-rw-`, so acceleration works without adding Zak to a `kvm` group.
- Linux has `timeout(1)`, so the poll loop above is a convenience rather than a necessity.
- The app requests location on first launch. Skip the dialog in scripted runs with
  `adb shell pm grant contact.kaufman.parks.debug android.permission.ACCESS_FINE_LOCATION`
  (and `ACCESS_COARSE_LOCATION`), then force-stop and relaunch.
- Screenshot previews also exist via `./gradlew updateDebugScreenshotTest`.
- Zak installs releases via Obtainium from GitHub Releases. He does not use ADB.

## Keeping the stack current
- `python3 tools/check-versions.py` compares every pin in `gradle/libs.versions.toml`
  against what is actually published, and lists the Gradle wrapper and GitHub Actions pins
  that live outside the catalogue. `--stale` hides what is already current. No Gradle
  plugin and no build dependency, so it cannot break the build and works even when the
  build is broken.
- It prints **two** "latest" columns on purpose. Parks is deliberately on the Compose
  **alpha** BOM, so a report that only knew about stable releases would tell you to
  downgrade and lose every Expressive API.
- The script **self-tests its own version ordering before reporting** (`--self-test` runs
  just that). Worth keeping: ordering went wrong three separate ways while it was written —
  a release candidate outranking its release, a suffix sorting higher merely by being
  longer, and `0.8.0-0.6.x-compat` having its digits swallowed into the numeric prefix so
  it beat plain `0.8.0`. Each case is pinned.
- **The policy, decided 2026-09-15: take stable and release candidates; take an alpha only
  when it buys something.** Compose qualifies — Material 3 Expressive exists nowhere else.
  AGP, activity, datastore and lifecycle alphas do not: nothing in the app needs an API
  that only exists there, and AGP alphas in particular churn. Re-checking is cheap now, so
  adopting them on release is a small job rather than a standing risk.

## Toolchain (verified 2026-09-13)
- AGP 9.x has built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android`. Kotlin options
  go in a top-level `kotlin { compilerOptions { } }` block.
- `compileSdk = 37` + `compileSdkMinor = 2`; `targetSdk = 37`, `minSdk = 31`.
- **`compose-bom-alpha` 2026.09.00 resolves material3 to 1.5.0-alpha28**, the newest alpha.
  Do not also pin material3 by hand — the BOM already has it, and a second pin just drifts.
  Confirmed by resolution, not by reading the comment (2026-09-15):
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath` shows
  `material3:1.5.0-alpha28`. Transitive requests for 1.3.1 and 1.4.0 appear in that tree
  but are *upgraded* by the BOM, which is what makes it look at a glance as though the app
  is on 1.4.0 stable. It is not.
- `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `LargeFlexibleTopAppBar` and
  `LoadingIndicator` are all **public and working** at alpha28. (Grassfed's CLAUDE.md says
  they are internal; that note is stale — it was true of an earlier alpha.)
- The screenshot-test plugin needs its flag in **both** `gradle.properties`
  (`android.experimental.enableScreenshotTest=true`) *and* the module's `android { }`
  block via `experimentalProperties[...]`. Setting only one fails configuration with a
  message telling you to set the other one.
- kotlinx-datetime 0.8.0 (bumped from 0.7.1 on 2026-09-15, no source changes needed):
  `LocalDate.month.number` and `dayOfWeek.isoDayNumber` are
  **extensions** — they need `import kotlinx.datetime.number` / `.isoDayNumber` or you get
  "Unresolved reference on receiver of type 'Month'". `Instant` comes from `kotlin.time`,
  not `kotlinx.datetime` (that typealias is deprecated).
- `hiltViewModel()` now lives in `androidx.hilt.lifecycle.viewmodel.compose`, not
  `androidx.hilt.navigation.compose`.
- Navigation 3: `entry` is a **member extension** on `EntryProviderScope`, so it needs no
  import inside `entryProvider { }` — importing `androidx.navigation3.runtime.entry`
  fails. `rememberSceneSetupNavEntryDecorator` is **internal**; `NavDisplay` installs it
  itself, so pass only the saveable-state and view-model decorators.
- AboutLibraries 15.x: the licence badge is tinted by a **per-licence hue resolver**, and
  neither `licenseChipColors` nor `ContrastLevel.High` overrides it — under a dynamic
  palette that renders pale-on-pale in light theme. The fix is
  `m3VariantColors(licenseHueResolver = LicenseHueResolver.None, licenseBadgeContainer = …,
  licenseBadgeContent = …)`. See `ui/settings/AboutScreen.kt`.

## Architecture
- Package `contact.kaufman.parks`; layers `data/{api,db,repo,crowd,prefs}`, `domain/`,
  `ui/<feature>/`, `di/`.
- Hilt for DI (KSP, not kapt), Room for persistence, DataStore for settings, Ktor
  (OkHttp engine) + kotlinx.serialization for networking, Navigation 3 for navigation.
- **That list is the whole list.** Five dependencies were declared and never used, and were
  removed 2026-09-15: `androidx.work` and `androidx.hilt:hilt-work` (the app does no
  background work at all — that is the point of it), `androidx.browser` (the hand-off uses
  plain intents, not Custom Tabs), and both Coil artifacts (nothing displays a remote
  image). `androidx.hilt:hilt-compiler` went too: it processes `@HiltWorker`, and Dagger's
  own compiler handles `@HiltViewModel`. Verified at runtime, not just compiled — DI is the
  kind of thing that fails on launch rather than in the build.
- `material-icons-extended` **is** needed despite looking like bulk: most icons in use
  (Thunderstorm, WbSunny, DirectionsCar, MyLocation, History, Grain) are not in the core
  icon set, and R8 shrinks what is not referenced.
- `ParksRepository` is the only place live data is assembled. It caches per-park snapshots
  in memory for 120s. **Nothing polls** — themeparks.wiki is volunteer-run, so every fetch
  is triggered by a screen or a pull-to-refresh.
- A failed refresh keeps the previous snapshot and attaches an `error` to it. Never blank a
  card that still holds usable, if stale, numbers.
- Weather is deliberately **not** part of a dashboard refresh. Zak has a dedicated weather
  app; park weather is fetched only when a park screen asks for it.

## The crowd model — the part worth understanding
`data/crowd/` reproduces the method WDW Passport publishes, because on public data it is
the only honest one available: neither resort releases attendance, so posted waits are the
whole signal.

1. `CrowdSeed` holds a curated set of **established** key attractions per park. New rides
   are excluded on purpose — their waits are high and flat regardless of crowds, so they
   add no information and bias every day upward.
2. Average each ride's posted standby across 10AM–5PM (`CrowdModel.WINDOW_*`).
3. Rank each **ride** 1–10 against what it normally does on a day like today.
4. Park level = the **mean of the ride ranks**, not the mean of the waits. This is the
   important bit and there is a test pinning it: one headliner melting down must move the
   park by less than a full level.

Expected wait = `baselineMinutes x monthFactor x dayOfWeekFactor`. One popularity number
per ride, one seasonality curve shared by all of them — rather than 12 hand-tuned numbers
per ride pretending to a precision nobody has.

`CrowdBaselines` is the self-correcting half. Every refresh records the posted waits into
`wait_samples` and rolls them up into `daily_wait_averages`. Once a ride has
`MIN_OBSERVATIONS_TO_TRUST` comparable days on file, its **measured** baseline replaces the
seed and `fromRecordedHistory` flips true. A recorded baseline already carries its own
seasonality, so the month/day curve must **not** be applied on top of it.

Two traps already hit:
- **Do not publish a reading from current waits after 5PM.** With no midday roll-up the
  fallback is "what is posted right now", and at 11PM that is a closing-time queue — every
  park read "Ghost town · much quieter than usual" until this was gated. Provisional
  readings are labelled "so far today".
- A reading needs at least 3 watched rides open. Fewer than that is a rumour, not a level.

## Disney Springs
- It is a `Park` with `kind = DINING_DISTRICT`, and **its `id` is `"disney-springs"`, not a
  UUID** — themeparks.wiki has no entity for it. Nothing may pass that id to `/live`,
  `/schedule` or `/children`. `ParksRepository.refresh` branches on `kind` before any
  request is made.
- **Its restaurants do exist upstream**, filed under the Walt Disney World *destination*
  (`e957da41-…`) rather than a park. `refreshDiningDistrict` fetches that list once, caches
  it for the process, and selects the entries whose coordinates fall inside the mapped
  Disney Springs boundary. 38 of them, verified on device.
- **No hours, no crowd level, and no live status, deliberately.** Nothing publishes Disney
  Springs hours where the app can read them; there are no posted waits to rank; and the
  destination feed carries no open/closed flag. Each of those is a place the UI had to be
  taught to show *less* rather than a blank:
  - the dashboard card shows "38 places to eat" instead of hours, and no crowd pill
  - the park screen omits the hours line entirely rather than printing "Closed today"
  - the "Open only" chip is hidden, and the filter skipped — it was silently hiding every
    restaurant, because `UNKNOWN` is not `OPERATING`
  - a row with `UNKNOWN` status draws **no badge**; it was drawing "Closed", which is a
    claim the app cannot support
- Parking works normally: the three garages (Orange, Lime, Grapefruit) and four surface
  lots (Lemon, Mango, Strawberry, Watermelon) are in both `ParkingLots` and `ParkingAreas`,
  so the geofence fills the section in. Verified: a fix in the Orange Garage prefills it.
- **`hasLevels` is true, but `mergesLevelIntoRow` is false.** The garages are multi-storey,
  but unlike Universal the level is not part of a posted space number, so gluing them into
  "412" would invent a number that appears on no sign. It reads "Level 4 · 12" instead.
- Adding it made the compiler flag every `when (park)` that needed a decision, which is the
  main reason it is an enum entry rather than a parallel type.

## Lands
- `domain/ParkLands.kt` is generated by `tools/park-lands.py` from OSM `place=locality`
  areas inside each park boundary. themeparks.wiki carries no land for an attraction, so it
  is derived from the coordinate.
- **`landAt` returns the smallest containing land.** Lands nest — Storybook Circus sits
  inside Fantasyland's outline — and the tighter answer is the useful one. Pinned by a test
  using The Barnstormer's real coordinates.
- **Coverage is 93% and the UI must tolerate the rest.** A ride with no land is normal:
  Animal Kingdom's Discovery Island is unmapped, and Universal Studios' Hogwarts Express
  platforms and Halloween Horror Nights houses genuinely sit in no themed land. Selecting a
  land hides them, which is correct — they are not in it.
- `landsIn` returning empty is the signal to draw **no chip row at all**, rather than an
  empty one. Every park has lands today, but that is the failure mode to preserve.
- The generator carries a small curation table: `NOT_A_LAND` drops market stalls and
  "Closed for construction", and `RENAME` fixes two OSM typos (Commissary Lane, Grand
  Avenue). Corrections live in the script so a regeneration cannot silently undo them.
  An area threshold was rejected — Pixar Place is a real land whose polygon is 0.12 ha.
- **Read relations, not just ways.** A first survey looked only at each element's own
  `geometry`, which relations do not carry, and concluded Islands of Adventure had no lands.
  It has eight, including Hogsmeade and Jurassic Park.
- EPCOT's lands are OSM's "Future World" and "World Showcase" — pre-2021 naming. Still how
  most people navigate it, but not what the signs say. Fix upstream in OSM.

## Best time to ride
- `ParkEntity.bestTimeAhead()` reads Disney's hourly `forecast` array. It is deliberately
  conservative: it stays silent below a 10-minute saving, when the current standby is
  already lower than anything forecast, and when fewer than two forecast hours remain.
  Bad advice is worse than no advice when someone is standing in front of the ride.
- The comparison is against **what is posted now**, not against the rest of the forecast.
  The question being answered is "queue now or come back?", not "when is the daily trough?".
- **Universal has no forecast at all**, so those ride rows are not expandable — no chevron,
  no tap target. Verified on IOA.
- In practice the quietest hour is very often the park's last operating hour, because
  queues taper at close. That is real, not a bug, but it does mean the advice reads
  similarly across many rides on the same evening.

## Nearest ride
- A third `RideSort.NEARBY` on the Rides tab. `ParkDetailUiState.availableSorts()` hides
  the chip unless there is a fix, so the control never exists in a state where it cannot
  do anything, and nothing has to explain itself when location is off.
- **The fix is discarded unless `Geo.parkAt` puts it in the park being viewed.** From home
  the sort would order every ride by a gradient pointing at the front gate — plausible
  output from a broken feature, which is the worst kind. `InParkFix` is a distinct type so
  that precondition is hard to drop in a refactor.
- `parkAt`'s soft spot applies: a poor fix near the USF/IOA wall can pick the sibling park
  and the chip then quietly does not appear. That is the right failure — the alternative is
  distances measured from next door.
- `visibleEntities()` guards on the **fix**, not the sort, so losing location falls back to
  the wait order rather than rendering an arbitrary one. Entities with no coordinates sort
  last, the same way a ride with no posted wait does.
- Distances are shown **only while that sort is active**. On the wait or A–Z orders they
  would be a number on every row that nobody asked for.
- `formatWalkingDistance` is feet and miles, matching the US English used everywhere else
  rather than adding a second unit setting beside the temperature one. Rounded to 10ft,
  which is finer than the fix deserves — but rounding coarser makes a sorted list look
  broken, with several rides sharing a distance while sitting in an obvious order.

## Hand-off to the official apps
- `ui/components/OfficialApps.kt`. Package names verified against the Play Store listings
  2026-09-14: `com.disney.wdw.android`, `com.universalstudios.orlandoresort`. Both are
  declared in the manifest `<queries>` — without that, Android 11+ reports every app as
  missing and the hand-off would always fall through to the store.
- **Launcher entry point only.** Both apps almost certainly have internal deep links to
  mobile order or a specific restaurant, but none are documented; an undocumented scheme
  that silently stops working is worse than one extra tap.
- Falls back `market://` then the Play Store web URL then a toast. The emulator is a
  `google_apis` image, so the web branch is the one exercised there — the direct-launch
  branch can only be verified on a device that actually has the app.
- **`com.android.vending` is present on `android37` but is a stub** (checked 2026-09-15):
  no launcher activity, and `market://` resolves to nothing. This is why the code tests
  with `getLaunchIntentForPackage` rather than `getPackageInfo` — the latter would report
  the Play Store as installed here — and why the `market://` step is wrapped in
  `runCatching` rather than pre-resolved. Both hold up on this AVD; keep them that way.

## Which park you are in
- `domain/ParkBoundaries.kt` holds real **mapped footprints** for all seven parks, plus
  CityWalk. `Geo.parkAt` is a thin delegate to it. **Generated — do not hand-edit**: run
  `python3 tools/park-boundaries.py` to refresh.
- **Regenerate when a park's footprint changes.** Magic Kingdom is expanding, so this will
  be needed. A park that grows outside its old shape simply stops being detected in the new
  part: a staleness bug, not a mystery. The script fetches by OSM element id, checks each
  park's declared coordinate still lands inside the shape it fetched, checks CityWalk stays
  disjoint from both Universal parks, and refuses to write the table if any of that fails.
- **What this replaced, and why it mattered more than it looked.** The old test was "nearest
  park centre within 1,200m". Measured against the mapped footprints it put only **80.7% of
  Universal Studios** in the right park — not a strip by the wall, a fifth of the park — and
  claimed **100% of CityWalk** was inside a park. USF and Islands of Adventure share a wall,
  so no arrangement of centres can separate them.
- **CityWalk is in the table with a null `park`.** It abuts both Universal parks, so "not a
  park" has to be an explicit answer rather than a gap between two polygons, and it is
  checked *before* the parks. `ParkBoundaries.at` returns the shape if you need to tell
  "CityWalk" from "nowhere"; `parkAt` collapses both to null.
- `EXPANSION_TOLERANCE_METERS` (150m) absorbs a fix that has drifted past a fence and buys
  slack when a park grows before anyone regenerates. It applies **only when exactly one
  park is in range** — near the USF/IOA wall both qualify and the answer stays null, because
  nearer is not the same as right.
- A ride-position heuristic was measured and rejected. Nearest-ride is good at which-park
  (99.7% USF, 96.7% IOA) but **cannot exclude CityWalk**: in-park points are up to 285m
  from their nearest ride while CityWalk gets within 31m of one, so no distance cap
  separates them. Every setting either strands you in CityWalk or ejects you from a park
  you are standing in.
- **Not exact at the gates, on purpose.** Four parking lots closest to an entrance —
  EPCOT's Eve, Hollywood Studios' Mickey and Olaf, Epic's Explorer — still report as being
  in the park, either because the OSM footprint takes in the entrance plaza or because they
  fall inside the 150m tolerance. Left alone: standing in the Eve lot, "You're here ·
  EPCOT" is the answer a person would give. It is also a big improvement on the old 1,200m
  radius, under which **every** lot reported in-park. Parking detection is unaffected —
  `ParkingAreas` is a separate table and still names the lot.
- **Three of the seven coordinates in `domain/Parks.kt` were wrong** and were corrected
  2026-09-15 to interior points, pinned by a test. Animal Kingdom's and Universal Studios'
  sat outside their own footprints; **Epic Universe's was 3.6km out, down by SeaWorld**, so
  Epic could never be detected and `Geo.resortCenter` dragged Universal's weather point a
  kilometre south. Nothing decides which park you are in from these any more, but weather
  still averages them.

## Location
- `data/location/LocationProvider.kt` uses the **platform `LocationManager`**, not Play
  Services' fused client: no Google dependency, which is the point of this app. Single fix
  on request only — there is no continuous-updates path and there should not be.
- **`getCurrentLocation` needs a timeout.** It is documented to always call back, but a
  cold GPS indoors (or a wedged emulator) simply never answers, and without
  `withTimeoutOrNull` the caller suspends forever and the feature looks dead rather than
  unavailable. This was a real hang, found on device.
- `Geo.parkAt()` compares against park **centres**, which is crude. Magic Kingdom and EPCOT
  are miles apart and safe; **Universal Studios and Islands of Adventure share a wall** and
  their centres are under 1km apart, so a poor fix near the boundary can pick the wrong
  one. A test pins that distance so the tightness is never a surprise. Real polygons would
  settle it — see the parking-geofence section of PLAN.md.
- **`Log.d`/`Log.v` are stripped from release** by a proguard `-assumenosideeffects` rule.
  The location diagnostics print GPS coordinates, and those have no business surviving into
  a shipped build of this app. Warnings and errors are kept. If you add a diagnostic that
  must survive release, use `Log.w`, and think hard about what is in it.
- **Simulating a position works on `android37`** (established 2026-09-15). The previous
  note said it was impossible and to verify on a real device; that was the wrong conclusion
  drawn from a real symptom. `geo fix` is the tool — the trick is *when* you send it:

  ```
  adb shell am force-stop contact.kaufman.parks.debug
  adb shell am start -n contact.kaufman.parks.debug/contact.kaufman.parks.MainActivity
  for i in $(seq 1 8); do adb emu geo fix -81.5901 28.3553; sleep 1; done   # Animal Kingdom
  ```

  **`adb emu geo fix` is silently discarded unless a client is holding a live GPS request
  at that moment.** The console answers `OK` either way, which is what makes it so
  misleading. `dumpsys location | grep -A1 "gps provider:"` showing `ProviderRequest[OFF]`
  means nothing was listening and your fix went nowhere. Parks asks for a *single* fix on
  demand, so the window opens on launch and shuts as soon as a fix lands — hence
  force-stop, relaunch, then loop the fix for a few seconds. Sending it to an app that is
  not running does nothing at all, verified.
- **To move to a *second* position, flush the cached fix first.** This is the step that
  bites: once any fix is cached, `getCurrentLocation` returns it instantly, the request
  shuts before your `geo fix` arrives, and the app keeps reporting the **first** position
  while `adb emu geo fix` keeps answering `OK`. It looks exactly like a broken geofence.
  Two screenshots of three different simulated locations came out identical before this
  was understood. Toggle location off and on between positions:
  ```
  adb shell am force-stop contact.kaufman.parks.debug
  adb shell settings put secure location_mode 0; sleep 2
  adb shell settings put secure location_mode 3; sleep 1
  adb shell am start -n contact.kaufman.parks.debug/contact.kaufman.parks.MainActivity
  for i in $(seq 1 10); do adb emu geo fix <lon> <lat>; sleep 1; done
  ```
  Always confirm the device actually moved before believing a screenshot:
  `adb shell dumpsys location | grep -m1 "last location=Location\[gps"`.
  Confirmed end to end twice: the dashboard pinned "You're here · Islands of Adventure"
  and then "· Animal Kingdom", and the weather strip moved between Universal's 76°/feels
  84° and Disney's 78°/feels 87° — which incidentally exercises the per-resort split.
- **Do not reach for `cmd location providers`** — it does not work here and looks like it
  might. `add-test-provider` throws `SecurityException: android from uid 2000 not allowed
  to perform MOCK_LOCATION`, `enable-test-provider` reports "Unknown command", and
  `set-test-provider-location gps --latitude …` throws `Unknown option: --latitude`. It is
  easy to run these, see the position change, and credit the wrong command — the change is
  a `geo fix` from moments earlier landing late. That mis-attribution happened while
  writing this note.
- **The clock-change wedge is real, and a plain restart does not clear it.** An earlier
  version of this note said it had not been reproduced; that was wrong, and reproducing it
  cost an hour. Setting the emulator's date forward to test the dashboard's date rollover
  killed its GPS: afterwards `adb emu geo fix` answered `OK` forever while
  `dumpsys location` showed the AVD's **persisted** last-known fix, hours stale and
  unchanged, with `ProviderRequest[OFF]` immediately after launch. Restarting the AVD did
  **not** help, because that fix is persisted in userdata.
  What clears it is a wipe:
  ```
  adb emu kill
  emulator -avd android37 -no-window -no-audio -no-boot-anim -gpu host -wipe-data
  ```
  After that `dumpsys location` reports no gps fix at all and `geo fix` lands first try.
  The cost is the app and its database, so **do the clock test last**, or expect to
  reinstall and re-grant `ACCESS_FINE_LOCATION` afterwards.
- There is a second, subtler reason a stale fix blocks everything: `LocationProvider`
  rejects any fix older than five minutes, so a persisted one is discarded — but
  `getCurrentLocation` still returns it *instantly* from cache for each provider in turn,
  so the GPS request opens and shuts before a `geo fix` can arrive. Flooding fixes during
  launch does not beat it. Only the wipe does.

## Trip history
- `park_sightings` stores one dumb row per moment the app could see you were in a park —
  park id and a timestamp, **no coordinates**. The park is the whole answer; the fix that
  produced it is nobody's business afterwards, this app's included.
- Written from the two places a fix already resolves to a park: the dashboard's
  `detectPark` and the park screen's `locate`. Recorded at those call sites rather than
  inside `Geo.parkAt`, so merely asking "which park is this?" never writes anything — a fix
  taken for the weather is not a visit.
- **Debounced to one row per park per 10 minutes** (`TripRepository.SIGHTING_INTERVAL`).
  The dashboard takes a fix every time it opens, and opening it six times in a queue would
  otherwise inflate the sighting count until it stopped meaning anything.
- `groupSightings` turns rows into visits. Two rules worth knowing:
  - **Park days, not calendar days** — it reuses `ParkingDay.dayOf`, so a 00:35 sighting
    after a hard-ticket night files under the evening before. Verified on device: a visit
    reads "10:10 PM – 12:35 AM".
  - **Visits are not split on gaps, deliberately.** The first version started a new visit
    after three hours of silence. Against a realistic day — app opened at 9:12, 1pm and
    7:40pm — that produced *three* visits from one continuous day at Magic Kingdom, because
    sightings only happen when a screen asks for a fix. No threshold fixes it: the app
    cannot see you leave, only the absence of evidence, and a phone in a pocket looks
    exactly like a drive home. A test pins this.
- The consequence, accepted: on a park-hopping day the two spans can overlap, because an
  all-day Magic Kingdom visit with an EPCOT trip inside it is what the evidence actually
  supports. `ParkVisit.sightings` is surfaced so the reader can see how thin a span is.
- A single sighting shows a **time, not a span** (`hasMeaningfulDuration`) — "9:12 AM –
  9:12 AM · 0m" would be worse than saying nothing.
- **The empty state has to explain itself.** This screen cannot fill itself in, so a blank
  one reads as broken. It says days appear once you open the app at a park, and that
  nothing is recorded in the background.
- Dawarich is the planned optional source for ride-level detail — see PLAN.md Phase 3. The
  local path must keep working entirely without it.

## Wait-time history
- An expanded ride shows two charts: Disney's **forecast** for the hours left today, and
  below it the app's **own recorded** midday averages for recent days. They are deliberately
  labelled differently — one is a projection, the other is measurement, and conflating them
  would misrepresent what the app actually knows.
- Days with fewer than `CrowdModel.MIN_SAMPLES_FOR_A_DAY` samples are excluded. A single
  reading captured while walking past is not a midday average, and plotting it beside real
  days would let an outlier dominate the chart.
- History is loaded **only when a row is opened**. Reading every ride's record just to
  decide whether to draw a chevron would be a database scan per refresh.
- This is why Universal rows can now expand at all: they have no forecast, but once the app
  has watched a ride for a few days its own history is worth showing.
- To exercise the chart without waiting days, seed `daily_wait_averages` directly:
  `adb shell run-as contact.kaufman.parks.debug sqlite3 databases/parks.db "INSERT ..."`.

## Conventions
- Compose only, Material 3 Expressive, dynamic color on, edge-to-edge, predictive back.
- Expressive opt-ins are set once in `app/build.gradle.kts`, not per call site.
- Crowd and wait colors share one cool→warm ramp (`ui/theme/Color.kt`). Deliberately not
  red/green: a busy park is not an "error".
- Park entity IDs are themeparks.wiki UUIDs, hardcoded in `domain/Parks.kt` and
  `data/crowd/CrowdSeed.kt`. If one goes stale the ride simply drops out of the average —
  that is the intended graceful failure, but it is worth re-verifying against
  `/v1/destinations` when numbers look wrong.
- Hard-ticket nights (Halloween Horror Nights, Not-So-Scary) arrive as separate
  `TICKETED_EVENT` schedule rows and are surfaced on the card. This is the single most
  confusing thing about park hours — a park "closes" at 6 and reopens on a separate ticket.
- **Room schema changes need a real `Migration`**, never a destructive fallback. Zak runs
  released builds on his own phone: the parking records and the months of wait samples the
  crowd model converges on are irreplaceable. `MIGRATION_1_2` (adding `park_sightings`) is
  the pattern — and verify it by installing over the previous build and checking the data
  survived, not just by building.
- Commit straight to `main`. Releases are tags `vX.Y.Z`; CI builds and publishes the signed
  APK. versionCode = major*10000 + minor*100 + patch, derived from the tag.

## API notes (verified 2026-09-13)
- `api.themeparks.wiki/v1` — no key, no auth. `/entity/{id}/live`, `/children`, `/schedule`.
- WDW gives `STANDBY`, `RETURN_TIME`, `PAID_RETURN_TIME`, and an hourly `forecast` array.
  **Universal gives no `forecast`** — it has `STANDBY`, `PAID_STANDBY` (Express) and
  `SINGLE_RIDER` instead. Any feature built on `forecast` is Disney-only.
- **Universal's `/live` omits restaurants entirely.** Dining for USF/IOA/EU can only come
  from `/children` (names + lat/long), with no live status. Disney's `/live` does include
  them.
- `/entity/{id}/history` exists and works, but it is a **rolling ~48-hour window only** and
  ignores every date parameter. There is no deep backfill to be had.
- WDW schedules also carry Lightning Lane pricing and availability under `purchases`.

## Weather
- Fetched when a screen that shows it appears — the dashboard header on open, a park screen
  on open. **Never in the background**, and cached per location for
  `WEATHER_FRESH_FOR_SECONDS` (15 min) so bouncing between parks does not hammer Open-Meteo.
- **One reading per resort, not per park.** `weather(park)` resolves to its resort's centre
  (computed by `Geo.resortCenter`) and fans the result out to every sibling park's snapshot,
  so opening EPCOT also answers for Magic Kingdom — four parks, one request. The two
  resorts stay separate: Universal is a dozen miles up I-4 and genuinely gets different
  weather. Pinned by `ResortWeatherTest`.
- The dashboard header follows the resort you are **detected in**, falling back to Walt
  Disney World.
- A failed refresh returns the **last cached reading** rather than null, so the strip never
  blanks on a flaky connection.

## The dashboard's chrome
- **Parking is an extended FAB, not a card in the list.** It was a full-width card pinned
  above every park, which meant an empty "Record your parking spot" prompt occupied the top
  of the screen every day for a thing done once a visit. The button keeps its label in both
  states — the icon alone is a car, which could as easily mean directions — and when a spot
  is recorded the label becomes the spot, so the dashboard still answers "where is the car?"
  at a glance. The park name is dropped there for width; the parking screen has it in full.
- The list carries **96dp of bottom content padding** so the last park card can scroll clear
  of the button rather than sitting under it forever.
- **The top bar uses `enterAlwaysScrollBehavior`, and must not go back to
  `exitUntilCollapsed`.** exitUntilCollapsed only re-expands once the list is back at the
  very top — and at the top, `PullToRefreshBox` consumes the downward drag to drive its
  indicator, so the app bar never receives the gesture that would grow it back. The visible
  bug was a large title that collapsed on the first scroll and then stayed collapsed for the
  rest of the session, even sitting at the top of the list. The two components want the same
  gesture in the same place; enterAlways sidesteps it by expanding on any upward scroll.

## Parking screen
- Every picker is a **fused Material 3 Expressive button group** (`ui/parking/FusedButtons.kt`)
  — park, section, garage level. Each step is a pick-exactly-one, which is what a connected
  group communicates and a row of detached chips does not.
- Rows are balanced (`balancedRows`), never a plain `chunked(3)`: four options as 3+1 leaves
  a lone full-width button that reads as a layout bug.
- **There is no free-text section field.** The posted lot names are the only ones that
  exist; anything unusual belongs in the note. Removing it was a deliberate call — the
  trade is that a stale `ParkingLots` table now blocks picking a renamed lot, so keep that
  table current.
- `Modifier.imePadding()` plus a `BringIntoViewRequester` on the row field. Under
  edge-to-edge, `adjustResize` alone does **not** resize a Compose window, so without both
  the keyboard draws straight over the field being typed into.

## Parking by geofence
- `domain/ParkingAreas.kt` is the polygon table, traced from **OpenStreetMap** (ODbL — the
  credit on the settings screen is a licence condition, not a courtesy, and must stay).
  Shapes are shipped as constants; nothing is fetched from OSM at runtime and there are no
  map tiles anywhere in this app.
- Outlines are **simplified to about 5m** — 1,700 OSM points down to 316. Finer would be
  false precision against a fix that is good to 5-10m at best. Neighbouring lots overlap by
  up to 3m along their shared tram aisles as a result, which is below that noise floor.
- `Geo.ringContains` is ray casting over a flat lat/lon grid. That is fine for shapes a few
  hundred metres across in Florida and would not be near a pole or the dateline. Rings are
  flat `DoubleArray`s rather than lists of points because they are shipped constants and a
  `List<Pair<Double, Double>>` would box several hundred coordinates for nothing.
- A fix inside nothing but within `NEAR_TOLERANCE_METERS` of an edge still counts as that
  lot: cars sit in tram aisles between polygons, and a fix drifts.
- **The prefill is an assist, never an authority.** `ParkingFormState.withDetected` never
  overwrites a park or section already chosen by hand, and never fills the row or the
  level. Tests pin the refusals, which is the half a refactor loses.
- **A Universal garage cannot tell you the park.** USF and Islands of Adventure share both
  structures, so those areas carry a `group` and a **null `park`**, and the screen asks
  which park rather than guessing. The garages themselves are 426m apart and separate
  cleanly — the old worry that they sat on top of each other was wrong.
- Not in OSM, so they prefill nothing: Hollywood Studios' **BB-8**, and Epic Universe's
  **Monster, Viking, Gamer, Hero**. The table is generated from an Overpass query, so the
  fix for those is upstream in OSM rather than a hand-edit here.

## Parking lot data
- A recorded spot **expires at 4AM park time**, not midnight (`domain/ParkingDay.kt`).
  Magic Kingdom's hard-ticket nights run to midnight and EPCOT's Extended Evening to 11PM,
  so a car parked at 9PM is still parked at 12:30AM. The rollover is computed by stepping
  a **calendar day**, never by subtracting 24 hours — the two DST days are 23 and 25 hours
  long and a fixed subtraction lands an hour off on exactly those mornings. Tests pin both.
- Expiry is evaluated **on read** (plus an idempotent `expireStale()` on screens that show
  parking), not scheduled: no wake-ups, no battery cost, still correct if the phone was off.
- `domain/ParkingLots.kt` is a **curated table** — themeparks.wiki has no parking data.
  Verified 2026-09-14 against Disney Parks Blog, Universal's app listings and on-site photo
  coverage. Resorts rename lots (EPCOT's were replaced wholesale in January 2023), so a
  section missing from the chips is a data-staleness bug, not a mystery. Every field stays
  free-text so a stale table can never block recording a spot.
- **Universal's garages encode the level into the posted number**: "Cat in the Hat 457" is
  level 4, row 57. That is why those two parks get a separate level picker and the rest
  do not — `ParkingLots.hasLevels()`.
- Row *ranges* per section are not published anywhere verifiable, so there is no fixed row
  dropdown. Rows previously used in a lot are offered as chips instead, learned from the
  user's own history (`ParkingDao.recentRows`).
- **US English throughout the UI** — "color", not "colour"; "license", not "licence".

## Driving the emulator
- Re-installing resets the nav stack, so scripted tap sequences must start from the
  dashboard. Check `dumpsys activity activities | grep ResumedActivity:` between steps
  rather than assuming a tap landed. This one is general and still holds.
- Recorded on the **retired** Linux box's `Pixel_9_Pro_CLI`, and **not re-checked** on this
  machine's `android37` — treat as a warning, not a fact:
  - `input keyevent 111` (ESCAPE) exited the app rather than closing the keyboard; BACK
    (`keyevent 4`) was the safe way to dismiss it.
  - Grassfed ran in a freeform floating window on that AVD and swallowed taps aimed at
    Parks. Confirmed 2026-09-15 that **neither Grassfed nor LubeLogger is installed on
    `android37`**, so nothing should be stealing taps here today — but all three projects
    share this AVD, so re-check with `pm list packages` if taps start going astray.

## Setting up a new machine
A fresh clone does **not** build signed releases, and three things have to be carried over
by hand because none of them belong in git:

1. **`~/.config/parks/`** — `parks-release.jks` and `keystore.properties`. Copy the whole
   directory (`chmod 600` both files afterwards). Without it, local release builds silently
   fall back to the **debug key**, which produces an APK that installs fine and then
   refuses every properly signed update. CI is unaffected: it signs from GitHub secrets.
2. **`local.properties`** — gitignored, one line, machine-specific: `sdk.dir=<path to the
   Android SDK>`. On this machine `sdk.dir=/var/home/zak/Android/Sdk`. Write the real
   `/var/home` path, not `/home`: `/home` is a symlink to `var/home` on Fedora atomic, so
   both resolve, but tools that compare paths literally will disagree with each other.
3. **JDK 17 and Android SDK platform 37.2** must be present. The build pins
   `jvmToolchain(17)`, so a newer default JDK is fine as long as 17 is installed. On an
   immutable host this means inside the container — do not try to layer a JDK with
   `rpm-ostree`.

**Status of this Bazzite box (verified 2026-09-15): all three done.** `~/.config/parks/`
holds both files at `600`; `local.properties` points at `/var/home/zak/Android/Sdk`;
Temurin 17 and platforms 37.0 + 37.2 are installed in the `android-dev` container. A local
release build here signs correctly — `keytool` reports alias `parks` and SHA-256
`83:B8:F7:0F:C6:B1:18:CE:…`, matching the published key.

One wrinkle worth knowing: `keystore.properties` has `storeFile=/home/zak/.config/parks/…`.
That resolves, because `/home` → `var/home`. Leave it alone unless it breaks.

## Secrets and release identity
- Release keystore lives in `~/.config/parks/keystore.properties` (outside the repo) and as
  GitHub Actions secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- **Every published build is signed with `~/.config/parks/parks-release.jks`, SHA-256
  `83:B8:F7:0F:C6:B1:18:CE:…`, alias `parks`.** A different key means Zak has to uninstall
  and lose his data before he can update. PKCS12, so the key password equals the store
  password. Back this file up; losing it ends the release line.
- The Gradle config **falls back to the debug key** when no keystore is found, so a release
  built without secrets is debug-signed, installs happily, and then rejects every properly
  signed update afterwards. CI fails a tagged build whose APK carries `CN=Android Debug`;
  do not remove that check.
- Never commit `local.properties`, `keystore.properties`, or `*.jks`.
