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

## Ranking

Candidates from all sources are scored and sorted by, in order:

1. **Match tier**: 3 = exact normalised name; 2 = every query token matches a name token (token, prefix or fuzzy);
   1 = some tokens match (only used for the not-found suggestions). An alias of my places is tier 4.
2. **Hint score** (only with a hint): the hint tokens found in the candidate's address or name (with the synonyms
   above), plus proximity to the hint's geocoded point (within 600 m) when that point is known.
3. **Distance to the anchor**, nearest first. Anchor = the user's last known fix, else the Pasto centre (1.2136, -77.2811).
4. **Confidence** (Overture), highest first. Nominatim hits count as 0.6.

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
