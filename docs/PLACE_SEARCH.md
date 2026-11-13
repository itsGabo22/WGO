# Place search: sources, ranking and acceptance rules (P7)

How WGO turns the place words of an utterance ("llévame a Cubanitos de la Panamericana") into the one or two
candidates of the confirm-place surface. Spec of the surfaces: `docs/design/WGO_DESIGN_SYSTEM.md` section 7.1.
Data notices: `docs/THIRD_PARTY_DATA.md`. Private list: `docs/PLACES.md`.

## Why (P7 audit, 2026-11-11)

Measured on the Galaxy Watch5 Pro with the P6-B build: "llévame a Cubanitos" auto-selected a restaurant in Medellín
(589.8 km; OpenStreetMap has no Cubanitos in Pasto), "... de la Panamericana" returned nothing for both owner places
(the landmark words were sent to Nominatim as part of the name) and "La Merced" put a hamlet 7 km away first. Overture
Maps Places has both places near the owner's home: a DATA gap in OSM for Cubanitos, a SEARCH gap for La Merced.

## Sources, in order

| # | source | where | network | used for |
|---|---|---|---|---|
| 1 | **My places** (`assets/my_places.json`, git-ignored, owner-curated) | on the watch | none | an alias match beats everything, works offline and without the language model |
| 2 | **Local places index** (`assets/places_pasto.tsv.gzip`, Overture Maps Places filtered to Pasto) | on the watch | none | businesses, shops, schools, churches... |
| 3 | **Nominatim** (OpenStreetMap) | online | at most 3 requests per utterance | addresses, streets, neighbourhoods, landmarks, and the fallback when 1-2 have no strong match |
| 4 | **Photon** (OpenStreetMap, fuzzy; P11) | online | shares the 3 requests with Nominatim | names Nominatim misses because of spelling ("Mr Pollo" -> "Míster Pollo") |
| - | optional keyed provider (P11) | online | default OFF | not implemented; see "Broad coverage" below |
| - | the language model | online | - | **never** coordinates: it only returns text (name, hint, category) |

The index (and my places) load off the main thread at the first microphone press of the process, so the work overlaps
with listening; never at app start and never on Home idle (measured on the Galaxy Watch5 Pro, release-like build:
about 0.5 s and 4 MB of heap; the debug build is 10-50x slower). Source 2 is skipped when the anchor is outside the index box (lon -77.40..-77.17, lat 1.08..1.32). Source 3 is skipped
when source 2 already has a strong match (tier >= 2 below) within 25 km of the anchor, so the common case is offline.

## Splitting the name from a landmark hint

The place words are split at the first landmark connector into **name X** and **hint Y**:
"X de la Y", "X del Y", "X por (la|el) Y", "X en (la|el) Y", "X sobre (la|el) Y", "X cerca (de|del) Y",
"X al lado (de|del) Y", "X junto (a|al) Y", "X frente (a|al) Y" (English: "X on Y", "X near Y", "X by Y", "X next to Y").
The split happens only when Y names a landmark kind or a known street word (Panamericana, avenida, calle, carrera,
barrio, centro, parque, plaza, terminal, universidad, unicentro...), so "Plaza de Nariño" or "Universidad de Nariño"
stay whole. The hint never filters: it only **re-ranks** (below). Name + hint with 0 hits retries the name alone.

Hint synonyms: "Panamericana" also matches "Pan-American", "Pan American", "Av. Panamericana".

## Normalisation and matching (local sources)

- Lower case, accents removed, punctuation to spaces; stop words dropped: de, la, el, los, las, del, en, por, y, a, al.
- A query token matches a name token when equal, when it is a prefix of it (query token >= 3 chars), or, for tokens of
  >= 5 chars, within edit distance 1 (so "Cuabanitos Express", a typo in the data, still matches "Cubanitos").
- Category words in the query ("restaurante", "droguería", "universidad", "hotel", "iglesia"...) are matched against the
  candidate's category instead of its name and give a soft boost; they are not required in the name.

## Nicknames and category words (P7, found on the watch)

- A Pasto nickname ("la plaza", "el terminal", "Torobajo"; `PlaceAliases`) becomes a search string verified on
  Nominatim, so it is asked there first (hits within 25 km win); offline, or with no near hit, the normal order applies.
  Measured: "llévame a la plaza de Nariño" had picked "Drogueria Plaza De Nariño", a pharmacy, from the local index.
- A candidate whose name has a category word the user did not say ("Droguería X" for "X") is never an exact match
  (tier 2 at most).

## Ranking

Candidates from all sources are scored and sorted by, in order:

1. **Match tier**: 3 = exact normalised name; 2 = every query token matches a name token (token, prefix or fuzzy);
   1 = some tokens match (only used for the not-found suggestions). An alias of my places is tier 4.
2. **Landmark** (P8, below): a landmark category beats any other.
3. **Category boost**: the category word the user said matches the candidate's category.
4. **Hint score** (only with a hint): the hint tokens found in the candidate's address or name (with the synonyms
   above), plus proximity to the hint's geocoded point (within 600 m) when that point is known.
5. **Trusted** (P8): confidence >= 0.6 beats a weaker row.
6. **Distance to the anchor**, nearest first. Anchor = the user's last known fix, else the Pasto centre (1.2136, -77.2811).
7. **Confidence** (Overture), highest first. Nominatim hits count as 0.6.

(Before all of these, a candidate within 25 km beats a farther one, see "Acceptance rules".)

Then **dedupe**: the same normalised name within 150 m keeps the best one. Then **at most 2 candidates** go to the
confirm surface (OTRO 1/2 cycles them, no request).

## Acceptance rules

- A candidate farther than **25 km** from the anchor is **never auto-selected**: it is listed after every nearer one and,
  when only far candidates exist, the confirm surface shows its distance in `alert` colour so the user decides.
- Nominatim gets **at most 3 requests per utterance** (1 per second, identifying User-Agent, results cached): name +
  hint; then the name alone if that gave 0 hits; then the hint alone when it is needed to re-rank (several candidates,
  no textual hint match). Without a hint: the name biased to the viewbox, then once bounded to it if every hit was far.
- No local candidate and no Nominatim candidate: the **not-found surface** (spec 7.1) with up to 2 tier-1 partial
  matches within 5 km (name only) and a microphone retry that asks for a street or neighbourhood. The retry's words
  become the hint for the same name; if the name still has no hit, the retry's words are searched as a place.

## The language model contract

The remote interpreter answers `{"kind", "name", "hint", "category", "mode", "confidence", "reply"}`: text only,
copied from the transcript (no invented names). Any coordinate field in the reply (`lat`, `lon`, `latitude`,
`longitude`, `coordinates`, `location`) is ignored, and a `name` that is only a coordinate pair ("1.21, -77.28") is
rejected. The user's position, my places and the index never leave the watch.

## Landmarks, nicknames and the language model in place choice (P8)

Why (field test, 2026-11-11): the owner said "la cooperativa" (Universidad Cooperativa de Colombia, his university)
and got "Cooperativa Nariñense de Militares Retirados", a party-planning business 0.7 km away. The university is in
both sources (Overture as "UCC Pasto", OpenStreetMap as "Universidad Cooperativa de Colombia", 16 m apart), but two
unrelated "Cooperativa ..." rows matched every query token, so Nominatim was never asked and nothing preferred a
university over a business.

**Landmark prior.** Categories people use as destinations are *landmarks*: education (university, college), health
(hospital), shopping centre (mall), transport hub (bus station, terminal, airport), government (town hall, government
office), park or plaza (park, square, public plaza) and stadium. In the ranking, right after the match tier, a landmark
beats a non-landmark; after the category boost and the hint, a row with confidence >= 0.6 beats a weaker one (Overture
rows of 0.3-0.5 are often duplicates or mislabelled), then distance and confidence (see "Ranking"). So a name that
only shares the token and carries an unsaid category word ("Droguería ...", "Cooperativa de ...") can never beat a
landmark with the same token. Nominatim hits carry their OpenStreetMap class and type (`amenity:university`).

**Nicknames** (`PlaceAliases`). Each nickname maps to the landmark's official name, asked on Nominatim first, plus the
name(s) the local index uses for it (Overture often has a short form: "UCC Pasto"), which answer offline. Leading
possessives and descriptors are not part of the nickname: "mi universidad la cooperativa", "mi U, la cooperativa",
"el hospital departamental" resolve like "la cooperativa" / "departamental" (the words removed are possessives `mi`,
`mis`, `nuestra`, `nuestro`, `my`, `our` and descriptor nouns `universidad`, `u`, `hospital`, `clinica`, `colegio`,
`centro comercial`, `estadio`, `terminal`, `parque`, `plaza`, followed by an article). Every nickname resolves to a
real record, checked on Nominatim and in the index before it is added (`docs/PLACES.md` lists the ones that did not).

**Two language-model roles.** The model never sees or returns coordinates, distances or the user's position.

1. *Expansion (text)*: when no nickname matched and the local candidates are weak (none, or every one is a
   non-landmark tier-2 match), the model is asked for up to 3 official names the words may stand for ("la cooperativa"
   -> "Universidad Cooperativa de Colombia"). Each name is searched in the index, then on Nominatim (inside the 3
   requests per utterance). Names are text only and are cleaned like any place query; nothing else in the answer is read.
2. *Choice (ids)*: when the ranked candidates are ambiguous, the model gets the utterance and up to 5 candidates as
   `{id, name, category, address}` (public data; ids are `c1`..`c5`) and answers `{"id": "c3"}` or `{"id": null}`.
   An id that is not in the list, any other field and any malformed answer are ignored; a valid id moves that
   candidate first, nothing else changes.

Triggers for the choice call (otherwise no call: clear matches cost no latency and no quota): (a) the top two have the
same tier and the same landmark class and neither is an exact name; (b) the utterance or the interpreter gave a
category that the top candidate's category does not match ("universidad" vs a party planner); (c) the utterance has a
possessive or descriptor ("mi universidad", "el hospital", "la U"); (d) the top candidate is a tier-2 non-landmark whose
name has more words than the query (it contains the token as part of a different entity). Never after my places, an
exact (tier 3) match alone, or a nickname answered by Nominatim.

Limits: at most **2 language-model calls per utterance** in total (the interpreter's call counts), the shared daily cap
of 200 and the 60 s cool-down after HTTP 429; each place call has a **2.5 s** timeout; answers are cached in memory by
the normalised utterance (20 entries). Offline, rate-limited, timed out or with no API key, the local ranking decides
alone. What is sent: the utterance text and the candidates' public names, categories and addresses. Never: the user's
position, distances, coordinates, my places, the index as a whole.

## Broad coverage, brand variants and chains (P11)

Why (owner feedback, 2026-11-13): "llévame por favor a Mr. Pollo", a local chicken chain with seven branches in Pasto,
was not recognised. STEP 0 trace: Overture has the chain (brand "Mister Pollo MRP", 7 rows) and Photon finds 7
"Míster Pollo ..." points in Pasto, but the query token "mr" never matched "mister", so "Mr. King Pollo" (a different
restaurant) was picked alone; "mr. pollo" lost "pollo" at the period, "mr pollo más cercano" kept "más cercano" in the
name (not found), and Nominatim finds the chain only when asked for "Mister". Coverage is never promised to be 100 %:
the measured numbers are in the benchmark below and in the README.

### Index v2 (`tools/build_places_index.py`)

- **Area**: a box of about 25 km (0.2247°) around the Pasto centre (1.2136, -77.2811): lon -77.506..-77.056,
  lat 0.989..1.438. It covers the urban area plus the places people from Pasto drive to: the airport (Chachagüí,
  20 km north; the old box held only wrongly placed copies of it), La Cocha and El Encano (20 km east), Genoy, Catambuco,
  Tangua, Yacuanquer, Nariño, La Florida, Buesaco. The search keeps its 25 km acceptance rule from the user.
- **Rows**: every Overture place with a primary name and confidence >= the threshold chosen with the benchmark (the
  final value and the comparison are in `docs/THIRD_PARTY_DATA.md`), except places whose `operating_status` is closed
  and the **excluded categories** (services that come to you or work online, usually geocoded to a private home):
  bartender, event_photography_service, psychic_advising, web_designer, software_development, graphic_designer,
  fitness_trainer, home_cleaning, janitorial_service, pool_cleaning, pest_control_service, furniture_assembly,
  tutoring_service, music_production, home_security, b2b_advertising_and_marketing_service, marketing_agency,
  advertising_agency, information_technology_company. The same normalised name within 50 m keeps its most confident row.
- **Columns**: the v1 columns plus `brand` (Overture `brand.names.primary`, e.g. "Mister Pollo MRP"); primary and
  alternate names stay (`names.common` and `names.rules` are empty in release 2026-10-21.0 for this area).
- **Limits**: <= 1.5 MB gzipped, release load <= 1 s on the Galaxy Watch5 Pro; the build is reproducible
  (pinned release, `--save-extract` / `--source` to rebuild from a saved box). Notices: `docs/THIRD_PARTY_DATA.md`.

### Normalisation

Applied to the query and to every index name at load (`PlaceText`):
- lower case, accents removed, punctuation to spaces, stop words dropped (de, la, el, los, las, del, en, por, y, a, al);
- one form per variant: `mr`, `mister`, `mistr`, `senor`, `sr` -> `mister`; `cll`, `cl` -> `calle`; `cra`, `kr`, `kra`,
  `cr` -> `carrera`; `av`, `avda` -> `avenida`; `cc` and the pair `c c` (from "C.C.") -> `centro comercial`;
- region words `pasto`, `narino`, `colombia` in a NAME do not block an exact match ("Catedral Pasto" is exact for
  "catedral").

### Fuzzy and phonetic matching

A query token matches a name token, best first: **equal**; **prefix** (query >= 3 characters); **same Spanish phonetic
key** (tokens >= 4 characters; b/v, s/z/soft c, ll/y, silent h, k/qu/hard c, j/soft g, double letters, ñ/n are one
sound each); **edit distance** <= 1 (5-7 characters) or <= 2 (8+), or a trigram overlap (Jaccard) >= 0.5 for tokens of
6+ characters. Speech slip: the query token `mi` may match the name token `mister` ("mi pollo" for "Mr. Pollo"). Each
token match has a quality (equal 3, prefix or phonetic 2, edit/trigram/slip 1); inside a tier, a better summed quality
ranks first, so exact "Catedral" beats fuzzy "Cátedra".

Tiers are unchanged (3 exact, 2 every query token matched, 1 some long token matched), and the P8/P10 category-word
rules stay: a category word is a soft boost, a name with an unsaid category word is never exact, a category word alone
is a category request.

### Brand variants and chains

- The index row's `brand` is a second name for matching (a query that matches the brand matches the row) and gives a
  brand boost right after the tier.
- **Chain** = the tier >= 2 candidates that share a normalised brand, or whose name starts with the query's tokens
  ("Lili Pink Unicentro", "Lili Pink Centro"), when there are 2 or more within 25 km. A chain answer is the pick list
  of its 3 nearest branches to the user's last known position (computed on the watch; the position is never sent);
  without a position, the 3 most confident. "más cercano/a", "más próximo/a", "nearest", "closest" are removed from the
  place and mark the request **nearest**: the nearest branch goes straight to Confirm place (OTRO cycles the others).
  "Mr." does not end a clause (abbreviations with a period: mr, mrs, sr, sra, dr, av, cra, cll, no, cc, sa).
- The language model's expansion role (P8, text only) is also told to return the brand's spelling variants
  ("Mr. Pollo" -> "Mister Pollo", "Míster Pollo"); its answers are searched like any name.

### Whisper vocabulary hint

The transcription `prompt` holds the region, the verified landmark names (P9) and up to 15 chain names from the loaded
index (brands or name chains with the most branches, shortest branch name as the spoken form, e.g. "Mister Pollo"), at
most 600 characters in total (Whisper reads only the last 224 tokens of a prompt). Public index data only: never my
places, the user's position, history or anything typed by the user. Without a loaded index, the P9 prompt.

### Fallback order and budgets

my places -> index -> Nominatim -> **Photon**. Photon is asked only when the index and Nominatim have no candidate of
tier >= 2 within 25 km, inside the same budget: **at most 3 geocoder requests per utterance in total** (Nominatim +
Photon), each service at most 1 request per second (process-wide limiters), identifying User-Agent, results cached in
memory (20 queries). At most 2 language-model calls per utterance, unchanged.

**Photon** (`https://photon.komoot.io/api/`, OpenStreetMap data, ODbL). Terms of the public instance, read on
2026-11-13 at https://photon.komoot.io/: "You can use the API for your project, but please be fair - extensive usage
will be throttled. We do not guarantee for the availability and usage might be subject of change in the future."
WGO sends one request per utterance at most (no search-as-you-type): `q` (the place words), `limit=8`, a FIXED box
(`bbox` = the index box) and the FIXED Pasto centre as `lat`/`lon` bias: **never the user's position**. Results are
OSM points (`osm_key:osm_value` as the category) and are credited "© OPENSTREETMAP". A public release needs its own
Photon instance or a paid one (`docs/RELEASE_CHECKLIST.md`).

**Optional keyed provider**: not implemented and OFF. A provider may be added only behind a key in `local.properties`
(through `BuildConfig`, never logged) and only if its terms allow showing its results on a non-vendor map with
OpenStreetMap tiles (Google Places does not; check any other provider's terms before adding it). It would be one more
`OnlinePlaceSearch` after Photon, inside the same 3-request budget.

### Not found

Unchanged surface ("No encontré «X»", partial matches, DIME LA CALLE). For a place that no source has,
`docs/PLACES.md` explains how to add it to my places (no invented coordinates).

### Coverage benchmark (`PlaceCoverageBenchmarkTest`)

`tools/build_place_benchmark.py` writes `app/src/test/resources/place_benchmark.tsv` from the index (fixed seed, public
data only); the test runs each utterance through the local parser and the resolver with no network, from the Pasto
centre:
- (a) 60 random real places of the index (confidence >= 0.5, 1-4 words, a unique name, within 20 km) said in a spoken
  style: accents and articles dropped, "mister"/"mr" swapped, one sound-alike slip (b/v, s/z, ll/y, a silent h) or one
  letter dropped; hit = same normalised name within 150 m;
- (b) 30 chains with 2+ branches in Pasto, every second one with "más cercano"; hit = a branch (its name contains the
  chain's words); the nearest-branch-first rate is reported too;
- (c) 20 landmarks and nicknames; hit = the place's row (same name) or anything within 300 m of it;
- (d) 10 places that do not exist; must never be picked automatically (only "No encontré" or the pick list).

Targets: top-3 >= 90 % and top-1 >= 75 % on (a)-(c), 0 automatic picks on (d), p95 local latency (parse + resolve)
<= 150 ms on the JVM. Baseline (P10 code and index, STEP 0): top-1 71.8 %, top-3 80.0 %, chains 12/30, 0/10, p95 6 ms.
