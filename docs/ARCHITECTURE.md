# WGO architecture

WGO is a single-module, 100% native Wear OS app (Kotlin, Jetpack Compose for Wear OS, Material 2). This page explains
how it is put together, how data flows (with the privacy notes), how to run and test it on the emulator, and the
resource budgets with their measured values. Visual design lives in `docs/design/`; data use in
`docs/PRIVACY_DATA_FLOW.md`.

## Modules (packages of `app/`)

| package | what it holds | Android? |
|---|---|---|
| `core` | Interfaces and value types every layer shares: `LocationSource` (+ `Fix`, `LocationProfile`), `VoiceInput`, `IntentInterpreter`, `Geocoder`, `Router` (+ `Route`, `RouteStep`, `Maneuver`), `Outcome` / `WgoFailure`, the permission broker. | interfaces are plain Kotlin |
| `nav` | **`NavigationEngine`** (snapping, progress, off route, reroute policy, arrival, GPS lost, ETA, turn events), `RouteGeometry`, `NavState` / `NavEvent`, and **`NavigationSession`**, the state holder that runs one trip (router calls for reroutes, the 1 s tick, pause/resume). | **framework-free** (no Activity, Compose, Context or lifecycle; time through `NavClock`), so a foreground service can host it in P6 |
| `location` | `FusedLocationSource` (Play services) and `PlatformLocationSource` (fallback), the power profiles (`LocationProfiles`), `FixFilter`. | yes |
| `voice` | `VoiceInputChain`: in-app `SpeechRecognizer` first, the system speech dialog as fallback; voice level; test-build utterance injection. | yes |
| `ai` | `TieredInterpreter`: `LocalIntentParser` + `PlaceAliases` (Pasto nicknames) first, the OpenAI-compatible `LlmInterpreter` only for low-confidence requests; `AiCallBudget` (200 calls/day, cool-down on 429). | plain Kotlin + DataStore for the budget |
| `geo` / `route` / `net` | `NominatimGeocoder` (1 request/s, cache, local retry), `OsrmRouter` (OSM-DE foot/car, OSRM demo fallback for car), `Polyline`, `ModeEstimate`, the minimal `HttpClient` over `HttpURLConnection` (no HTTP/JSON framework). | `org.json` from the platform |
| `haptics` | `HapticEvent` and `HapticPlayer` (predefined effects). Only `MainActivity` plays them. | yes |
| `metrics` | `WgoMetrics`: debug-only counters, frame counts per screen, the `WGO_METRICS` log line every 30 s, the `WGO_ROUTE` dump. No-ops outside debug builds. | yes |
| `ui` | Theme (palette, type, motion, responsive scale, the shared `WgoClock`), components, the radar (`RadarMinimap`, `LiveRadarSource`), the screens and surfaces, `Strings.kt` (ES/EN). | Compose |
| root | `MainActivity` (the only Activity: permissions, haptics, Back, lifecycle), `AppViewModel` (the screen state machine), `SettingsViewModel`, `SettingsRepository`. | yes |

## State machine

```
Intro ──> Listening ──(voice / injected utterance)──> Working[interpreting] ──> Working[resolving]
             ^   ^                                         │ failure: Error (RETRY repeats the step)
             │   └───────── Settings <-> MapPicker         v
             │                                       ConfirmPlace ──GO──> (ModeSelection) ──> Working[routing]
             │                                                                                   │
             │       ┌──────────────────────────── Navigation ────────────────────────────┐<────┘
             │       │ WaitingForGps (GPS SEARCHING) ─first usable fix─> Navigating          │
             │       │ Navigating <-> OffRoute -> Rerouting -> Navigating (new route)        │
             │       │                 └─3 failed in a row─> LostRoute (RETRY / END)         │
             │       │ any -> GpsLost (10 s without a usable fix) -> back by itself          │
             │       │ Back / CANCEL -> END NAVIGATION? (KEEP / END)                         │
             │       └──────────────┬───────────────────────────────┬─────────────────────┘
             │                      │ END                           │ Arrived (15 / 25 m)
             └──────────────────────┴──────────── Arrived ──FINALIZAR / Back──┘
```

Back: Intro skips; the pipeline goes one step back (routing back to the place or mode choice, earlier stages home);
Navigation asks END NAVIGATION? (Back again = KEEP); Arrived goes home; Settings goes home; the picker returns to
Settings. Ending or arriving stops the engine and the location request and clears the saved destination. After process
death the app starts at Home: a trip never resumes by itself.

## Data flow

```
mic ──SpeechRecognizer (on device; network recognizer only when the device has no offline model)──> transcript
transcript ──LocalIntentParser + PlaceAliases──> place query (+ mode)        (no network)
          └─(low confidence only)──> LLM provider: transcript text only, no location, no identifiers
place query + coarse bias box ──> Nominatim (identifying User-Agent, 1 request/s) ──> up to 3 places
origin fix + destination + mode ──> OSRM (OSM-DE) ──> route polyline + steps
fixes (one request, only while the app is visible) ──> NavigationSession ──> NavState / NavEvent ──> UI + haptics
                                                     └─(off route, at most 1 per 20 s, 3 in a row)──> OSRM again
```

Privacy: the remote interpreter never receives coordinates; Nominatim receives the query and a ±0.2° bias box around
the user (or the Pasto centre); the router receives the origin and the destination coordinates (needed to route).
Nothing is logged outside debug builds, and debug logs never contain transcripts, keys, URLs or contact addresses
(the debug route dump contains the route's coordinates only). Keys and the contact address live in the git-ignored
`local.properties`. Details: `docs/PRIVACY_DATA_FLOW.md`.

## Build types

| type | use | notes |
|---|---|---|
| `debug` | development | debug receivers (`src/debug`), `WgoMetrics`, test hooks |
| `benchmark` | performance runs | `initWith(release)`, not debuggable, R8 + resource shrinking, debug-signed, id suffix `.bench` (installs next to debug); no receivers, no metrics; test hooks only through activity extras |
| `release` | not shipped yet | minification still off |

## Running and testing on the emulator

- JDK: Gradle 8.10.2 needs JDK 17-21. Use the repo-local `jdk-21.0.4+7` for the session only:
  `$env:JAVA_HOME = (Resolve-Path .\jdk-21.0.4+7).Path; .\gradlew.bat assembleDebug`.
- Emulator: `.\tools\start-emulator.ps1` (headless Wear OS Small Round on the host GPU, one fallback to
  `angle_indirect`, never the software GPU); `-Stop` shuts it down. No window: screenshots with
  `adb -s emulator-5554 shell screencap -p /sdcard/x.png` + `adb pull`, touches with `adb shell input tap`.
- Inject an utterance (debug): `adb -s emulator-5554 shell am broadcast -a com.wgo.navigator.debug.UTTERANCE -p com.wgo.navigator --es text_b64 <UTF-8 Base64>`.
  Benchmark build: `adb shell am start -f 0x20000000 -n com.wgo.navigator.bench/com.wgo.navigator.MainActivity --es wgo_utterance_b64 <Base64>`.
- Simulate GPS: `adb -s emulator-5554 emu geo fix <lon> <lat>` (longitude first), or replay a route with
  `.\tools\sim_drive.ps1 -FromLogcat -SpeedKmh 36` (the route the debug build is following, from the `WGO_ROUTE` dump);
  `-OffsetAfterM 150 -OffsetM 80` leaves the route, `-StopAfterM` stops early, `-Points` / `-PolylineFile` take any
  polyline.
- GPS loss: `adb shell cmd location set-location-enabled false` (and `true` to recover).
- No internet: `adb shell svc wifi disable; adb shell svc data disable` (and `enable`).
- Counters: `adb logcat -s WGO_METRICS` (a snapshot every 30 s and at the end of each trip).

## Budgets and measured values

Measured on `emulator-5554` (Wear OS Small Round, API 37, host GPU). Battery cannot be measured on the emulator; the
counters below stand in for it. The real Galaxy Watch5 Pro is the final judge.

| budget | target | measured (2026-11-10) | result |
|---|---|---|---|
| Screen 01 idle | 0 location / 0 network / 0 recogniser | two WGO_METRICS snapshots 30 s apart identical; no location request in `dumpsys location` | PASS |
| Location while navigating | exactly 1 request, NAVIGATING profile | 1 fused request, HIGH_ACCURACY, 1-2 s | PASS |
| Location after END / arrival | stops within 2 s | GPS provider OFF 150 ms after the END tap | PASS |
| Location with the screen off | stops within 2 s (current policy) | OFF 1.29 s after screen off; back 0.39 s after wake, no false GPS-lost | PASS (P6 changes the policy) |
| Network while navigating | 0 except reroutes, <= 1 per 20 s | only route requests on reroutes, spaced 20.0-37 s; map tiles: about 500 KB received for a 3.6 km drive (not counted by WgoMetrics) | PASS (tiles reported) |
| Engine cost per fix | (report) | 1.98 us on the JVM (300-point route); 250-490 us in the debug build on the emulator (debuggable, includes state publishing) | reported |
| Frames | loop only while something animates | radar at the 30 fps ambience tier (about 30 fps measured); loops stop under the opaque surfaces | PASS |
| Jank, navigation with a moving route | <= 3 % | legacy janky median debug 2.1 % / benchmark 1.3 %; slow UI thread 1.5 % / 0.6 % (the deadline metric reads about 96 % for 30 fps frames on this emulator: artifact) | PASS (legacy) |
| Jank, GPS LOST overlay | <= 3 % | legacy median debug 0.0 % / benchmark 3.8 % (2.2-4.3) | borderline (benchmark over) |
| Jank, ARRIVED transition | <= 3 % | legacy median 0.8 % both | PASS |
| Jank, END NAVIGATION? transition | <= 3 % | about 39 frames, 2-3 slow UI frames (5.1 %), p99 101 ms debug / 44-46 ms benchmark | FAIL (see below) |
| PSS while navigating | <= 140 MB | 105-118 MB | PASS |
| PSS growth minute 1 -> minute 10 | <= 15 MB | +3.0 MB (moving drive, 111.4 -> 114.4 MB); +0.3 MB over 14 min of stationary navigation | PASS |
| logcat `*:E` | clean | 0 crash-buffer entries, 0 error lines from the app process in the buffers checked | PASS (partial: buffers were cleared between runs) |
| Benchmark APK / cold start | (report) | 2.52 MB; cold start median 366 ms (debug 4024 ms) | reported |

Counters for a 6.5-minute moving drive (3.6 km at 36 km/h; the planned 10-minute run was cut short by the test harness): 192 fixes, 191 engine updates, 1 geocode (551 B), 1 route (7.7 KB), 0 reroutes, 178 map re-projections, about 500 KB received and 20 KB sent by the app in total (mostly map tiles).

Known misses: the END NAVIGATION? surface spends 2-3 slow UI-thread frames composing over the radar (the forced reduce-motion switch recomposes the radar subtree); to be fixed with the P6 HUD rebuild. The pre-P6 navigation screen still has 36 dp zoom buttons (39 x 37.5 real dp) and a 41 real dp CANCELAR chip whose label measures fit 109.1.

## Top remaining power and resource risks (real watch)

1. **No guidance with the wrist down**: location stops with the screen off; a navigator needs a foreground service with an ongoing activity and ambient mode (P6), and that service will keep the GPS on, so its interval policy becomes the main battery cost.
2. **High-accuracy GPS at 1-2 s for the whole trip**: no minimum distance (needed so standing still is not a GPS loss) means steady wakeups; P6 should lower the rate on long straights and in ambient.
3. **Map tiles over the network**: about 140 KB per km with no prefetch or size cap visible; on LTE/Bluetooth-proxied phones this costs radio time. A tile prefetch along the route or a smaller zoom range would help.
4. **Radar animation at 30 fps while navigating**: the sweep and flow run for the whole trip; on the Exynos W920 this keeps the GPU awake. Ambient (P6) must stop them, and the interactive screen could drop them on long straights.
5. **Fused-location dependency and cached origins**: the origin may be a fused fix up to 60 s old, and without Play services the platform fallback has not been measured on a real watch.
