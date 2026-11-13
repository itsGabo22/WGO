# WGO (Where We Go)

A 100% native **Wear OS** navigator for drivers and pedestrians in Pasto, Colombia: say where you want to go, confirm
with one tap, and follow a north-up (or heading-up) radar map with haptic (and optional spoken) turn cues, screen on
or off.
Kotlin + Jetpack Compose for Wear OS; built and tested on a Samsung Galaxy Watch5 Pro.

| | | |
|---|---|---|
| ![Confirm place](docs/screenshots/confirm-place.png) | ![Navigation, NEON style](docs/screenshots/navigation-neon.png) | ![Arrival](docs/screenshots/arrival.png) |
| Confirm the place ("llévame a Cubanitos") | Navigation, one of 8 map styles | Arrival with the trip summary |
| ![Heading-up landmark labels](docs/screenshots/heading-up-landmarks.png) | ![Voice picker](docs/screenshots/voice-picker.png) | ![Scrolling name](docs/screenshots/marquee-name.png) |
| Heading up: upright landmark names (emulator) | Voices by engine, no duplicates (watch) | A long name scrolls (watch) |

Map data © OpenStreetMap contributors, map tiles © CARTO, places © Overture Maps Foundation (see Credits in the app
and `docs/THIRD_PARTY_DATA.md`).

## What it does

- **Voice first, instant mic (P9)**: "HABLA AHORA" shows within 50 ms of the press; the app records the utterance
  itself, finds the end of speech on the watch and has it transcribed in the cloud (Whisper on Groq, same key as the
  language model; text about 1.1 s after you stop talking, median on the watch). Silence sends nothing ("No te oí");
  invented silence phrases are rejected; if the cloud fails, the watch's own recognizer takes over ("DILO DE NUEVO").
  A one-time notice explains what is sent; Settings > RECONOCIMIENTO switches to SISTEMA (the recognizer only).
  P10: the audio is sent as FLAC (about 2.2x smaller); without any network the press shows WGO's own "Sin conexión"
  screen at once (never the system's dialog). Long phrases keep only the destination ("por favor amigo, quiero ir a
  la cooperativa, es lo único que te pido" -> Universidad Cooperativa); a kind of place near a landmark ("alguna
  droguería que quede en el centro") or a street address lists real options under "¿CUÁL?", and Confirm place shows
  the words it heard, so nothing is guessed silently.
  Easy requests are understood on the watch, hard ones by a language model (text only, never coordinates).
- **Real places**: your private places, an offline index of 7,984 places within about 25 km of Pasto (Overture, P11:
  brands included), then OpenStreetMap search (Nominatim, then Photon for misspelled names); "X de la Y" uses Y as a
  landmark hint; far results are never picked first; Pasto nicknames ("la cooperativa", "la terminal", "Unicentro")
  and a landmark prior; the language model may only expand a nickname or a brand into official names or pick among
  real candidates by id, never with your position (`docs/PLACE_SEARCH.md`).
- **Chains and speech slips (P11)**: "llévame por favor a Mr. Pollo" finds the Míster Pollo chain ("Mr", "Mister",
  "Míster" and even a heard "mi pollo" are the same) and lists its 3 nearest branches with distance and address
  ("¿CUÁL?", OTRO 1/3); "... el más cercano" goes straight to the nearest one. Names also match by Spanish sound and
  small spelling slips. Measured on 120 test requests: the right place first 95.5 % of the time, in the first three
  99.1 % (P10: 75.5 % / 80.9 %), and an invented place is never picked by itself.
- **Best route, alternatives and live reroute (P11)**: the fastest route by the road network (no live traffic: free
  data has none), plus up to two alternatives drawn lighter with their extra minutes ("+4 MIN"); the RUTA 1/2 chip
  switches with no network. Driving onto an alternative switches to it at once; any other road reroutes from where you
  are (pointing the new route your way, at most every 8 s); the route behind you disappears and the distance and time
  update live. Without network the old route stays ("SIN CONEXIÓN · RUTA NO ACTUALIZADA") until it is back.
- **Compass (P11)**: standing or walking, the arrow and the heading-up map follow the watch's compass (fused with the
  GPS course when moving fast); at the start the HUD arrow shows where to turn to face the route. The sensor runs only
  while the navigation screen is on.
- **Legible radar map**: CARTO tiles recoloured into 8 original styles (Day / Night), sharp street names on top;
  north up by default (names always upright) or heading up with upright landmark labels; long texts scroll.
- **Trip in a foreground service**: haptics per turn, optional spoken guidance (OFF / AUTO with a headset /
  ALWAYS) with a choice of voice, engine, pitch and speed, ambient screen, ongoing activity, celebratory arrival.

## Honest limits

- Free data has gaps: a place missing from Overture and OpenStreetMap cannot be found; add it to your private places
  (`docs/PLACES.md`). OpenStreetMap has no house numbers in Pasto, so a street address finds the street.
- No live traffic: "best" is the fastest route by the road network. OSRM offers alternatives only when they differ
  clearly; many short driving trips in Pasto have none.
- No offline voice: without a network the app says so instead of listening.
- Ambient (dimmed) screen: Wear OS redraws the app only about once a minute there, so the distance shown can be stale;
  the turn haptics stay on time (they come from the trip service). Proposed fix in `docs/ARCHITECTURE.md` (backlog).
- The public Nominatim, Photon and OSRM servers are for light use; a public release needs its own or paid services and
  a key proxy (`docs/RELEASE_CHECKLIST.md`).

## Build

Requirements: Android SDK (compileSdk 34, targetSdk 35), **JDK 17-21** for Gradle 8.10.2 (JDK 21 recommended; set it for the
session only):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat assembleDebug          # debug build with test hooks and metrics
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat assembleRelease        # R8 build, debug-signed: for your own watch only
```

Optional keys in the git-ignored `local.properties` (the app works without them):

| key | effect without it |
|---|---|
| `MAP_TILE_KEY` | CARTO map tiles; without it (or if CARTO refuses it) the map uses OpenStreetMap tiles |
| `LLM_API_KEY` (+ `LLM_BASE_URL`, `LLM_MODEL`) | the remote interpreter for hard requests; without it only the on-watch rules |
| `WGO_CONTACT` | contact in the User-Agent sent to OpenStreetMap services (required before any public use) |

Private places: copy `app/src/main/assets/my_places.example.json` to `my_places.json` (git-ignored); see
`docs/PLACES.md`. Keys end up inside the APK: never distribute a build made with them (`docs/RELEASE_CHECKLIST.md`).

Install on a watch over Wi-Fi debugging: `adb connect <ip:port>`, then
`adb install -r --no-streaming app\build\outputs\apk\release\app-release.apk` (measured on the watch: the release build
starts in about 1.1 s instead of 4 s and loads the places index in about 0.5 s instead of 20-30 s).

## Documentation

| file | contents |
|---|---|
| `AGENTS.md` | rules for anyone (human or agent) changing the code |
| `docs/ARCHITECTURE.md` | modules, state machine, service, data flow, emulator tools, measured budgets |
| `docs/design/WGO_DESIGN_SYSTEM.md`, `docs/design/tokens.json` | the design spec (source of truth) and tokens |
| `docs/PLACE_SEARCH.md`, `docs/PLACES.md` | place search rules; private places and fixing OpenStreetMap |
| `docs/PRIVACY_DATA_FLOW.md` | what leaves the watch |
| `docs/VOICES.md` | the voices on the watch and how to get more |
| `docs/THIRD_PARTY_DATA.md` | data sources, licences, the places index update |
| `docs/RELEASE_CHECKLIST.md` | what must happen before a public release |
| `docs/MERGE_PLAN.md` | branches and the merge plan |
