# Third-party data in WGO

## Overture Maps Places (`app/src/main/assets/places_pasto.tsv.gz`)

- **What**: the places index used offline by the place search (`docs/PLACE_SEARCH.md`): 6,285 places with a primary
  name and confidence >= 0.3 inside the Pasto box (lon -77.40..-77.17, lat 1.08..1.32), 413 KB.
- **Release**: Overture Maps Foundation release `2026-10-21.0` (the latest in https://stac.overturemaps.org/catalog.json
  on 2026-11-11), theme `places`, type `place`. Built with `tools/build_places_index.py` on 2026-11-11.
- **No OpenStreetMap data inside**: the Places theme is not derived from OpenStreetMap, so the Open Database License
  does not apply to this file.
- **Sources of the rows** (first source per place, counted in the asset): Meta 6,157, Microsoft 64, Foursquare 35,
  AllThePlaces 16, Uberall 10, DAC 3. The `source` column of each row names it.

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
2. `python tools/build_places_index.py` (latest release; `--release <id>` pins one, `--box` and `--min-confidence`
   change the area and the threshold).
3. Check the printed counts and that the file stays under 3 MB, update the release id, date and counts above, then
   commit the script output on its own (`chore(places): update the Overture places index to <release>`).

Overture publishes a release about once a month; places change slowly, so a quarterly update is enough.

## Map tiles

- Base and labels: CARTO basemaps (`basemaps.cartocdn.com`), © OpenStreetMap contributors, © CARTO. Map data under
  the Open Database License (ODbL); free tier with a key: 5 M tiles/month non-commercial, 1 M/month commercial.
- Fallback without a key: OpenStreetMap standard tiles, © OpenStreetMap contributors.

## Geocoding and routing

- Nominatim (OpenStreetMap) for addresses, streets and the fallback search; OSRM on the FOSSGIS OSM-DE servers for
  routes. Data © OpenStreetMap contributors, ODbL.
