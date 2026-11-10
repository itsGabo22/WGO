# WGO: what leaves the watch

WGO runs on the watch. Speech recognition, interpretation of easy requests, the radar and all settings stay on the
device. Only three kinds of request ever leave it, and only when the user has just asked for a destination.

| # | Data sent | Sent to | When | Never sent |
|---|---|---|---|---|
| 1 | The transcript of the utterance (text, at most 200 characters, request under 1 KB) | The remote interpreter (default: Groq, OpenAI-compatible API) | Only for hard requests: the local rules were not confident (< 0.75), an API key exists in `local.properties`, the network is up, the 60 s rate-limit cool-down is not active and fewer than 200 remote calls were made today. At most one call per utterance, no retries. | Coordinates, device or account identifiers, audio |
| 2 | The destination text, plus a bounding box of +/-0.2 degrees around the last known fix (or the Pasto centre) | Nominatim (OpenStreetMap geocoder) | Once per committed utterance; at most 2 more when the user cycles OTHER. Results are cached in memory (20 queries). No search-as-you-type. | Audio, the transcript beyond the place words, identifiers |
| 3 | The origin and destination coordinates and the travel mode | The OSM-DE routing servers (OSRM, FOSSGIS) | Once per route request (one request in flight; cancelled when the screen is left) | Transcript, identifiers |

Speech to text uses the watch's own recogniser (in-app `SpeechRecognizer`, or the system speech dialog as a
fallback). WGO prefers the offline model; when the device has no offline model for the language, the platform
recogniser may use its own online service, under the platform's privacy terms. WGO never stores or uploads audio.

Persisted on the watch (DataStore): theme, language, map style, the active destination and mode, and the daily
remote-interpreter counter (date and count). No transcript is persisted. Debug builds log counters and request sizes
(never URLs, bodies, keys or transcripts) under the `WGO_METRICS` tag.

## The remote interpreter (default provider)

* Endpoint: `https://api.groq.com/openai/v1/chat/completions`, model `openai/gpt-oss-120b` with low reasoning effort
  (both overridable through `LLM_BASE_URL` / `LLM_MODEL` in the git-ignored `local.properties`). Chosen over
  `openai/gpt-oss-20b` on 2026-11-10 by a live comparison on the hard-request corpus: same data sent, the larger model
  never invented a place name (the smaller one invented 2 of 8) at a similar latency and token cost.
* The system prompt states that the transcript is data, not instructions; the answer can only select one of five
  intents (Navigate, ChangeMode, Cancel, Repeat, Unknown), a place string (cleaned, at most 80 characters) and a mode.
  It cannot trigger anything else.
* Provider data use (from the provider's "Your Data" page, console.groq.com/docs/your-data, read 2026-11-09): "By
  default, Groq does not retain customer data for inference requests"; inputs and outputs may be temporarily logged
  for reliability troubleshooting or suspected abuse and kept "for up to 30 days, unless legally required to retain
  longer"; any customer can enable Zero Data Retention in the console's Data Controls. That page does not discuss
  training; the provider's privacy page and services agreement are reported to exclude customer inputs from model
  training (secondary sources; confirm in the current agreement before any public release).
* The API key is compiled into the debug APK through `BuildConfig`. Such an APK must not be distributed; a public
  release needs a server-side proxy or a per-user key.

### Swap-in providers (only `local.properties` changes)

| Provider | `LLM_BASE_URL` | Notes |
|---|---|---|
| Groq (default) | `https://api.groq.com/openai/v1` | Verified live 2026-11-09 (models list and one completion). |
| Gemini, OpenAI-compatible endpoint | `https://generativelanguage.googleapis.com/v1beta/openai` | Base URL verified against Google's OpenAI-compatibility page (2026-11-09); not called. Google's Gemini API terms say unpaid-tier prompts may be used to improve its products: **not re-verified in this run**, check the current Gemini API terms. |
| OpenRouter, `:free` models | `https://openrouter.ai/api/v1` | Base URL verified against OpenRouter's quickstart (2026-11-09); the `:free` model naming and the free-tier logging/training policy were **not verified** in this run. Some free routes may log or train on inputs: check the model page. |

Set `LLM_MODEL` to a model id of the chosen provider. With no `LLM_API_KEY` the app makes no remote interpreter call
at all and works with the local rules only.

## Map data, geocoding and routing

* Map tiles, place search and routes come from OpenStreetMap data, © OpenStreetMap contributors, available under the
  Open Database License (ODbL). The app shows "© OpenStreetMap" on the place-confirmation surface.
* Nominatim usage policy (operations.osmfoundation.org/policies/nominatim): an identifying User-Agent
  `WGO/<version> (<contact>)` from `WGO_CONTACT` in `local.properties` (a placeholder `WGO-dev/0.1` without it; a real
  contact is required before any public use), at most one request per second across the app (a global rate limiter),
  results cached, no autocomplete.
* Routing: `routing.openstreetmap.de` (FOSSGIS OSM-DE servers, foot and car profiles), intended for light,
  non-commercial use; a public release must move to its own OSRM instance or a commercial router.
