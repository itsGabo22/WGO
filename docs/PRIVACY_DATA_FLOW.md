# WGO: what leaves the watch

WGO runs on the watch. Interpretation of easy requests, the radar and all settings stay on the device. Only the
requests below ever leave it, and only when the user has just pressed the microphone or asked for a destination.
Speech recognition (P9) runs in the cloud in the default RÁPIDO (NUBE) / FAST (CLOUD) mode (row 0) and on the watch's
own recogniser in SISTEMA / SYSTEM mode.

| # | Data sent | Sent to | When | Never sent |
|---|---|---|---|---|
| 0 | P9: the audio of ONE utterance, in memory, as FLAC (P10: lossless, about 2.2x smaller, encoded while recording) or as a WAV when the encoder fails (16 kHz mono PCM16, from 500 ms before the detected speech to 300 ms after it, at most about 10.5 s / 340 KB), plus the fields `model`, `language` (the app language), `temperature` 0, `response_format` and a vocabulary `prompt` made only of public landmark names from the verified `PlaceAliases` table | The transcription endpoint of the same OpenAI-compatible provider and key as row 1 (default Groq, `POST /openai/v1/audio/transcriptions`, model `whisper-large-v3`) | Only after an explicit mic press, in RÁPIDO (NUBE) mode, after the one-time notice was accepted, with the app visible, and only when the on-watch endpointer heard speech (silence sends nothing). Network up (checked at the press; P10: without one nothing is recorded or sent and WGO shows "Sin conexión"), no 429 cool-down (60 s), fewer than 300 transcriptions today (own counter). One retry only when the connection itself failed and a network is present; total timeout 4 s + 1 s per 32 KB, at most 10 s. | Coordinates, device or account identifiers, my places, history, other presses, audio from before the press or after the end of speech |
| 1 | The transcript of the utterance (text, at most 200 characters, request under 1 KB) | The remote interpreter (default: Groq, OpenAI-compatible API) | Only for hard requests: the local rules were not confident (< 0.75), an API key exists in `local.properties`, the network is up, the 60 s rate-limit cool-down is not active and fewer than 200 remote calls were made today. No retries. | Coordinates, device or account identifiers, audio |
| 1b | P8 place choice: the utterance text, P10: the destination words (text taken from the utterance), and for a choice up to 5 candidate places as public text (name, category, address, a list id `c1`..`c5`) | The same remote model | Only when the place search is weak or ambiguous (`docs/PLACE_SEARCH.md`, "Landmarks, nicknames and the language model"): name expansion returns text names, choice returns one id from the list. **At most 2 model calls per utterance in total** (1 + 1b together), same daily cap and cool-down, 2.5 s timeout. | The user's position, distances, coordinates of any place, my places, identifiers, audio |
| 2 | The destination text (the place name, then possibly the landmark hint alone), plus a bounding box of +/-0.2 degrees around the last known fix (or the Pasto centre) | Nominatim (OpenStreetMap geocoder) | Only when the watch's own lists (my places, the local places index) have no strong match; at most 3 requests per utterance, none when OTHER cycles. Results are cached in memory (20 queries). No search-as-you-type. | Audio, the transcript beyond the place words, identifiers, my places |
| 3 | The origin and destination coordinates and the travel mode | The OSM-DE routing servers (OSRM, FOSSGIS) | Once per route request (one request in flight; cancelled when the screen is left) | Transcript, identifiers |

P10: the remote interpreter is also the destination extractor for long phrases: it may only copy the destination words
from the transcript (a code check drops any word the user did not say); the place chooser answers an id only for a
candidate that clearly is the destination, and the code ignores an id whose name shares no word with it. Category
requests ("alguna droguería que quede en el centro") and street addresses ("calle 6A #36-41, barrio San Vicente") send
Nominatim only the hint or the street text with ", Pasto" (row 2); the category search itself runs on the watch's index.

Speech to text (P9). In RÁPIDO (NUBE) mode (the default) the app records the utterance itself (`AudioRecord`, only
between the press and the end of speech, released on every exit) and sends that clip to the transcription provider
(row 0). The audio lives only in memory: it is never written to disk and its buffers are zeroed after the upload or on
cancel. Whisper's text then enters the same interpreter as before. If the cloud transcription fails (offline,
timeout, rate limit, daily cap, an answer rejected as a hallucination) the watch's own recogniser takes over (in-app
`SpeechRecognizer`, or the system speech dialog) and the user is asked to say it again. In SISTEMA mode only the
watch's recogniser is used: WGO prefers its offline model; when the device has none for the language, the platform
recogniser may use its own online service, under the platform's privacy terms. The mode is a Settings row
(RECONOCIMIENTO); the first press in cloud mode shows a one-time notice (ACEPTAR keeps it, USAR SISTEMA switches to
SISTEMA).

Spoken guidance (P7) uses the watch's text-to-speech engine. WGO prefers offline voices; if the chosen voice is a
network voice, the engine may send the phrase text (for example "Gira a la derecha por Carrera 35") to its provider,
under the engine's privacy terms. No position, place name lists or identifiers are added by WGO.

Persisted on the watch (DataStore): theme, language, map style, the speech recognition mode and the notice answer,
the active destination and mode, and the daily remote-interpreter and transcription counters (date and count). No
audio and no transcript is persisted. The release build logs the voice path under `WgoVoice` (times, byte counts,
HTTP codes, guard reasons and transcript LENGTHS; never audio, text, URLs or keys). Debug builds log counters and request sizes
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
* Transcription (P9) uses the same provider and key: `POST https://api.groq.com/openai/v1/audio/transcriptions`, model
  `whisper-large-v3` (`STT_MODEL` in `local.properties` overrides it). The provider's "Your Data" page
  (console.groq.com/docs/your-data, read 2026-11-12) lists `/openai/v1/audio/transcriptions` among the inference
  endpoints: "By default, Groq does not retain customer data for inference requests"; inputs and outputs may be logged
  for reliability or abuse investigations for "up to 30 days", and Zero Data Retention (Data Controls) disables that.
  Data is held in Google Cloud buckets in the United States. The page says nothing about training on customer data.
  Recommended before wider use: enable ZDR on the account.
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
