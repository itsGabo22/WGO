"""Builds WGO's offline places index from Overture Maps Places (dev-only tool; not an app dependency).

Usage (in a throwaway virtual environment OUTSIDE the repo):
    python -m venv %TEMP%\\wgo-venv && %TEMP%\\wgo-venv\\Scripts\\pip install duckdb
    %TEMP%\\wgo-venv\\Scripts\\python tools/build_places_index.py                       # pinned release, Pasto box
    %TEMP%\\wgo-venv\\Scripts\\python tools/build_places_index.py --save-extract box.parquet   # also keep the raw box
    %TEMP%\\wgo-venv\\Scripts\\python tools/build_places_index.py --source box.parquet         # rebuild offline

Output: app/src/main/assets/places_pasto.tsv.gzip, one `#` line, one header line, then one place per line, tab
separated:
    id  name  name_norm  alt_norm  category  address  lat  lon  confidence  source  brand
`alt_norm` holds the normalised alternate names joined with "|"; `brand` is Overture's brand name (chains: "Mister Pollo
MRP"), empty when none. Index v2 (P11, docs/PLACE_SEARCH.md "Index v2"): every Overture place with a primary name and
confidence >= the threshold inside a box of about 25 km around the Pasto centre, except closed places and the
EXCLUDED categories; the same name within DEDUPE_M keeps its most confident row. Sorted by id so rebuilds diff cleanly.

Data: Overture Maps Foundation, Places theme (CDLA-Permissive-2.0; rows sourced from Foursquare: Apache-2.0).
See docs/THIRD_PARTY_DATA.md for the notices and the update procedure.
"""
import argparse
import gzip
import json
import math
import pathlib
import re
import sys
import unicodedata
import urllib.request

STAC = "https://stac.overturemaps.org/catalog.json"
RELEASE = "2026-10-21.0"
# lon min, lon max, lat min, lat max: 25 km (0.2247 deg) around the Pasto centre (1.2136, -77.2811), which takes in the
# urban area, the airport (Chachagüí), La Cocha (El Encano) and the nearby municipalities (P11).
DEFAULT_BOX = (-77.506, -77.056, 0.989, 1.438)
DEFAULT_MIN_CONFIDENCE = 0.2
OUT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "places_pasto.tsv.gzip"
STOP = {"de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al"}
CLOSED = {"permanently_closed", "closed", "temporarily_closed"}
DEDUPE_M = 50.0

# Pure noise for a navigator (P11): services that come to you or work online, mostly geocoded to a private home, and
# single people's trades. Every other category stays.
EXCLUDED = {
    "bartender", "event_photography_service", "psychic_advising", "web_designer", "software_development",
    "graphic_designer", "fitness_trainer", "home_cleaning", "janitorial_service", "pool_cleaning",
    "pest_control_service", "furniture_assembly", "tutoring_service", "music_production", "home_security",
    "b2b_advertising_and_marketing_service", "marketing_agency", "advertising_agency", "information_technology_company",
}


def normalize(text: str) -> str:
    """Same rule as the app's PlaceText.normalize: lower case, accents off, punctuation to spaces, stop words out."""
    t = unicodedata.normalize("NFD", (text or "").lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^0-9a-z]+", " ", t)
    return " ".join(w for w in t.split() if w not in STOP)


def clean(text) -> str:
    t = (text or "").replace("&nbsp", " ").replace("\t", " ").replace("\n", " ").replace("|", " ")
    return re.sub(r"\s+", " ", t).strip()


def metres(lat1, lon1, lat2, lon2) -> float:
    k = math.cos(math.radians(lat1)) * 111_320.0
    return math.hypot((lon2 - lon1) * k, (lat2 - lat1) * 111_320.0)


def latest_release() -> str:
    with urllib.request.urlopen(urllib.request.Request(STAC, headers={"User-Agent": "WGO places index builder"})) as r:
        return json.load(r)["latest"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--release", default=RELEASE, help="Overture release id ('latest': the newest in the STAC catalog)")
    ap.add_argument("--source", default=None, help="a local parquet saved with --save-extract (no download)")
    ap.add_argument("--save-extract", default=None, help="also save the raw rows of the box to this parquet file")
    ap.add_argument("--box", type=float, nargs=4, default=DEFAULT_BOX, metavar=("LONMIN", "LONMAX", "LATMIN", "LATMAX"))
    ap.add_argument("--min-confidence", type=float, default=DEFAULT_MIN_CONFIDENCE)
    ap.add_argument("--out", type=pathlib.Path, default=OUT)
    args = ap.parse_args()

    import duckdb  # dev-only

    release = latest_release() if args.release == "latest" else args.release
    xmin, xmax, ymin, ymax = args.box
    con = duckdb.connect()
    con.execute("INSTALL spatial; LOAD spatial;")
    if args.source:
        src = f"read_parquet('{args.source}')"
        lat, lon = "lat", "lon"
    else:
        con.execute("INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2';")
        src = f"read_parquet('s3://overturemaps-us-west-2/release/{release}/theme=places/type=place/*', hive_partitioning=1)"
        lat, lon = "ST_Y(geometry)", "ST_X(geometry)"
    box = f"bbox.xmin BETWEEN {xmin} AND {xmax} AND bbox.ymin BETWEEN {ymin} AND {ymax}"
    if args.save_extract and not args.source:
        con.execute(f"COPY (SELECT * EXCLUDE (geometry), {lat} lat, {lon} lon FROM {src} WHERE {box}) "
                    f"TO '{args.save_extract}' (FORMAT parquet)")
    cols = {r[0] for r in con.execute(f"DESCRIBE SELECT * FROM {src}").fetchall()}
    status = "operating_status" if "operating_status" in cols else "NULL"
    category = "coalesce(taxonomy.primary, basic_category)" if "taxonomy" in cols else "categories.primary"
    brand = "brand.names.primary" if "brand" in cols else "NULL"
    rows = con.execute(f"""
        SELECT id, names.primary, names.rules, {category}, addresses[1].freeform,
               {lat}, {lon}, confidence, sources[1].dataset, {status}, {brand}
        FROM {src}
        WHERE {box} AND names.primary IS NOT NULL AND confidence >= {args.min_confidence}
        ORDER BY confidence DESC, id
    """).fetchall()

    kept, closed, excluded, dupes = [], 0, 0, 0
    by_name = {}
    for pid, name, rules, cat, addr, la, lo, conf, source, op, brand_name in rows:
        if op and str(op).lower() in CLOSED:
            closed += 1
            continue
        if cat and cat in EXCLUDED:
            excluded += 1
            continue
        name = clean(name)
        norm = normalize(name)
        if not norm:
            continue
        # The same name within DEDUPE_M is one place (rows arrive most confident first).
        if any(metres(la, lo, a, b) <= DEDUPE_M for a, b in by_name.get(norm, ())):
            dupes += 1
            continue
        by_name.setdefault(norm, []).append((la, lo))
        alts = sorted({normalize(r["value"]) for r in (rules or []) if r and r.get("value")} - {norm, ""})
        kept.append((pid, "\t".join([
            pid, name, norm, "|".join(alts), clean(cat), clean(addr),
            f"{la:.6f}", f"{lo:.6f}", f"{conf:.2f}", clean(source), clean(brand_name)
        ])))
    kept.sort()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    header = "\t".join(["id", "name", "name_norm", "alt_norm", "category", "address", "lat", "lon", "confidence",
                        "source", "brand"])
    body = (f"# overture {release} box {xmin},{xmax},{ymin},{ymax} min_confidence {args.min_confidence} v2\n"
            + header + "\n" + "\n".join(line for _, line in kept) + "\n").encode("utf-8")
    with open(args.out, "wb") as f:
        with gzip.GzipFile(filename="", mode="wb", fileobj=f, compresslevel=9, mtime=0) as gz:
            gz.write(body)
    print(f"release {release}: {len(rows)} rows >= {args.min_confidence}, {closed} closed, {excluded} excluded, "
          f"{dupes} duplicates dropped, {len(kept)} written, {args.out.stat().st_size} bytes -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
