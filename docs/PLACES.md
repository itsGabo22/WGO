# My places and missing places

WGO finds a place in this order (details: `docs/PLACE_SEARCH.md`): **my places** (your private list), the **local
places index** (Overture Maps, on the watch), then **Nominatim** (OpenStreetMap, online). This page explains the first
one and how to fix a place that none of them knows.

## My places (`app/src/main/assets/my_places.json`)

A private list of places you name your own way ("la casa", "donde mi mamá", "la oficina"). It is **git-ignored**: it
never leaves your computer and your watch, and it is never sent to the language model or any server. Without the file
the app and the build work as usual.

1. Copy `app/src/main/assets/my_places.example.json` to `app/src/main/assets/my_places.json`.
2. For each place, read its coordinates: on a phone, open a map app (OpenStreetMap or Google Maps), **long-press** the
   exact entrance and copy the latitude and longitude it shows (for Pasto: latitude about 1.2, longitude about -77.3).
   Use only coordinates you read yourself; never guess them.
3. Fill one entry per place and rebuild (`.\gradlew.bat assembleDebug`) and install.

```json
[
  {
    "aliases": ["la casa", "mi casa"],
    "name": "Casa",
    "lat": 1.21000,
    "lon": -77.28000,
    "note": "Barrio, street or anything to show under the name"
  }
]
```

- `aliases`: what you will say after "llévame a". Case, accents and the words de, la, el, los, las, del, en, por, y,
  a, al do not matter: "La Casa", "casa" and "lá casa" are the same alias. The `name` is an alias too.
- `note`: shown as the second line on the confirm screen.
- An alias match beats every other source and works offline and without the language model.

## A place WGO cannot find

1. Try it with a landmark: "llévame a Cubanitos de la Panamericana", "... cerca de Unicentro". The landmark only
   re-orders the results.
2. If it is a place only you need, add it to my places (above).
3. If everybody should find it, add it to **OpenStreetMap** (the next index update or Nominatim will pick it up).
   Create a free account on openstreetmap.org, open the editor at the place and add a point with these tags
   (example for a fast-food restaurant):

   | tag | example |
   |---|---|
   | `amenity` | `fast_food` (or `restaurant`, `cafe`, `pharmacy`, `bar`...) |
   | `name` | `Cubanitos` |
   | `cuisine` | `sandwich` (optional) |
   | `addr:street` | `Avenida Panamericana` |
   | `addr:housenumber` | `12-34` (as written on the door) |
   | `addr:city` | `Pasto` |

   For a place that already exists without an address (for example the restaurant "La Merced", node 879662751), only
   add `addr:street` and `addr:housenumber`. Follow the OpenStreetMap rules: add only what you have checked on the
   ground; do not copy from other maps.
