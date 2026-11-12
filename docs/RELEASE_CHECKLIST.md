# Release checklist (before any public release)

WGO is not published. The `release` build type (R8 + resource shrinking, 3.10 MB, cold start 1.07-1.62 s on the
Galaxy Watch5 Pro) is signed with the **debug key** and contains the keys from `local.properties`: it is for the
owner's own watch only. Everything below must be done before an APK or bundle leaves this machine.

## 1. Keys out of the APK: a small proxy (MANDATORY)

**P9 makes this mandatory, not optional**: every mic press in the default mode uploads audio with `LLM_API_KEY`, so an
extracted key would let anyone transcribe on the owner's account (2,000 requests/day per model on the current account).
No APK with a key inside may leave this machine.

`LLM_API_KEY` and `MAP_TILE_KEY` are compiled into `BuildConfig`; anyone can extract them from an APK. A public build
calls a proxy instead, which holds the keys server-side. Minimal design (serverless, for example on Vercel; no code
in this repo yet):

| route | forwards to | limits |
|---|---|---|
| `POST /v1/interpret` | the OpenAI-compatible `chat/completions` endpoint (today Groq, `openai/gpt-oss-120b`) | body <= 1 KB; per install 200 calls/day (the app's own `AiCallBudget` stays), per IP 30/min; 429 on excess |
| `POST /v1/transcribe` (P9) | the OpenAI-compatible `audio/transcriptions` endpoint (today Groq, `whisper-large-v3`) | multipart body <= 400 KB (10.5 s WAV); only the fields the app sends (model, language, temperature, response_format, prompt, file); per install 300/day (the app's own counter stays), per IP 20/min; 429 on excess; the audio is streamed through, never stored or logged |
| `GET /v1/tiles/{layer}/{z}/{x}/{y}.png` | `basemaps.cartocdn.com/{layer}/...?key=` (layers `dark_nolabels`, `light_only_labels` only) | z 12-18 and the Pasto box only; `Cache-Control` passed through (30 days) so the CDN absorbs repeats; per IP 600 tiles/min |

- Authentication: an install token (random, created on first run, sent as a header), plus Play Integrity if abuse
  appears. No user account.
- The proxy logs counts only: no transcripts, no coordinates beyond the tile index, no tokens.
- App side: `LLM_BASE_URL` and the tile base URL point at the proxy; `LLM_API_KEY` / `MAP_TILE_KEY` stay empty in
  release builds (the OSM fallback must never be the production path: see section 3).

## 2. Map tiles (CARTO)

- Free tier with a key: 5 M tiles/month non-commercial, 1 M/month commercial (owner's decision, P7). Measured traffic:
  80.8 KB/km on a cold cache with base + labels at 1x (P7, watch); P8 fetches the labels at `@2x` for sharpness:
  126 KB/km on the emulator (87.0 with 1x labels on the same drive), budget 160 KB/km. The tile count is unchanged; the P7 estimate below is in tiles. Estimate (tiles were not counted directly): about 9 tiles/km
  (base ~15 KB + labels ~3 KB per position), so a 20 km/day user fetches about 5,400 tiles/month on a cold cache and
  the free commercial tier covers roughly 185 such users (more with the 20 MB on-watch cache and the proxy's CDN).
- Commercial use (ads, paid app, company use) needs a commercial CARTO plan: confirm the current terms at carto.com
  before launch.
- Attribution (P8, spec 9.1): "© OpenStreetMap · © CARTO" along the bottom arc of the dial for 2.5 s when a trip's map
  appears, always while exploring and on the map picker, and in full in Credits. The owner accepted this reduced
  placement **for the prototype only**. **Before any public release a persistent, or clearly reachable, attribution is
  mandatory** (CARTO's basemap terms and the OpenStreetMap attribution guidelines expect it wherever the map is shown):
  for example the bottom-arc line visible for the whole trip, or an always-present "©" control that opens the
  credits. Re-read both current terms before launch.

## 3. OpenStreetMap services (public instances are not for production)

- **Nominatim** (osm.org): max 1 request/s, identifying User-Agent with a real contact (`WGO_CONTACT`), no heavy use
  (operations.osmfoundation.org/policies/nominatim). A public app must use its own Nominatim, or a commercial geocoder
  whose terms allow showing results on a non-Google map.
- **Routing** (`routing.openstreetmap.de`, FOSSGIS): light, non-commercial use only; production needs its own OSRM or
  a commercial router.
- **OSM tiles** (the fallback without a key): the OSM tile usage policy forbids heavy app use; production must never
  depend on it.

## 4. Places index (Overture)

- Update procedure: `docs/THIRD_PARTY_DATA.md` (`tools/build_places_index.py`, about quarterly). Keep the notices in
  that file and in the Credits screen in step with the release used.
- Licences: CDLA-Permissive-2.0 (Meta, Microsoft and others), Apache-2.0 (Foursquare rows, with the NOTICE link),
  CC0 (AllThePlaces). Shipping the asset = sharing the data: the notices must ship too (they do, in Credits).

## 5. Privacy

- A public privacy policy (URL in the Play listing) based on `docs/PRIVACY_DATA_FLOW.md`: what leaves the watch (the
  transcript text to the language model provider for hard requests, place names to the geocoder, origin and
  destination to the router, tile requests to CARTO, phrase text to a network TTS voice if one is chosen), what never
  does (position to the language model, private places, audio), retention (none on the watch beyond settings).
- Re-check the LLM provider's data terms (no training on inputs, retention) at release time.
- **Audio transparency (P9)**: the store listing and the privacy policy must say that, in the default FAST (CLOUD)
  mode, the audio of what the user says after tapping the mic is sent to the transcription provider (Groq) and not
  stored by WGO; that SYSTEM mode keeps recognition on the watch's recogniser; and the provider's retention (up to 30
  days only for abuse or reliability, unless Zero Data Retention is on). Enable ZDR on the provider account before
  launch. The in-app first-use notice and the Credits lines stay in step with the policy.
- Play Data safety form: location (approximate and precise, app functionality, not shared); audio: "Voice or sound
  recordings" collected for app functionality, processed ephemerally, sent to a service provider (the transcription
  provider) acting on WGO's behalf, optional (SYSTEM mode); app activity (none collected).

## 6. Google Play and Wear OS requirements (checked 2026-11-11)

Sources: https://developer.android.com/google/play/requirements/target-sdk and
https://developer.android.com/docs/quality-guidelines/wear-app-quality

- **Target API: Wear OS apps must target Android 15 (API 35) or higher since 31 Aug 2026** (re-read 2026-11-12 on the
  page above; the extension deadline, 1 Nov 2026, has passed). **Done in P8**: `targetSdk 35` (compileSdk stays 34: no
  API 35 symbol is needed; build, lint and the regression pass). Re-check the Android 15 behaviour changes if a later
  feature uses foreground-service types other than `location`.
- WO-V1 user font size, WO-V2 48 dp touch targets (met), WO-V3 swipe to dismiss (met; the navigation screen keeps its
  explicit CANCELAR), WO-V4 ongoing activity during navigation (met, P6-A), WO-V6 launcher icon and name (met, P7),
  WO-V8 scroll bar on scrolling screens (met: Settings, voice picker, Credits), WO-V13 black background (the
  Daylight theme uses white: a reviewer may flag it; Night is compliant), WO-V14 12 sp essential / 10 sp minimum
  text, WO-V15 splash icon matching the launcher icon (the system splash uses the new icon: verify), WO-V16 round
  screens down to 192 dp (met, fit tests).
- 64-bit and 16 KB pages: the only native library in the APK is DataStore's `libdatastore_shared_counter.so` (arm64-v8a,
  armeabi-v7a, x86, x86_64); P8 checked every copy: ELF LOAD alignment 0x4000 (16 KB), stored uncompressed at a 16 KB
  aligned offset. Re-check after dependency updates.
- Listing: description without "Android Wear", 1:1 screenshots without frames, localized ES/EN.

## 7. Signing and build

- Create an upload key (kept outside the repo), enable Play App Signing, sign `release` with it through a
  `signingConfigs` entry that reads from environment variables, never from committed files.
- Bump `versionCode` / `versionName`; build an App Bundle (`bundleRelease`).
- Keep `benchmark` for performance runs (debug-signed, `.bench` suffix).
