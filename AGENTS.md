# WGO (Where We Go) — Agent Rules

WGO is a **100% native Wear OS app** (Kotlin + Jetpack Compose for Wear OS; no web, no WebView) that gives voice-first, one-touch navigation to drivers and pedestrians in Pasto, Colombia. It is a solo project.

## Stack (do not change versions unless the task says so)

* Kotlin 2.1.20, AGP 8.7.0, Gradle wrapper (8.10.2)
* Jetpack Compose BOM 2024.02.01
* `androidx.wear.compose:compose-material:1.3.0` (Material 2)
* minSdk 30, compileSdk 34
* osmdroid for the map

## Build and validation

* Build: `./gradlew assembleDebug` (Windows: `.\gradlew.bat assembleDebug`). **Exit code 0 is required** before a step counts as done.
* Gradle needs JDK 17–21. If `JAVA_HOME` points to a newer JDK, set `JAVA_HOME` for the session only; never commit it or change global settings.
* Validate before considering any step complete.

## Do not touch

* `legacy_web_app/` and `stitch_wgo_smartwatch_ai_navigator/` stay on disk, are git-ignored and are never edited, committed or pushed.

## Design source of truth

* `docs/design/` is the single source of truth. Read `docs/design/WGO_DESIGN_SYSTEM.md` before any UI work.
* Colors, type, spacing and motion come from `docs/design/tokens.json` (via `WgoPalette`, `WgoMotion`, the theme typography). **Never hard-code colors in screens.**
* Two themes: **Daylight** (light, the source theme) and **Night** (dark, AMOLED). Both must work; every screen is checked in both.
* Typography: Space Grotesk (variable font; weights via `FontVariation`).

## Hard design rules

* No sidebars or hamburger menus.
* No soft or blurred shadows; elevation is crisp rings only.
* No sharp-cornered shapes (pill or circle).
* Touch targets >= 48 dp; massive, one-touch interaction.
* Text contrast >= 4.5:1.
* Nothing flashes faster than 3 Hz.
* Wearable-first: design for round screens (227 dp large, 192 dp small).

## Code conventions

* All user-facing strings go through `ui/Strings.kt` (`StringKey`). Spanish (ES) is the default, English (EN) the second language.
* Haptics are emitted from ViewModels, never from composables.
* Map styles are original genre-inspired looks. Never use the name, logo, icon or art of any video game, in code, strings, comments or commit messages.
* `mapas_extra/` holds local visual reference screenshots only. It is git-ignored: never commit, copy or trace them; take only general traits (palette mood, layout ideas) and re-draw them as original art.

## Target device

* Primary target: **Samsung Galaxy Watch5 Pro** (round, 450 x 450 px, ~321 ppi, about 225 dp, Exynos W920, 1.5 GB RAM, Wear OS 3.5 = API 30). No rotating bezel and no crown: the app must be fully usable by touch and the two hardware buttons; a crown or bezel may only be an optional extra.
* minSdk is 30: do not call APIs above 30 without a version guard (`Build.VERSION.SDK_INT`). Lint must show 0 `NewApi` findings.
* Also supported: Wear OS Small Round (192 dp) and Large Round (227 dp); everything scales uniformly (see `docs/design/WGO_DESIGN_SYSTEM.md` section 4.1).

## UI conventions

* Geometry is always written in reference dp (the 227 dp canvas) and never converted manually: `WgoResponsive` scales the density once. Never use raw pixels.
* Hit areas use `Modifier.wgoMinTouch()` (48 REAL dp); hairlines use `Dp.atLeastRealDp()`.

* One `WgoClock` (`rememberWgoClock()`) per screen, created at screen level and passed to every looping piece. Pieces read it only inside draw / `graphicsLayer` lambdas, so nothing recomposes per frame. Do not add `InfiniteTransition`s.
* Respect reduce motion everywhere: read `LocalWgoReduceMotion` (provided by `WgoTheme`), use static states for loops and `orCut()` / 120 ms cuts for transitions.
* Icons are hand-drawn `ImageVector`s in `WgoIcons` (24 grid, 2 dp round stroke). Do not add `material-icons-extended` usages in new code.
* Haptics: ViewModels emit `HapticEvent`s through a `SharedFlow`; `MainActivity` plays them. Trip haptics (spec §8 turn, GPS-lost and arrival rows) are played by `NavigationHost` instead, so they work with the screen off. Composables never touch the vibrator.
* Every commit must compile (`assembleDebug` exit 0), so history stays bisectable. Changes that must land together go in one commit.

## Rules learned in P6-P7

* **Data and privacy**: the language model returns text only (name, hint, category); coordinates in its replies are
  ignored. User coordinates, private places (`assets/my_places.json`, git-ignored) and the places index never go to
  the model or any server. Never invent coordinates for a real place. Nominatim: <= 1 request/s, <= 3 per utterance.
* **Keys**: `MAP_TILE_KEY`, `LLM_API_KEY` and anything in `local.properties` reach the code only through `BuildConfig`;
  never log, print or commit them, nor full tile URLs. CARTO answers a bad key with HTTP 200 and a placeholder image:
  keep the hash check in `MapTiles`.
* **Assets**: never name an asset `*.gz`; the Android packager gunzips it and drops the extension (use `.gzip`).
* **Performance**: the debug build is 10-50x slower for CPU work on the watch (index load 20-30 s vs 0.5 s); measure
  speed on the benchmark or release build. Jank comes from the emulator with the host GPU, compared on the same day
  with a baseline screen (the intro), because the emulator's speed drifts between sessions.
* **Text-to-speech**: engines differ; Samsung TTS reports ISO-3 locales without the region (`spa`), so the voice
  region comes from the voice name (`es-US-SMTf00`). Speech is spoken by `NavigationHost` only; never play TTS
  through the PC.
* **Map labels**: labels come from their own tile layer with a hue-free stretch, never through the style filter
  (`WgoTileFilter.labelArray`); contrast is checked by `MapContrastTest` for both tile bases.
* **Watch testing**: adb lives in `%LOCALAPPDATA%\Android\Sdk\platform-tools`; `adb shell svc power stayon true` while
  testing; Wi-Fi debugging drops with the screen off (read logcat afterwards); skip the intro with a tap at (225, 60)
  px (the centre hits the mic); `input swipe` moves about twice its distance; the debug `MAP_STYLE` receiver only
  persists (restart the app). Restore the owner's settings after a test session.
* **Windows tooling**: PowerShell 5.1 splits `git commit -m` at double quotes (use `git commit -F <file>`); most
  sources are CRLF, so scripted string replacements must match `\r\n` (or use an editor that does).

## Rules learned in P8

* **Voice**: the release build logs the voice path under `WgoVoice` (timings, codes, lengths; never a transcript): read
  it with `adb logcat -s WgoVoice` before guessing. No recognizer error reaches the user before a word is transcribed
  (quiet restarts); the screen stays on while a voice session runs; a recognizer process held in the system's restart
  backoff is revived through its package's TTS engine.
* **Coroutines on ART**: a large `suspend fun` whose local `suspend fun` captures mutable locals passed the JVM tests and
  crashed at launch on the device (`VerifyError`). Keep per-call state in a small class, and launch the debug build on
  the emulator after refactoring suspend code; unit tests are not enough.
* **Places and the model**: the model may only expand a nickname into official names (text) or pick among real
  candidates by id; ids are validated, coordinates and distances are never sent, at most 2 model calls per utterance.
  Every Pasto nickname must resolve to a real Nominatim record and/or index row before it is added.
* **Map**: north up is the default (raster street names stay upright); heading up hides the raster names and draws
  upright landmark labels clear of every control. CARTO labels are `@2x` (2 screen px per tile px on the Watch5 Pro);
  the base stays 1x; budget 160 KB/km.
* **Long texts**: use `WgoMarqueeText` (2 cycles, static under reduced motion), never a bare ellipsis the owner cannot
  read.
* **Watch testing**: the benchmark build (`com.wgo.navigator.bench`) takes utterances with
  `am start --activity-single-top ... --es wgo_utterance_b64 <b64>`; wake the screen (`KEYCODE_WAKEUP`) before each
  step, the watch is not always on its charger. Uninstall the bench app afterwards.
* **Lint**: the `ProduceStateDoesNotAssignValue` check misreads conditional assignments; use `LaunchedEffect` + state.

## Rules learned in P9

* **Voice ladder**: rung 1 is the app's own capture (`audio/AudioCapture` + `SpeechEndpointer`) with cloud
  transcription (`voice/TranscriptionClient`, Whisper on the LLM provider); rung 2 is the P8 `VoiceInputChain`, which
  `VoiceLadder` wraps and must not change. Silence ends with "No te oí" and no request; any other cloud failure goes to
  rung 2. The microphone is held only between a press and the end of speech, with the Activity visible, released on
  every exit (on its own thread: `AudioRecord.stop` blocked 1.1 s on the watch); never a microphone service type.
* **Audio privacy**: audio only in memory (never on disk), buffers zeroed after the upload; only that press's clip is
  sent; the Whisper `prompt` holds only `PlaceAliases.landmarkNames` (never my places); `WgoVoice` logs sizes, codes,
  times and lengths only (a test asserts no key, text, URL or audio in the log).
* **Whisper on silence**: it invents text ("Gracias.", or the prompt read back); keep `TranscriptGuard` and the
  endpointer in front of the network; `whisper-large-v3-turbo` reports `no_speech_prob` 0, so the default is the full
  model.
* **Watch audio facts (Galaxy Watch5 Pro)**: audio flows 0.7-1.0 s after `AudioRecord` creation (`startRecording`
  0.35-0.7 s, then ~200 ms of zero samples); no NoiseSuppressor/AGC/AEC; the first HTTPS request of a process takes
  ~2.3 s (prewarm at speech start); uploads over the watch's Wi-Fi vary 0.45-2.4 s for 30-150 KB.
* **Emulator microphone**: the AVD's `hw.audioInput` is the PC microphone; run `adb -s emulator-5554 emu avd
  hostmicoff` before any capture test so the owner's room is never recorded.

## Rules learned in P10

* **No network, no voice**: check connectivity at the press (`NET_CAPABILITY_INTERNET`, a Bluetooth-proxied network
  counts, no listener); without one show "Sin conexión" and never start a capture, a recognizer or the system speech
  dialog. The system-recognizer rung runs only with a network; an offline failure must not latch the chain to the dialog.
* **Long phrases**: keep the clause with the navigation verb, strip filler at both ends, allow the verb anywhere, cut the
  place at clause words ("es", "porque"); a remote phrase must be made of the user's own words (`groundedIn`).
* **Strict choice**: the model's pick must share a content word with the destination (or its nickname's names); no clear
  winner, a vague request, a category or an address -> the pick list ("¿CUÁL?"), never one silent guess.
* **Kinds of place and addresses**: a category word (singular or plural) is a category search near the hint's point,
  never a name match ("mejor" matched "Peluquería canina mi mejor amigo"); a street address goes to Nominatim only
  (OpenStreetMap in Pasto has no house numbers).
* **Upload**: FLAC via `MediaCodec`, fed while recording (600 ms lag), in memory, WAV fallback; Opus changed transcripts
  of short clips. Timeout 4 s + 1 s per 32 KB, max 10 s.
* **Tooling**: never change device location settings or test providers on the emulator without the owner (he declined
  it); PowerShell `Set-Content` mangles UTF-8 accents in Kotlin sources (write files with the editor or Python); the
  watch's Wi-Fi adb hangs when its screen sleeps: use short `timeout`s and stop instead of retrying.

## Rules learned in P11

* **Places**: index v2 = Overture within ~25 km of Pasto, confidence >= 0.2, brand column, rebuilt reproducibly
  (`--save-extract` / `--source`); keep `PlaceCoverageBenchmarkTest` green (top-1 >= 75 %, top-3 >= 90 %, no automatic
  pick of a missing place) after any matching or index change, and regenerate its data with
  `tools/build_place_benchmark.py` after an index rebuild. Spelling variants (mr = mister...) live in
  `PlaceText.canonical`, applied at load, not in the Python builder.
* **Chains**: a name chain needs the query's words EXACTLY ("Max" is not "Maxiel"), a said category word must match
  every branch, copies within 60 m keep the better-matched name (Overture brands "Mister Pizza" as Mister Pollo), and an
  exact whole name with no brand stays one place. Branch distances are computed on the watch only.
* **Geocoders**: Nominatim + Photon share 3 requests per utterance; Photon gets a fixed box and centre, never the
  position; its fuzzy results count only when they match the words locally (tier >= 2). No Google APIs.
* **Routes**: OSRM gives alternatives only when they differ clearly (driving pairs in Pasto often have none; a walking
  pair had one): never promise them. Reroutes >= 8 s apart with back-off; offline sends nothing and never counts toward
  LOST ROUTE. The emulator's `geo fix` has no GPS course: the wrong-way rule is unit-tested only.
* **Compass**: one rotation-vector listener, only while `CompassPolicy.shouldListen` (trip + visible + not ambient);
  heading to the radar at <= 10 Hz and >= 2 deg; the azimuth uses the 12 o'clock + into-the-screen axes (no flip when
  the wrist is raised).
* **Lint**: `ComposableStateFlowValueDetector` crashes on a StateFlow `.value` read inside a stdlib lambda (Kotlin 2.1
  metadata): read `.value` into a local first.
* **Tooling**: bash heredocs with quotes break in this shell: write edit scripts to the scratchpad and run them; a
  worktree in the scratchpad needs `ANDROID_HOME` and `rm -rf` + `git worktree prune` (long paths).

## Git

* Atomic Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, ...), one concern per commit.
* Stage files explicitly; never `git add .`.
* Work on feature branches; never push `main` or `master` without being asked.
