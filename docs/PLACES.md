# My places and missing places

WGO finds a place in this order (details: `docs/PLACE_SEARCH.md`): **my places** (your private list), the **local
places index** (Overture Maps, on the watch, about 25 km around Pasto, brands included), then **Nominatim** and
**Photon** (OpenStreetMap, online; Photon forgives spelling, "Mr Pollo" finds "Míster Pollo"). This page explains the
first one and how to fix a place that none of them knows.

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

## Chains and their branches (P11)

A chain ("Mister Pollo", "Lili Pink", "Dollarcity") is found by its brand or by branch names that start with its name;
the pick list shows the 3 branches nearest to you. "Mr. Pollo" (Míster Pollo) is in the index with 7 branches and in
OpenStreetMap, so it needs nothing from you.

- **A branch is missing**: add it to OpenStreetMap with the chain's usual name plus the area as the name ("Míster
  Pollo Calle 18") and, if you know it, `brand=Míster Pollo`; the next index update or Photon picks it up.
- **Only for you, now**: add the branch to my places with coordinates you read yourself (long-press its entrance in a
  map app). Give it the chain's name as an alias only if you want that one branch every time (an alias beats the
  chain list):

```json
{ "aliases": ["mr pollo de la 18"], "name": "Míster Pollo Calle 18", "lat": <your latitude>, "lon": <your longitude>, "note": "Calle 18" }
```

## Pasto nicknames (P8)

Built in (`PlaceAliases.kt`), each checked on 2026-11-11 against Nominatim and the local index:

| nickname(s) | landmark (Nominatim) | local index row (offline) |
|---|---|---|
| la cooperativa, la U cooperativa, universidad cooperativa, UCC | Universidad Cooperativa de Colombia (amenity/university, Carrera 45, Pandiaco) | UCC Pasto (college_university) |
| udenar, torobajo, la U de Nariño | Universidad de Nariño (amenity/university) | Universidad de Nariño |
| la mariana | Universidad Mariana (amenity/university) | Universidad Mariana |
| cesmag | Universidad CESMAG (amenity/university) | University Institution CESMAG |
| la unad | UNAD (amenity/university) | Universidad Nacional Abierta Y A Distancia |
| la plaza, plaza de Nariño | Plaza de Nariño (place/square) | none: offline it is not found (never a shop named after it) |
| la terminal / el terminal | Terminal de Transportes de Pasto (amenity/bus_station) | Terminal De Transportes De Pasto S.A. |
| el hospital departamental, HUDN | Hospital Departamental de Nariño (amenity/hospital) | Hospital Departamental De Nariño |
| el estadio | Estadio Departamental Libertad (leisure/stadium) | Estadio Departamental Libertad |
| unicentro | Centro Comercial Unicentro (landuse/commercial) | Unicentro Pasto (shopping_mall) |
| bomboná | Centro Comercial Bomboná (shop/mall); plain "Bomboná" is a locality in Consacá | Centro Comercial y Artesanal Bomboná |
| el aeropuerto | Aeropuerto Antonio Nariño (P7) | none (the index row's coordinates are wrong) |

Not added, because no real record could be verified: **"la nacho"** (no place by that name in either source) and
**"la U"** (ambiguous: every university in Pasto). If you mean a specific place by them, add it to my places with
coordinates you read yourself on a map (for example by long-pressing it in a map app); the app never invents them:

```json
{ "aliases": ["la nacho"], "name": "<official name>", "lat": <your latitude>, "lon": <your longitude>, "note": "<barrio>" }
```