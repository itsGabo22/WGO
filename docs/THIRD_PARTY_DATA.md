# Third-party data in WGO

## Overture Maps Places (`app/src/main/assets/places_pasto.tsv.gzip`)

- **What**: the places index used offline by the place search (`docs/PLACE_SEARCH.md`), index v2 (P11): 7,984 places
  with a primary name and confidence >= 0.2 inside a box of about 25 km around the Pasto centre (lon -77.506..-77.056,
  lat 0.989..1.438), without the excluded categories (159 rows) and with same-name duplicates within 50 m merged
  (11 rows); 247 rows carry an Overture brand. 527 KB (515 KiB). v1 (P7) had 6,285 places >= 0.3 in the urban box.
- **Threshold**: chosen with the coverage benchmark (`docs/PLACE_SEARCH.md`): on the same 120 queries, 0.3 and 0.2 gave
  the same hit rates and no automatic pick of a non-existent place; 0.2 keeps 1,305 more real places, so 0.2.
- **Release**: Overture Maps Foundation release `2026-10-21.0` (still the latest in
  https://stac.overturemaps.org/catalog.json on 2026-11-13), theme `places`, type `place`. Built with
  `tools/build_places_index.py` on 2026-11-13 (from a saved extract of that release's box: `--save-extract`/`--source`).
- **No OpenStreetMap data inside**: the Places theme is not derived from OpenStreetMap, so the Open Database License
  does not apply to this file.
- **Sources of the rows** (first source per place, counted in the asset): Meta 7,849, Microsoft 66, Foursquare 37,
  AllThePlaces 18, Uberall 11, DAC 3. The `source` column of each row names it.

### Notices (from https://docs.overturemaps.org/attribution/, read 2026-11-11)

Overture Maps Foundation, overturemaps.org.

- Meta, Microsoft, Uberall, DAC and the other CDLA sources: "Data from Meta. Available under CDLA Permissive 2.0." (and
  the same wording per source). License text: Community Data License Agreement – Permissive, Version 2.0,
  https://cdla.dev/permissive-2-0/. CDLA-Permissive-2.0 lets you use, modify and share the data; when you share the
  data itself you must include the license text or a link to it. Results of computation on the data (for example a
  route to a place) carry no obligation.
- Foursquare: "Data from Foursquare. Copyright 2024 Foursquare Labs, Inc. All rights reserved. Available under
  Apache 2.0. Foursquare data was transformed to the Overture schema." License: Apache License 2.0,
  https://www.apache.org/licenses/LICENSE-2.0; NOTICE: https://opensource.foursquare.com/places-notice-txt/. WGO
  modifies it by filtering the rows and reducing them to the columns listed in the builder.
- AllThePlaces: "Data from AllThePlaces. Available under CC0 1.0."

The app shows these credits on its Credits screen (Settings).

### Update the index

1. In a throwaway virtual environment outside the repo: `pip install duckdb`.
2. `python tools/build_places_index.py --release latest` (the default is the pinned release; `--box` and
   `--min-confidence` change the area and the threshold; `--save-extract box.parquet` keeps the raw rows so
   `--source box.parquet` rebuilds the same file offline).
3. Check the printed counts and that the file stays under 1.5 MB, update the release id, date and counts above, then
   commit the script output on its own (`chore(places): update the Overture places index to <release>`).

Overture publishes a release about once a month; places change slowly, so a quarterly update is enough.

## Map tiles

- Base and labels: CARTO basemaps (`basemaps.cartocdn.com`), © OpenStreetMap contributors, © CARTO. Map data under
  the Open Database License (ODbL); free tier with a key: 5 M tiles/month non-commercial, 1 M/month commercial.
- Fallback without a key: OpenStreetMap standard tiles, © OpenStreetMap contributors.

## Speech to text (P9)

- **Model**: Whisper (`whisper-large-v3`) by OpenAI, "MIT License", "Copyright (c) 2022 OpenAI"
  (https://github.com/openai/whisper/blob/main/LICENSE, read 2026-11-12). WGO ships no model weights: the model runs
  on the provider's servers.
- **Service**: hosted by Groq (`api.groq.com`, OpenAI-compatible `audio/transcriptions`), under Groq's terms and data
  policy (`docs/PRIVACY_DATA_FLOW.md`). Credited on the Credits screen: "Voz a texto: Whisper de OpenAI (MIT), en los
  servidores de Groq." / "Speech to text: OpenAI's Whisper (MIT), hosted by Groq."

## Geocoding and routing

- Nominatim (OpenStreetMap) for addresses, streets and the fallback search; OSRM on the FOSSGIS OSM-DE servers for
  routes. Data © OpenStreetMap contributors, ODbL.
