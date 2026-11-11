"""Builds WGO's offline places index from Overture Maps Places (dev-only tool; not an app dependency).

Usage (in a throwaway virtual environment OUTSIDE the repo):
    python -m venv %TEMP%\\wgo-venv && %TEMP%\\wgo-venv\\Scripts\\pip install duckdb
    %TEMP%\\wgo-venv\\Scripts\\python tools/build_places_index.py                 # latest release, Pasto box
    %TEMP%\\wgo-venv\\Scripts\\python tools/build_places_index.py --release 2026-10-21.0 --min-confidence 0.3

Output: app/src/main/assets/places_pasto.tsv.gzip, one header line then one place per line, tab separated:
    id  name  name_norm  alt_norm  category  address  lat  lon  confidence  source
`alt_norm` holds the normalised alternate names joined with "|". Rows need a primary name and confidence >= the
threshold; places whose operating_status says they are closed are dropped. Sorted by id so rebuilds diff cleanly.

Data: Overture Maps Foundation, Places theme (CDLA-Permissive-2.0; rows sourced from Foursquare: Apache-2.0).
See docs/THIRD_PARTY_DATA.md for the notices and the update procedure.
"""
import argparse
import gzip
import json
import pathlib
import re
import sys
import unicodedata
import urllib.request

STAC = "https://stac.overturemaps.org/catalog.json"
DEFAULT_BOX = (-77.40, -77.17, 1.08, 1.32)  # lon min, lon max, lat min, lat max (Pasto urban area, audit P7)
OUT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "places_pasto.tsv.gzip"
STOP = {"de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al"}


def normalize(text: str) -> str:
    """Same rule as the app's PlaceText.normalize: lower case, accents off, punctuation to spaces, stop words out."""
    t = unicodedata.normalize("NFD", (text or "").lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^0-9a-z]+", " ", t)
    return " ".join(w for w in t.split() if w not in STOP)


def clean(text) -> str:
    t = (text or "").replace("&nbsp", " ").replace("\t", " ").replace("\n", " ")
    return re.sub(r"\s+", " ", t).strip()


def latest_release() -> str:
    with urllib.request.urlopen(urllib.request.Request(STAC, headers={"User-Agent": "WGO places index builder"})) as r:
        return json.load(r)["latest"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--release", default=None, help="Overture release id (default: the latest in the STAC catalog)")
    ap.add_argument("--box", type=float, nargs=4, default=DEFAULT_BOX, metavar=("LONMIN", "LONMAX", "LATMIN", "LATMAX"))
    ap.add_argument("--min-confidence", type=float, default=0.3)
    ap.add_argument("--out", type=pathlib.Path, default=OUT)
    args = ap.parse_args()

    import duckdb  # dev-only

    release = args.release or latest_release()
    xmin, xmax, ymin, ymax = args.box
    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs; INSTALL spatial; LOAD spatial; SET s3_region='us-west-2';")
    try:
        con.execute("SET geometry_always_xy = true;")
    except duckdb.Error:
        pass
    src = f"s3://overturemaps-us-west-2/release/{release}/theme=places/type=place/*"
    cols = {r[0] for r in con.execute(f"DESCRIBE SELECT * FROM read_parquet('{src}', hive_partitioning=1)").fetchall()}
    status = "operating_status" if "operating_status" in cols else "NULL"
    category = "coalesce(taxonomy.primary, basic_category)" if "taxonomy" in cols else "categories.primary"
    rows = con.execute(f"""
        SELECT id, names.primary, names.rules, {category}, addresses[1].freeform,
               ST_Y(geometry), ST_X(geometry), confidence, sources[1].dataset, {status}
        FROM read_parquet('{src}', hive_partitioning=1)
        WHERE bbox.xmin BETWEEN {xmin} AND {xmax} AND bbox.ymin BETWEEN {ymin} AND {ymax}
          AND names.primary IS NOT NULL AND confidence >= {args.min_confidence}
        ORDER BY id
    """).fetchall()

    kept, closed = [], 0
    for pid, name, rules, cat, addr, lat, lon, conf, source, op in rows:
        if op and str(op).lower() in ("permanently_closed", "closed", "temporarily_closed"):
            closed += 1
            continue
        name = clean(name)
        norm = normalize(name)
        if not norm:
            continue
        alts = sorted({normalize(r["value"]) for r in (rules or []) if r and r.get("value")} - {norm, ""})
        kept.append("\t".join([
            pid, name, norm, "|".join(alts), clean(cat), clean(addr),
            f"{lat:.6f}", f"{lon:.6f}", f"{conf:.2f}", clean(source)
        ]))

    args.out.parent.mkdir(parents=True, exist_ok=True)
    header = "\t".join(["id", "name", "name_norm", "alt_norm", "category", "address", "lat", "lon", "confidence", "source"])
    body = (f"# overture {release} box {xmin},{xmax},{ymin},{ymax} min_confidence {args.min_confidence}\n"
            + header + "\n" + "\n".join(kept) + "\n").encode("utf-8")
    with open(args.out, "wb") as f:
        with gzip.GzipFile(filename="", mode="wb", fileobj=f, compresslevel=9, mtime=0) as gz:
            gz.write(body)
    print(f"release {release}: {len(rows)} rows >= {args.min_confidence}, {closed} closed dropped, {len(kept)} written, "
          f"{args.out.stat().st_size} bytes -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
