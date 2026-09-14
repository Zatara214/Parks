# Parks — notes for Claude

Android client for Walt Disney World and Universal Orlando wait times, hours, crowds and
weather. Kotlin, Compose, Material 3 Expressive, GPLv3.

The owner (Zak, GitHub Zatara214) has no engineering background: explain choices in plain
language and proactively suggest workflow improvements. Roadmap and open decisions live in
`PLAN.md`.

## Start of every session
1. `gh issue list` — Zak files bugs and ideas from his phone as GitHub Issues.
2. Read `PLAN.md` for the current milestone.

## Build & verify
- Build: `./gradlew :app:assembleDebug` (JDK 17, SDK at `~/Android/Sdk`).
- Tests: `./gradlew :app:testDebugUnitTest`. The crowd model is the part with real logic
  in it, so it has real tests — keep them passing.
- **An emulator works on this host** (unlike the LubeLogger project's notes, which predate
  the BIOS fix). `Pixel_9_Pro_CLI` authorizes adb without a tap:
  ```
  emulator -avd Pixel_9_Pro_CLI -no-window -no-audio -no-boot-anim -gpu host &
  adb wait-for-device
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb shell am start -n contact.kaufman.parks.debug/contact.kaufman.parks.MainActivity
  adb exec-out screencap -p > /tmp/shot.png
  ```
  **Always launch with `-gpu host`** — the default swiftshader backend segfaults here.
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

## Conventions
- Compose only, Material 3 Expressive, dynamic colour on, edge-to-edge, predictive back.
- Expressive opt-ins are set once in `app/build.gradle.kts`, not per call site.
- Crowd and wait colours share one cool→warm ramp (`ui/theme/Color.kt`). Deliberately not
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

## Secrets
- Release keystore lives in `~/.config/parks/keystore.properties` (outside the repo) and as
  GitHub Actions secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Never commit `local.properties`, `keystore.properties`, or `*.jks`.
