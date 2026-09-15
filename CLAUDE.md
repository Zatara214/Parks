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

## Toolchain (verified 2026-09-13)
- AGP 9.x has built-in Kotlin: do NOT apply `org.jetbrains.kotlin.android`. Kotlin options
  go in a top-level `kotlin { compilerOptions { } }` block.
- `compileSdk = 37` + `compileSdkMinor = 2`; `targetSdk = 37`, `minSdk = 31`.
- **`compose-bom-alpha` 2026.09.00 resolves material3 to 1.5.0-alpha28**, the newest alpha.
  Do not also pin material3 by hand — the BOM already has it, and a second pin just drifts.
- `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `LargeFlexibleTopAppBar` and
  `LoadingIndicator` are all **public and working** at alpha28. (Grassfed's CLAUDE.md says
  they are internal; that note is stale — it was true of an earlier alpha.)
- The screenshot-test plugin needs its flag in **both** `gradle.properties`
  (`android.experimental.enableScreenshotTest=true`) *and* the module's `android { }`
  block via `experimentalProperties[...]`. Setting only one fails configuration with a
  message telling you to set the other one.
- kotlinx-datetime 0.7.1: `LocalDate.month.number` and `dayOfWeek.isoDayNumber` are
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
- The clock-change wedge described previously was **not** reproduced here, and the
  `ProviderRequest[OFF]` symptom showed up on a cold-booted AVD whose clock was never
  touched — so the clock was probably never the cause. The cached-fix behaviour above
  explains the original symptom without it.

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
