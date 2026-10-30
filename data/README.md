# data/

Datasets: airport database, sample ADS-B / GDL 90 captures, test flight logs.

The datasets the apps ship with live next to the code (one copy per app, kept identical by CI):

| File | What | Source / licence |
| --- | --- | --- |
| `app/android/data/src/main/resources/sample/flights.json` | Made-up flights for MVP 1 sample mode | Synthetic |
| `app/android/data/src/main/resources/gdl90/scenarios.json` | Pilot-mode test scenarios (also used by `tools/gdl90-replay`) | Synthetic |
| `app/android/data/src/main/resources/airports/airports.json` | 24,069 airports with ICAO/IATA codes, plus US fields with only an FAA identifier | [OurAirports](https://ourairports.com/data/), public domain (checked Oct 2026); built by `tools/airports/build_airports.py` |

Receiver captures from `gdl90_replay.py record` can go here if small; large ones belong outside git.

Do not commit large binaries here (>50 MB); use Git LFS or cloud storage and link to it.
