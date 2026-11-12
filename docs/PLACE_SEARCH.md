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
