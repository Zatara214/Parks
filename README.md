# Parks

A fast, private Android app for Walt Disney World and Universal Orlando.

The official apps are slow, heavy, and want far more of your data than a wait-time
lookup should cost. Parks does the small number of things you actually need at a
glance — how busy each park is, what the waits are, when things open, what the weather
is doing, and where you left the car — and hands off to the official apps for anything
that genuinely requires them.

## What it covers

Seven parks, and only these seven:

| Walt Disney World | Universal Orlando |
| --- | --- |
| Magic Kingdom | Universal Studios Florida |
| EPCOT | Islands of Adventure |
| Hollywood Studios | Epic Universe |
| Animal Kingdom | |

Water parks are out permanently.

## Crowd levels

Neither resort publishes attendance, so no app can tell you how many people are in a
park. What they *can* tell you is what the posted waits are, and that is the signal every
crowd calendar is actually built on.

Parks computes its level the way [WDW Passport](https://wdwpassport.com) publishes it:
average each of a curated set of established key attractions across the 10AM–5PM window,
rank each *ride* 1–10 against what that ride normally does on a day like today, then
average the ride ranks. Ranking rides before averaging is what stops a single broken
headliner from dragging a whole park up two levels.

The "normal" it compares against starts as a shipped estimate and is progressively
replaced by what the app itself observes, so the numbers get more honest the longer you
use it. A reading still resting on estimates says so.

## Data

- **[themeparks.wiki](https://themeparks.wiki)** — wait times, hours, showtimes, dining.
  Volunteer-run, so Parks never polls it; data is fetched when a screen asks and cached.
- **[Open-Meteo](https://open-meteo.com)** — weather, fetched on demand only.

No accounts, no analytics, no ad SDKs, no telemetry. Everything you record — parking
spots, wait history — stays on the device.

## Building

```
./gradlew :app:assembleDebug
```

JDK 17, Android SDK platform 37.2.

## License

[GNU General Public License v3.0](LICENSE).

Parks is not affiliated with, endorsed by, or sponsored by The Walt Disney Company or
NBCUniversal. All park, attraction, and character names are trademarks of their
respective owners and are used here only to identify the places being described.
