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
| `audio` | P9: `AudioCapture` (an `AudioRecord` wrapper, `VOICE_RECOGNITION` 16 kHz mono; opened at the press on a background thread, released on every exit on its own thread, buffers zeroed), `SpeechEndpointer` (framework-free energy VAD: calibrated noise floor, minimum statistics for a steady background, 150 ms start, 500 ms pre-roll, 900 ms end, 10 s cap, 6 s no-speech), `WavEncoder`. | `AudioCapture` yes; the endpointer and encoder are plain Kotlin |
| `voice` | P9: `VoiceLadder` (rung 1 `CloudVoiceInput` = capture + `TranscriptionClient` + `TranscriptGuard`; rung 2 the unchanged `VoiceInputChain`; silence ends with "No te oí", any other cloud failure continues on rung 2 with "DILO DE NUEVO"). `VoiceInputChain`: in-app `SpeechRecognizer` first, the system speech dialog as fallback; voice level; test-build utterance injection. `InAppVoiceInput` drives a `Recognizer` (the platform one behind a small interface, faked in tests) and follows `RecognizerErrors`: language-model errors go online through the tag list, busy/disconnected/cold-network errors get one silent fresh instance 400 ms later, transcribed words end the session instead of a restart, a restart after "ready" emits `Restarting` (the UI returns to preparing and `SpeakNowCue` re-arms), never two instances at once; `RecognizerMemory` keeps "no offline model" and the working tag across app restarts. The session starts at pointer-down on the mic (preparing state), never at idle. | yes |
| `ai` | `TieredInterpreter`: `LocalIntentParser` + `PlaceAliases` (Pasto nicknames) first, the OpenAI-compatible `LlmInterpreter` only for low-confidence requests; `AiCallBudget` (200 calls/day, cool-down on 429). | plain Kotlin + DataStore for the budget |
| `geo` / `route` / `net` | `NominatimGeocoder` (1 request/s, cache, local retry), `OsrmRouter` (OSM-DE foot/car, OSRM demo fallback for car), `Polyline`, `ModeEstimate`, the minimal `HttpClient` over `HttpURLConnection` (no HTTP/JSON framework). | `org.json` from the platform |
| `service` | **`NavigationHost`** (process-wide owner of a trip: session, the trip's location request, radar data, trip haptics, screen-off power policy), **`NavigationService`** (foreground service, type `location`), `NavigationNotification` (channel + ongoing activity), `OngoingStatus`, `ProgressSpeed`. | yes (the engine inside stays framework-free) |
| `haptics` | `HapticEvent` and `HapticPlayer` (predefined effects). Trip haptics are played by `NavigationHost`; pipeline haptics by `MainActivity`. | yes |
| `metrics` | `WgoMetrics`: debug-only counters, frame counts per screen, the `WGO_METRICS` log line every 30 s, the `WGO_ROUTE` dump. No-ops outside debug builds. | yes |
| `ui` | Theme (palette, type, motion, responsive scale, the shared `WgoClock`), components, the radar (`RadarMinimap`, `LiveRadarSource`), the screens and surfaces, `Strings.kt` (ES/EN). The navigation screen (P6-B) has an adaptive HUD whose state is a pure function (`HudRules`: distance hysteresis 200/240 m, tap override until the maneuver changes, 4 s after a reroute, COMPACT while exploring) and an explore mode: a one-finger pan handled in Compose over the map (the edge swipe-back keeps its strip), clamped to the route box + 400 m, back to following 10 s after the last touch or on recenter. Explore is UI state only: the engine, the service and the single location request never see it. | Compose |
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

## Navigation service, ambient mode and power policy (P6-A)

```
 Activity (visible / ambient)                 process-wide                              foreground service
 ┌──────────────────────────┐   commands   ┌──────────────────────────────┐  start   ┌──────────────────────────┐
 │ MainActivity             │ ───────────> │ NavigationHost               │ ───────> │ NavigationService        │
 │  AmbientLifecycleObserver│  start/end/  │  NavigationSession (engine)  │          │  type=location, not      │
 │ AppViewModel (observes)  │  retry       │  one LocationSource request  │ <─────── │  sticky; notification +  │
 │  navState, radar, ambient│ <─────────── │  trip haptics (HapticPlayer) │  active  │  OngoingActivity (status │
 │  snapshot, lastEnd       │   flows      │  NAVIGATING / AMBIENT profile│  = false │  <= 1 post per 5 s)      │
 └──────────────────────────┘              └──────────────────────────────┘  → stop  └──────────────────────────┘
```

- **Start**: when the route is ready the ViewModel calls `host.start()`, which starts the service with
  `startForegroundService` from the foreground app; the service calls `startForeground(location)` at once (measured
  0.2-1.2 s after the trip starts). No background-location permission: the service always starts while the app is
  visible. If the platform refuses the start, the trip runs only while the app is visible (the P5 behaviour).
- **Stop** (service, notification and location request together): END, arrival, the service losing its permission
  (`pm revoke` kills the process; it is not sticky, so nothing restarts), and **task removal**: swiping WGO away from
  Recents ends the trip (`onTaskRemoved`), because that gesture means "close the app" on a watch and a trip with no
  way back to its UI except the notification would be surprising.
- **Observe only**: the Activity/ViewModel never own the trip. A reopened Activity (same process) reconnects to the
  live trip, or to its unread arrival. After process death the app starts at Home (unchanged).
- **Haptics**: one collector of the engine events, in the host: spec section 8 mapping, played from the process the
  service keeps in the foreground, so they fire once each with the screen off, in ambient or in the background
  (default attributes: usage TOUCH; delivered on the API 37 emulator and the API 36 Galaxy Watch5 Pro).
- **Notification permission** (API 33+): asked once, lazily, the first time a route is requested; a denial never
  blocks the trip (the foreground service runs; its notification and ongoing activity are only hidden).
- **Ambient**: the observer enables ambient for the whole Activity. During a trip, `AmbientNavigationScreen` replaces
  the radar and map (no frame loop), redrawn on the ambient tick or when the maneuver / guidance state changes;
  leaving ambient rebuilds the live screen with no transition. On other screens ambient draws nothing (black) and a
  voice session is cancelled.
- **Location policy**: screen on and interactive = NAVIGATING (P5-D: 1 s inside 80 m of a maneuver, 3 s fast on long
  straights, else 2 s). Screen off, ambient or background = AMBIENT, high accuracy with
  `interval = clamp((distanceToNext - 250 m) / speed / 2, 2 s, 10 s)`, **1 s only when the maneuver is <= 8 s away
  in time** (distance / speed), rounded down to 2/3/4/6/8/10 s so the request is replaced only when the step changes;
  unknown speed: 1 s inside 80 m else 2 s; standing still: 10 s. Speed = the fix's speed, or the speed along the
  route when fixes report none. The engine's GPS-lost rule widens to two expected intervals + 2 s (no false GPS-lost
  cue between 10 s fixes) and is switched before the request slows down.

Back: Intro skips; the pipeline goes one step back (routing back to the place or mode choice, earlier stages home);
Navigation asks END NAVIGATION? (Back again = KEEP); Arrived goes home; Settings goes home; the picker returns to
Settings. Ending or arriving stops the engine and the location request and clears the saved destination. After process
death the app starts at Home: a trip never resumes by itself.

## Data flow

```
press ──(no network: P10)──> "Sin conexión" surface (no capture, no recognizer, no system dialog; REINTENTAR)
press ──(FAST mode, notice accepted, online, under cap)──> AudioCapture ──SpeechEndpointer──> FLAC while recording (WAV fallback, memory only)
      │                                                         └─(no speech in 6 s)──> "No te oí" (nothing sent)
      │   audio ──TranscriptionClient (provider, 3 s connect / 4 s + 1 s per 32 KB, max 10 s, 1 retry on connect failure)──> verbose_json
      │        └─TranscriptGuard (no_speech_prob, avg_logprob, compression, silence phrases, prompt echo)──> transcript
      │   cloud failure with a network ──> "DILO DE NUEVO" ──> SpeechRecognizer chain (P8) ──> transcript
      │   cloud failure and the network gone ──> "Sin conexión" (P10)
      └─(SYSTEM mode / no key / offline / cool-down / cap)──> SpeechRecognizer chain ──> transcript
SpeechRecognizer chain: on device; network recognizer only when the device has no offline model; system dialog last
transcript ──LocalIntentParser + PlaceAliases──> place query (+ mode)        (no network)
          P10: destination clause, filler, verb anywhere, nickname prefix, kind of place, street address; heard phrase
          P10: no clear winner / vague / category / address ──> pick list "¿CUÁL?" (up to 3), else Confirm place
          └─(low confidence only)──> LLM provider: transcript text only, no location, no identifiers
place name (+ landmark hint) ──> my places (private, on the watch) ──> local places index (Overture, on the watch)
          └─(no strong local match)──> Nominatim (identifying User-Agent, 1 request/s, <= 3 per utterance) ──> <= 2 places
          (ranking and acceptance rules: docs/PLACE_SEARCH.md)
origin fix + destination + mode ──> OSRM (OSM-DE) ──> up to 3 routes (fastest active), polyline + steps each (P11)
fixes (one request per trip, screen on or off) ──> NavigationHost ──> NavigationSession ──> NavState / NavEvent ──> UI + haptics + ongoing activity
          ├─(on a known alternative for 3 fixes)──> switch, no network (P11)
          └─(off route or wrong way; >= 8 s apart, back-off 8/16/32/60 s, 3 failures in a row; never offline)──> OSRM again
```

## Routing and reroute (P11)

- **Router**: `OsrmRouter.routes(from, to, mode, bearing)` asks one OSRM `route` with `alternatives=true`, `steps=true`,
  `overview=full`, `geometries=polyline` (and `bearings=<course>,45;` when a course is given) and returns up to 3
  routes, fastest first; walking on the FOSSGIS OSM-DE foot server, driving on its car server, the OSRM demo as the
  driving fallback (server error or timeout). "Best route" is the fastest by the road network: **no live traffic** in
  free data.
- **Engine** (`NavigationEngine`, framework-free): the active route plus its alternatives (each a `RouteGeometry`).
  On every usable fix: snapping and progress on the active route as in P5; when the fix is more than 40 m from it, the
  distance to each alternative's polyline; 3 fixes in a row within 20 m of one alternative -> that alternative becomes
  active (no request; `NavEvent.Rerouted`, the old route joins the alternatives). Wrong way: on the route but the GPS
  course more than 135° from the route's direction at >= 3 m/s driving (>= 1 m/s walking) for 3 fixes -> off route.
  Off route -> a reroute request with the fix and, above 3 m/s, its course as the bearing hint; requests at least 8 s
  apart, the wait doubling after each failure (max 60 s), LOST ROUTE after 3 failures in a row. The session checks
  connectivity before each request (no listener): without a network nothing is sent, the engine keeps the old route in
  `OffRoute(offline = true)` and asks again as soon as a network is back; offline waits never count as failures.
  `selectRoute(i)` (the RUTA chip) makes a known route active the same way, with no request.
- **Usage policies** (read 2026-11-13): FOSSGIS (https://routing.openstreetmap.de/about.html, full terms in German at
  https://www.fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen/): attribution and a "fix the map" link, a valid
  User-Agent, **at most 1 request per second**, no scraping, no heavy use. The OSRM demo server
  (https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy): no excessive use, ODbL attribution and OSRM as
  the route source, a valid User-Agent, no guarantees, access may be withdrawn. WGO sends one request per trip start
  plus reroutes (>= 8 s apart, never a loop) with the identifying User-Agent; the one `OsrmRouter` in flight at a time
  keeps it under 1 per second. **A public release needs its own or a paid router** (`docs/RELEASE_CHECKLIST.md`).


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
| `release` | the owner's own watch (not published) | P7: R8 + resource shrinking, 3.10 MB, cold start 1.07-1.62 s on the Galaxy Watch5 Pro; signed with the debug key; contains the `local.properties` keys, so never distribute it (`docs/RELEASE_CHECKLIST.md`) |

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
- Real watch (no `geo fix`): `.\tools\mock_drive.ps1 -Serial <ip:port> -Setup -Hold "lat,lon"` before a trip, then
  `-FromLogcat -SpeedKmh 36` (debug build; fused mock mode, partial wake lock, stops 30 s after the route's end).
  Wi-Fi debugging on the Galaxy Watch5 Pro drops when its screen turns off (it switches to Bluetooth), so
  screen-off runs read the device's logcat afterwards.
- Ambient: `adb shell input keyevent KEYCODE_SLEEP` with always-on enabled (`KEYCODE_WAKEUP` leaves it); frames drawn
  in ambient are counted under `Ambient` in WGO_METRICS.
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
| Location after END / arrival | stops within 2 s | P6-A: END tap -> GPS registration removed 90 ms, service + notification gone 280 ms; arrival (screen off) -> service stopped 24 ms | PASS |
| Location with the screen off (P6-A) | keeps guiding | one request stays open in the foreground service (screen off, ambient or background); P5-D removed it 0.86 s after screen off | PASS |
| Stop on task removal / permission loss (P6-A) | within 2 s | swipe from Recents: trip ended 0.22 s, GPS off 0.27 s, service gone 0.69 s; `pm revoke`: process killed, GPS off 0.23 s, no crash, reopens at Home | PASS |
| Trip haptics with the screen off (P6-A) | spec section 8, once each | emulator 9/9 and later full drives all `finished`; Galaxy Watch5 Pro 17/17 `finished` (usage TOUCH) | PASS |
| Fixes per minute, screen off (P6-A) | below the fixed 2 s policy (30/min) | recorded Udenar route (1235 m, 10 maneuvers): drive 36 km/h 29.1/min (first P6-A rule: 36.4); walk 5 km/h 20.8/min over 9 min; simulation: walk 28.2 (was 40.6), long straights 11.5 walk / 14.5 drive | PASS (drive marginal on dense routes) |
| Ambient frames (P6-A) | no frame loop | 12 frames in 8.5 min of ambient (radar: about 30 fps) | PASS |
| Reroute with the screen off (P6-A) | >= 20 s apart | off route -> request at 2 s at once; reroutes 20.8 s apart | PASS |
| PSS while navigating, screen off (P6-A) | <= 140 MB, growth <= 15 MB | 105.1-105.4 MB; minute 1 -> minute 9: -0.2 MB | PASS |
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
| Benchmark APK / cold start | (report) | 2.52 MB; cold start median 366 ms (debug 4024 ms). P6-A: 2.536 MB with the service, ongoing activity and androidx.wear (+16 KB); cold start not re-measured | reported |

P7 measurements (Galaxy Watch5 Pro unless noted, 2026-11-11):

| budget | target | measured | result |
|---|---|---|---|
| Home idle | 0 location / 0 network / 0 recogniser / 0 TTS | identical snapshots 30 s apart; no service; the intro's recognizer warm-up released 8 s after the intro (`recognizer_session=0`) | PASS |
| Navigating | 1 location request + 1 service | `locActive=1`, 1 service; after END 0 / 0 (`reason=Ended`) | PASS |
| PSS while navigating | <= 140 MB | 97.4 -> 99.9 MB over the first minute (debug) | PASS (minute 9 not re-measured) |
| Map tiles (CARTO base + labels at 1x) | <= 100 KB/km | 80.8 KB/km, 2.78 km at 60 km/h, cold cache (2x labels: 109) | PASS |
| Jank, navigation with two tile layers | <= 3 % | emulator benchmark build, legacy: 0.00 / 2.68 / 2.43 %, then 0.22 / 0.22 / 1.32 % | PASS |
| Arrival animation (2.4 s) | report | emulator: about 22 ms per frame while it plays (the intro baseline the same day: 22 ms, 100 % legacy jank), 0 frames once it ends; not measured on the watch | emulator-limited |
| Places index load | report | benchmark build ~0.5 s, +4 MB heap; debug build 20-30 s | reported |
| Place search | <= 3 Nominatim requests | owner places 0-1 requests (index), not-found 2 | PASS |
| TTS | report | Samsung engine, 26 voices (es-US, es-ES offline; no es-CO), init 1.2-4.3 s, 8 phrases on a drive all `SUCCESS` | reported (speech not heard) |
| Lint / tests | 0 errors / all pass | lint 0 errors, 0 NewApi, 13 warnings; 264+ unit tests pass | PASS |

### First mic press (P8 live session, Galaxy Watch5 Pro, release build, 2026-11-11)

The owner pressed the mic and said "llévame a la cooperativa"; "cold" = recognizer host and app force-stopped, app
relaunched (intro warm-up runs; "bound" when the press came while it still held the service). Times are from the
press (`adb logcat -s WgoVoice`). "Error shown" = the user saw an error surface and had to press again.

| round | press | ready | what happened | result | error shown |
|---|---|---|---|---|---|
| A (before) | cold, warm-up released | 1.10 s | error 2 at 6.3 s, nothing heard -> silent restart, ready 7.8 s | text at 11.6 s | no (bounce to preparing) |
| A | cold, bound | 1.23 s | - | text at 6.6 s | no |
| A | cold, bound | 1.14 s | - | text at 6.9 s | no |
| A | warm | 0.67 s | **no match at 6.0 s, no speech detected** | "not understood" | **yes** |
| A | warm x2 | ~1.2 s | - | text at 4.6 / 6.0 s | no |
| A | cold, system dialog | - | dialog shown 15.7 s, never listened (owner), cancelled | no text | **yes** |
| B (quiet restarts) | cold | 1.98 s | first press closed by the app at 1.1 s (cause not logged yet); next: 8 s cap without a word -> quiet restart; then speech without words, no match | "not understood" | **yes** |
| B | cold | 1.37 s | - | text at 7.0 s | no |
| B | cold, bound | 0.69 s | first press closed by the app at 1.4 s; next: speech without words, no match | "not understood" | **yes** |
| B | warm | 0.72 s | - | text at 5.4 s | no |
| B | warm | 0.67 s | two no matches without speech -> 2 quiet restarts | text at 19.2 s | no |
| B | warm, silent 8 s on purpose | 0.5 s | no match -> quiet restart | text at 15.0 s | no |
| C (speech-without-words rule) | cold | 1.13 s | error 2 -> restart; speech without words -> quiet restart; **`cancel reason=stop` at 15.4 s** (display timeout) | closed | yes (pressed again) |
| C | cold | 1.46 s | error 2 -> restart; 3 no matches with no speech detected | "not understood" at 26.7 s | **yes** |
| C | cold | 1.11 s | - | text at 7.0 s | no |

Strategy chosen: the in-app recognizer for every press, including the first after a cold start. The system dialog lost
its only cold trial (it never listened), and the watch re-selects its recognition service by itself, so the dialog stays
the fallback for a device without a selected service. Fixes, each from a logged failure: quiet restarts until a word is
transcribed (2 per session, separate one-restart budgets for network and service codes); the screen stays on while a
session runs (the 30 s display timeout stopped the Activity mid-phrase; verified: flag on during a 33 s silent session,
cleared at its end); recognizer revival (a press waited 20 s for a service whose dead process the system kept
postponing under memory pressure, then showed "mic could not start"; the TTS-engine poke fires at 4.06 s on the watch,
the backoff itself could not be reproduced on demand).

Remaining gap (reported, not fixed): right after a cold error 2, the online recognizer sometimes detects no speech for
three sessions in a row while the owner speaks (round C, press 2); the next press hears at once. Cause unknown (the
recognizer gives no more than code 7). The two early "closed by the app" presses of round B predate the cancel-reason
log; round C shows the same symptom as `reason=stop`.

### P8 additions

- **Language model in place choice** (`ai/PlaceChooser`, `ai/LlmPlaceChooser`, `geo/ChoiceTriggers`): two roles on the
  same endpoint as the interpreter, both validated: *expansion* (up to 3 official names as text when no nickname
  matched and the candidates are weak) and *choice* (an id among up to 5 real candidates when the ranking is
  ambiguous). Shared daily budget and 429 cool-down, 2.5 s timeout, cache by utterance, at most 2 model calls per
  utterance including the interpreter's (`PlaceRequest.aiCallsUsed`). Nickname landmarks (`PlaceAliases`) carry the
  official Nominatim name and the index's own name; the landmark prior ranks destinations above businesses.
- **Map orientation** (`MapOrientation`, Settings): north up by default (map bearing 0, the chevron turns, CARTO's
  raster names on); heading up (the map turns, raster names off, `LandmarkLabels` / `LandmarkLabelLayer` draw upright
  landmark names from the places index, re-selected only when the camera changes).
- **Marquee** (`WgoMarqueeText`): the clamped text switches to one scrolling line in the same box for 2 cycles, then
  rests; static under reduced motion and covering surfaces.
- **Voices** (`VoiceCatalog`, `VoiceSettingsRules`, `VoicePitch` / `VoiceRate`): duplicates removed across engines, the
  default engine bound first in the picker; pitch, speed and engine persisted and applied by `SpeechEngine`.

P8 measurements (2026-11-12):

| check | where | result |
|---|---|---|
| Home idle: 0 location / 0 network / 0 recogniser / 0 TTS | emulator, debug | two snapshots 30 s apart identical, `locActive=0`, `recognizer_session=0`, no service, no TTS engine bound | 
| Navigating: 1 location request + 1 service | emulator | `locActive=1`, 1 service |
| HOME mid-trip | emulator | service kept, fixes 16 -> 25 |
| Reroute | emulator | 2 reroutes after leaving the route by 90 m |
| END | emulator | service and location gone within 2 s |
| Arrival | emulator | `Arrived`, service gone; arrival animation 2.97 % janky (debug, deadline metric) |
| Notifications denied | emulator | the trip runs, 1 service |
| Ambient | emulator | static ambient navigation, 3 frames in 35 s, location kept |
| Location revoked mid-trip | emulator | process ends, no crash, reopens at Home |
| `logcat *:E` | emulator | 0 crash-buffer lines, 0 error lines from the app |
| Navigation jank, north up | Galaxy Watch5 Pro, benchmark build, real GPS | 1,286 frames, 2.72 % over 34 ms (the radar runs at the 30 fps ambience tier; the deadline metric reads 95 %, its known 30 fps artefact); PSS 53 MB |
| Marquee jank | emulator, debug | 2.79 % janky while scrolling, 0 frames after the 2 cycles |
| Long names | watch | "Cooperativa Nariñense de Militares Retirados" and a long not-found title scroll in full |
| Tiles | emulator, cold cache, 2.81 km at 60 km/h | 1x labels 87.0, `@2x` labels 126 (chosen), base + labels `@2x` 202 KB/km |
| Lint / unit tests | | 0 errors, 0 NewApi, 13 warnings (as in P7); all unit tests pass |

Not measured in P8: the screen-off drive and the 9-minute walk with PSS at minute 9 (P6-A numbers stand), heading-up
jank on the watch, a heading-up "driving south" screenshot on the watch (heading-up labels verified on the emulator).

Counters for a 6.5-minute moving drive (3.6 km at 36 km/h; the planned 10-minute run was cut short by the test harness): 192 fixes, 191 engine updates, 1 geocode (551 B), 1 route (7.7 KB), 0 reroutes, 178 map re-projections, about 500 KB received and 20 KB sent by the app in total (mostly map tiles).

Known misses: the END NAVIGATION? surface spends 2-3 slow UI-thread frames composing over the radar (the forced reduce-motion switch recomposes the radar subtree); to be fixed with the P6 HUD rebuild. The pre-P6 navigation screen still has 36 dp zoom buttons (39 x 37.5 real dp) and a 41 real dp CANCELAR chip whose label measures fit 109.1 (P6-B replaces them: 40 dp zoom with 48 real dp touch, an 86x32 CANCELAR at fit 99.0 with 48 / 60 real dp touch).

P6-A froze the radar data under the modal surfaces; P6-B measured it on the benchmark build (emulator, host GPU, 3 interleaved rounds): END NAVIGATION? 0.00 / 0.00 / 0.43 % legacy jank with the freeze and the same without, GPS LOST 0.88 / 0.22 / 0.00 % with and 0.00 % x3 without, so it was dropped (loops still stop under the surfaces). Fixed in P6-B (was an open issue on the real watch): the first microphone press after a cold start could wait about 6 s (the system recognizer process cold-starts) and then show "Sin internet": the offline recognizer has no es-CO pack (error 12) and the online retry raced the service unbinding (error 11, mapped to Offline); RETRY works.

### Instant-capture mic (P9 live session, Galaxy Watch5 Pro, release build, 2026-11-12)

Before (STEP 0, P8 release, 3 automated cold presses, nobody speaking): the recognizer got ready 2.17 / 2.36 / 2.17 s
after the press, then failed with codes 7, 2 and 12 and quiet restarts; the sessions ended after 19-26 s with "not
understood" twice and "no internet" once (Wi-Fi was up). P8's live table (above): text 4.6-26.7 s after the press.

After (P9, the owner pressing and speaking; times from the press, `adb logcat -s WgoVoice`). "drawn" = the frame
that shows HABLA AHORA; "audio" = first non-zero sample; "end->text" = end of speech (endpointer) to the transcript.

| press | drawn | audio | speech start | clip | upload | end->text | rung / outcome | error shown |
|---|---|---|---|---|---|---|---|---|
| cold, first press (notice) | 278 ms | 0.83 s | - | - | - | - | the owner pressed the mic after ACEPTAR, which stopped the capture the notice had started | "No te oí" (fixed: c2fc305) |
| cold | 40 ms | 1.00 s | 1.66 s | 2.70 s / 86 KB | 0.92 s (prewarm 2.2 s) | 1.31 s | cloud, full phrase (24 chars) | no |
| cold | 45 ms | 1.02 s | 1.75 s | 2.76 s / 88 KB | 0.72 s (prewarm 2.3 s) | 1.08 s | cloud | no |
| warm | 40 ms | 0.81 s | 1.49 s | 7.82 s / 250 KB | timeout 6.25 s | 25 s | cloud -> system (3 instances) | no (mic covered by the owner, he said) |
| warm | 45 ms | 0.74 s | 1.74 s | 4.60 s / 147 KB | 2.28 s | 2.43 s | cloud | no |
| warm | 28 ms | 0.69 s | 1.20 s | 2.50 s / 80 KB | 1.04 s | 1.12 s | cloud | no |
| TV / music | 31 ms | 0.81 s | 1.56 s | 3.56 s / 114 KB | - | - | cancelled by the owner (CANCELAR) | - |
| TV / music | 26 ms | 0.76 s | 2.52 s | 2.42 s / 77 KB | 0.65 s | 0.80 s | cloud | no |
| TV / music | 32 ms | 0.80 s | 1.90 s | 3.08 s / 99 KB | 2.36 s | 2.49 s | cloud (37 chars) | no |
| warm | 27 ms | 0.74 s | 1.58 s | 2.98 s / 95 KB | 0.93 s | 1.02 s | cloud | no |
| silence (8 s) | 29 ms | 0.81 s | 3.75 s (noise burst) | 0.98 s / 31 KB | 0.60 s | - | "Gracias." rejected by the guard -> system rung | no (now "No te oí": 898072b) |
| warm | - | - | 2.31 s | 0.98 s / 31 KB | 0.45 s | - | "Gracias." rejected -> system rung, text at 7.7 s | no |

- Press -> HABLA AHORA drawn: 26-45 ms on every press, 278 ms on the first frame after the notice: all within the
  300 ms budget (target 100 ms met except that one).
- End of speech -> text: median **1.12 s** over the 7 completed cloud transcriptions (0.80-2.49 s); budget 1.5 s met.
  The spread is the watch's Wi-Fi upload (0.45-2.36 s for 31-147 KB; the provider answered in about 0.4 s).
- No error surface on any press where the owner spoke with the mic uncovered. No clipped first syllable: the cold
  presses transcribed the whole phrase although audio flows only 0.7-1.0 s after the press (`startRecording` 0.35-0.66 s,
  then about 200 ms of zero samples on this watch).
- Silence: the 6 s no-speech path makes no request (emulator, and the watch press at 14:46 in STEP 0 testing); the
  guard rejected both "Gracias." hallucinations live. The connection prewarm now starts only at speech start
  (3b46bf7), so a silent press makes no request at all.
- Offline: not run on the watch (airplane mode drops Wi-Fi adb); on the emulator with Wi-Fi and data off the press
  went straight to the system chain (`rung=system reason=offline`) and ended with a clean "voice unavailable" at 6 s.
  Network failures during an upload are covered by the unit tests (`TranscriptionClientTest`, `VoiceLadderTest`).
- SISTEMA mode: the Settings row was toggled on the emulator; the owner's SISTEMA press stayed on the cloud rung (the
  row was not switched), so SISTEMA on the watch is not verified live.
- Prewarm (kept): the first HTTPS request of a process took 2.2-2.3 s on the watch, later ones 0.17-0.9 s.

Remaining gap: uploads of large clips on the watch's Wi-Fi (a 250 KB clip timed out at 6 s; 147 KB took 2.3 s). A
compressed upload (Opus in Ogg or FLAC through `MediaCodec`, in memory) would cut the bytes 2-10x; not done in P9.

### Long phrases, strict choice and no network (P10, Galaxy Watch5 Pro, release build, 2026-11-12/13)

Before (P10 STEP 0, field log of the P9 release): 4 cloud presses OK (WAV 69-198 KB, upload 0.85-1.57 s); the owner's
long sentence reached the cloud and came back as text, then the local rules kept "amigo quiero ir a la cooperativa es lo
único que te pido" as the place and a "Cooperativa ..." business won. Without any network: `rung=system reason=offline`
-> offline recognizer (5 s to ready), errors 12 / 2 / 12 / 12 -> "unavailable" at 15.2 s -> the system's white "La red
no está conectada" dialog; the next presses opened that dialog at once.

After (P10 release, the owner speaking; times from the press, `adb logcat -s WgoVoice`):

| press (what the owner said) | capture | WAV -> FLAC | drain | upload | end of speech -> text | result |
|---|---|---|---|---|---|---|
| "llévame a la calle 6A #36-41 barrio San Vicente porque ahí es mi casa" | 10.0 s (cap) | 306 -> 148 KB | 31 ms | 0.78 s (timeout 8.5 s) | 0.97 s | text OK; then the place search picked a business 220 m away (fixed: fa8060a) |
| "llévame a alguna droguería que quede en el centro porque no conozco Pasto ..." | 10.0 s (cap) | 312 -> 133 KB | 51 ms | 1.45 s (timeout 8.1 s) | 1.70 s | text OK; pick list of 3 pharmacies, not checked against "centro" (fixed: b5ac8b5) |
| "llévame por favor al mejor restaurante de la avenida de los estudiantes ..." | 10.0 s (cap) | 306 -> 131 KB | 100 ms | 0.95 s (timeout 8.0 s) | 1.39 s | text OK; "No encontré", suggesting "Peluquería canina mi mejor amigo" (fixed: b5ac8b5) |

- Compression: FLAC 2.1-2.3x smaller on real speech (2.2x on the emulator), identical transcripts on the emulator's
  A/B; drain 31-100 ms on the watch (budget 150 ms); every 10 s clip uploaded within its scaled timeout (8.0-8.5 s).
- The same three phrases through the real pipeline afterwards (text, real model and Nominatim, real index, JVM trace):
  pharmacies nearest the centre ("Drogueria Plaza De Nariño, Carrera 25 #19-52, Centro" among them), restaurants on
  "Av. de los Estudiantes" (Ambrosía, Bennie's pizza, Herencia Restaurante), and "Calle 6A" in three neighbourhoods
  (OpenStreetMap has no house numbers or "San Vicente" for it), all as the "¿CUÁL?" pick list, no model call.
- The owner's own sentences and the STEP 0 golden phrases are unit tests (`LongPhraseStrictChoiceTest`).
- No network (emulator, Wi-Fi and data off): "Sin conexión" on screen at the press (`offline_at_press` +0 ms, one
  haptic), no capture, no recognizer, no system dialog; REINTENTAR still offline shows it again; with the network
  back it goes straight to listening (cloud rung) without restarting the app.

Not verified live on the watch: the re-run of the three phrases with the fixes, the vague phrase, Wi-Fi off with the
phone's Bluetooth network, Wi-Fi and Bluetooth both off, and a network lost mid-capture (the owner was not available for
the second round; the no-network path is verified on the emulator and by `VoiceLadderTest`).

## Top remaining power and resource risks (real watch)

1. **GPS for the whole trip, wrist down included** (P6-A): the foreground service keeps one high-accuracy request; the
   screen-off interval (2-10 s, 1 s only <= 8 s before a maneuver) is now the main battery cost. On dense routes it
   stays near 30 fixes/min; battery on the real watch is not measured yet (Wi-Fi debugging drops with the screen off).
2. **Request swaps**: each interval step change replaces the request (21 registrations on a 2-min drive); cheap in the
   fused provider, but worth watching on the real watch.
3. **Map tiles over the network**: about 140 KB per km with no prefetch or size cap visible; on LTE/Bluetooth-proxied phones this costs radio time. A tile prefetch along the route or a smaller zoom range would help.
4. **Radar animation at 30 fps while navigating**: the sweep and flow run while the screen is on and interactive; ambient (P6-A) stops them. The interactive screen could drop them on long straights (P6-B HUD rebuild).
5. **Fused-location dependency and cached origins**: the origin may be a fused fix up to 60 s old, and without Play services the platform fallback has not been measured on a real watch.
