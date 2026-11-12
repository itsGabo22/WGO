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

## Git

* Atomic Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, ...), one concern per commit.
* Stage files explicitly; never `git add .`.
* Work on feature branches; never push `main` or `master` without being asked.
