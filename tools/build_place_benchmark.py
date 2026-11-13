"""Generates WGO's place coverage benchmark from the places index (dev-only tool, P11; docs/PLACE_SEARCH.md).

Usage:  python tools/build_place_benchmark.py [--index app/src/main/assets/places_pasto.tsv.gzip]

Output: app/src/test/resources/place_benchmark.tsv (public Overture data only; no personal data, no secrets), read by
PlaceCoverageBenchmarkTest. One line per query, tab separated:
    group  utterance  expect  lat  lon  branches
group a = a random real place of the index said in a spoken style (expect = its normalised name, lat/lon = the row);
b = a chain (expect = the chain key; lat/lon = the branch nearest to the Pasto centre; branches = every branch as
"lat,lon;..." : the rows whose name or brand contains the key); c = a landmark or nickname (expect = the row's
normalised name, lat/lon = the row; the same name or anything within 300 m counts); d = a place that does not exist
(must not be picked automatically). Deterministic (fixed seed).
"""
import argparse
import gzip
import math
import pathlib
import random
import re
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
INDEX = ROOT / "app" / "src" / "main" / "assets" / "places_pasto.tsv.gzip"
OUT = ROOT / "app" / "src" / "test" / "resources" / "place_benchmark.tsv"
CENTRE = (1.2136, -77.2811)
STOP = {"de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al"}
SEED = 11

# (chain key as normalised name tokens, how people say it)
CHAINS = [
    ("mister pollo", "mr pollo"), ("pollo sorpresa", "pollo sorpresa"), ("lili pink", "lili pink"),
    ("dollarcity", "dollarcity"), ("texaco", "la texaco"), ("bajaj", "bajaj"), ("justo bueno", "justo y bueno"),
    ("pasteur", "droguería pasteur"), ("bbc", "la bbc"), ("electromillonaria", "electromillonaria"),
    ("farmacenter", "farmacenter"), ("western union", "western union"), ("moneygram", "moneygram"),
    ("victoria peluqueria", "victoria peluquería"), ("canasta", "la canasta"), ("bancolombia", "bancolombia"),
    ("tigo", "tigo"), ("presto", "presto"), ("koaj", "koaj"), ("qbano", "sandwich qbano"),
    ("comfamiliar", "comfamiliar"), ("tiendas metro", "tiendas metro"), ("alkosto", "alkosto"),
    ("jumbo", "jumbo"), ("subway", "subway"), ("smart fit", "smart fit"), ("studio f", "studio f"),
    ("popsy", "popsy"), ("body fit", "body fit"), ("punto vive", "punto vive"), ("pollo al dia", "pollo al día"),
    ("caffeto", "caffeto"), ("dominos", "domino's"), ("honda", "honda"), ("renault", "renault"),
]

# (utterance, a name to find the landmark's row in the index)
LANDMARKS = [
    ("llévame a la cooperativa", "ucc pasto"), ("llévame a la udenar", "universidad narino"),
    ("vamos a torobajo", "universidad narino"), ("llévame a la mariana", "universidad mariana"),
    ("llévame a la cesmag", "university institution cesmag"), ("quiero ir a la unad", "universidad nacional abierta distancia"),
    ("llévame al terminal", "terminal transportes pasto s"), ("llévame al hospital departamental", "hospital departamental narino"),
    ("vamos al estadio", "estadio departamental libertad"), ("llévame a unicentro", "unicentro pasto"),
    ("llévame a bomboná", "centro comercial artesanal bombona"), ("llévame a la catedral de pasto", "catedral pasto"),
    ("llévame al aeropuerto", "aeropuerto antonio narino airport"), ("llévame al hospital san pedro", "fundacion hospital san pedro"),
    ("llévame al museo taminango", "museo taminango"), ("llévame a la laguna de la cocha", "laguna cocha"),
    ("llévame al centro comercial único", "centro comercial unico"), ("llévame a la alcaldía de pasto", "alcaldia pasto"),
    ("vamos al parque bolívar", "parque bolivar"), ("llévame a la plaza del carnaval", "plaza carnaval"),
    ("llévame al templo de cristo rey", "templo cristo rey"), ("llévame al volcán galeras", "volcan galeras"),
    ("llévame al museo del carnaval", "museo carnaval pasto"), ("vamos al parque santiago", "parque santiago"),
]

NEGATIVES = [
    "llévame a la pizzería galáctica de venus", "llévame al restaurante el dragón azul de marte",
    "quiero ir a la ferretería los pingüinos voladores", "llévame al hotel kraken imperial",
    "vamos a la droguería zeppelin morado", "llévame a la clínica unicornio dorado",
    "llévame a la panadería saturno cuántico", "llévame al gimnasio titanio lunar",
    "llévame a la librería nebulosa de andrómeda", "quiero ir al bar xilófono púrpura",
]

VERBS = ["llévame a {}", "{}", "quiero ir a {}", "vamos a {}", "llévame a {}"]
SWAPS = [("v", "b"), ("b", "v"), ("z", "s"), ("ll", "y"), ("c", "s"), ("y", "ll"), ("s", "z"), ("h", "")]


def normalize(text: str) -> str:
    t = unicodedata.normalize("NFD", (text or "").lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^0-9a-z]+", " ", t)
    return " ".join(w for w in t.split() if w not in STOP)


def plain(text: str) -> str:
    t = unicodedata.normalize("NFD", text.lower())
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


def metres(a, b) -> float:
    k = math.cos(math.radians(a[0])) * 111_320.0
    return math.hypot((b[1] - a[1]) * k, (b[0] - a[0]) * 111_320.0)


def speech_error(name: str, rnd: random.Random) -> str:
    """One sound-alike slip in the longest word of 6+ letters (b/v, s/z, ll/y, a silent h), else one letter dropped."""
    words = name.split()
    i = max(range(len(words)), key=lambda k: len(words[k]))
    w = words[i]
    if len(w) < 6:
        return name
    for a, b in SWAPS:
        if a in w[1:]:
            j = w.index(a, 1)
            words[i] = w[:j] + b + w[j + len(a):]
            return " ".join(words)
    j = rnd.randrange(1, len(w) - 1)
    words[i] = w[:j] + w[j + 1:]
    return " ".join(words)


def spoken(name: str, variant: int, rnd: random.Random) -> str:
    t = re.sub(r"[^\w\s'&.-]", " ", name)
    t = re.sub(r"\s+", " ", t).strip()
    if variant == 0:
        t = plain(t)
    elif variant == 1:
        t = " ".join(w for w in plain(t).split() if w not in ("el", "la", "los", "las"))
    elif variant == 2:
        t = speech_error(plain(t), rnd)
    elif variant == 3:
        low = plain(t)
        t = re.sub(r"\bmister\b", "mr", low) if "mister" in low else re.sub(r"\bmr\.?(?=\s)", "mister", low)
    return VERBS[variant].format(t)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--index", type=pathlib.Path, default=INDEX)
    ap.add_argument("--out", type=pathlib.Path, default=OUT)
    args = ap.parse_args()
    lines = gzip.open(args.index, "rt", encoding="utf-8").read().splitlines()
    rows = [l.split("\t") for l in lines if l and not l.startswith("#")][1:]
    places = [(r[1], r[2], (float(r[6]), float(r[7])), float(r[8]), r[4], normalize(r[10]) if len(r) > 10 else "")
              for r in rows]
    counts = {}
    for p in places:
        counts[p[1]] = counts.get(p[1], 0) + 1
    rnd = random.Random(SEED)
    out = []

    # (a) random real places: a plain, sayable name (1-4 words, letters), unique in the index, within 20 km.
    pool = [p for p in places if counts[p[1]] == 1 and p[3] >= 0.5 and 1 <= len(p[1].split()) <= 4
            and re.fullmatch(r"[A-Za-zÁÉÍÓÚÜÑáéíóúüñ .'&-]+", p[0]) and len(p[1]) >= 4 and metres(CENTRE, p[2]) <= 20_000]
    pool.sort(key=lambda p: p[1])
    for i, p in enumerate(rnd.sample(pool, 60)):
        out.append(("a", spoken(p[0], i % 5, rnd), p[1], p[2]))

    # (b) chains present with 2+ branches: every second one asks for the nearest.
    chains = []
    for key, say in CHAINS:
        k = key.split()
        branches = [p for p in places if all(t in p[1].split() for t in k) or all(t in p[5].split() for t in k)]
        if len(branches) >= 2:
            chains.append((key, say, min(branches, key=lambda p: metres(CENTRE, p[2])), branches))
    for i, (key, say, nearest, branches) in enumerate(chains[:30]):
        utt = f"llévame a {say}" if i % 2 == 0 else f"llévame al {say} más cercano"
        if key == "mister pollo" and i % 2 == 0:
            utt = "llévame por favor a mr pollo"
        out.append(("b", utt, key, nearest[2], ";".join(f"{p[2][0]:.6f},{p[2][1]:.6f}" for p in branches)))

    # (c) landmarks and nicknames whose row exists.
    for utt, name in LANDMARKS:
        # "<name> <category>" pins one row when the name has copies elsewhere (the airport's mis-placed copies).
        cat = name.split()[-1] if name.endswith(" airport") else None
        if cat:
            name = name[: -len(cat) - 1]
        row = next((p for p in places if p[1] == name and (cat is None or p[4] == cat)), None)
        if row is not None:
            out.append(("c", utt, row[1], row[2]))
    out = [o for o in out if o[0] != "c"] + [o for o in out if o[0] == "c"][:20]

    # (d) places that do not exist.
    out += [("d", u, "", None) for u in NEGATIVES]

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# generated by tools/build_place_benchmark.py from " + lines[0].lstrip("# ") + "\n")
        for g, utt, expect, coord, *more in out:
            lat, lon = (f"{coord[0]:.6f}", f"{coord[1]:.6f}") if coord else ("", "")
            f.write("\t".join([g, utt, expect, lat, lon, more[0] if more else ""]) + "\n")
    groups = {g: sum(1 for o in out if o[0] == g) for g in "abcd"}
    print(f"{len(out)} queries {groups} -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
