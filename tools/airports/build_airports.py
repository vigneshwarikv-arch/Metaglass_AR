#!/usr/bin/env python3
"""Builds the bundled airport list for pilot mode from OurAirports.

OurAirports data is released to the Public Domain ("All data is released to the Public Domain,
and comes with no guarantee of accuracy or fitness for use", https://ourairports.com/data/, checked
Oct 2026). We still credit it in the app.

Kept: large, medium and small airports and seaplane bases (no heliports, no closed fields) that have
an ICAO or IATA code, plus US fields that only have an FAA identifier (e.g. 0B5) — those matter for
direct-to and for the nearest airport in an emergency.

Usage (no dependencies; Python 3.8+):
    python tools/airports/build_airports.py                 # downloads airports.csv
    python tools/airports/build_airports.py path/to/airports.csv

Writes app/android/data/src/main/resources/airports/airports.json and the identical iOS copy
app/ios/PlanesKit/Sources/PlanesData/Resources/airports.json (CI checks they match).
"""
import csv
import io
import json
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

SOURCE_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv"
ROOT = Path(__file__).resolve().parents[2]
OUTPUTS = [
    ROOT / "app/android/data/src/main/resources/airports/airports.json",
    ROOT / "app/ios/PlanesKit/Sources/PlanesData/Resources/airports.json",
]
TYPES = {"large_airport": "L", "medium_airport": "M", "small_airport": "S", "seaplane_base": "W"}
FAA_ID = re.compile(r"[0-9A-Z]{3,4}")
ICAO = re.compile(r"[A-Z]{4}")
IATA = re.compile(r"[A-Z]{3}")


def load_csv(arg):
    if arg:
        return Path(arg).read_text(encoding="utf-8")
    with urllib.request.urlopen(SOURCE_URL, timeout=60) as r:
        return r.read().decode("utf-8")


def row(r):
    t = TYPES.get(r["type"])
    if not t:
        return None
    icao = (r.get("icao_code") or "").strip().upper()
    iata = (r.get("iata_code") or "").strip().upper()
    icao = icao if ICAO.fullmatch(icao) else ""
    iata = iata if IATA.fullmatch(iata) else ""
    faa = ""
    if r["iso_country"] == "US":
        local = (r.get("local_code") or "").strip().upper()
        faa = local if FAA_ID.fullmatch(local) else ""
    ident = icao or faa or iata
    if not ident:
        return None
    try:
        lat = round(float(r["latitude_deg"]), 4)
        lon = round(float(r["longitude_deg"]), 4)
    except ValueError:
        return None
    elev = r.get("elevation_ft") or ""
    elev = int(float(elev)) if elev.strip() else None
    name = r["name"].strip()
    city = (r.get("municipality") or "").strip() or None
    return [ident, icao or None, iata or None, faa or None, name, city, r["iso_country"], lat, lon, elev, t]


def main():
    text = load_csv(sys.argv[1] if len(sys.argv) > 1 else None)
    rows = [x for x in (row(r) for r in csv.DictReader(io.StringIO(text))) if x]
    rows.sort(key=lambda x: (x[0], x[6]))
    doc = {
        "source": "OurAirports (public domain), https://ourairports.com/data/",
        "generated": date.today().isoformat(),
        "fields": ["ident", "icao", "iata", "faa", "name", "city", "country", "lat", "lon", "elevationFt", "type"],
        "types": {"L": "large", "M": "medium", "S": "small", "W": "seaplane"},
        "airports": rows,
    }
    data = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).replace('],[', '],\n[')
    for out in OUTPUTS:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(data + "\n", encoding="utf-8", newline="\n")
    print(f"{len(rows)} airports, {len(data.encode('utf-8')) / 1e6:.2f} MB -> " + ", ".join(str(o.relative_to(ROOT)) for o in OUTPUTS))


if __name__ == "__main__":
    main()
