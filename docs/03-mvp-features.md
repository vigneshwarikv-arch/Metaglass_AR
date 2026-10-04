# 03 — MVP Features

*Status: **proposed**, not final. Validate with 10–20 hobby pilots before building.*

## Core positioning

**"Traffic plus nearest airport," with audio callouts.** These solve real safety fears, use data available without wiring into the aircraft, and demo well in a 30-second video.

Sell it as an **add-on companion to the pilot's existing iPad flight app**, not a replacement.

## Free tier — phone GPS only, no extra hardware

| Feature | Example on glasses | Why it's useful | Notes |
| --- | --- | --- | --- |
| **Groundspeed** | `112 kt` | Speed over the ground; tailwind raises it, headwind lowers it. Judges trip time. | Different from airspeed |
| **Track** | `TRK 045°` | Real direction of travel; wind makes the plane drift from where the nose points. | |
| **GPS altitude** | `4,500 ft GPS` | Rough cross-check | **Must be labelled GPS.** Pilots fly by the barometric altimeter. |
| **Direct-to destination** | Arrow + `23 nm` + `12 min` | Pilot enters airport code before takeoff; glasses show direction, distance, time to arrive. | Simplest version of "which way to go" |

## Paid tier

| Feature | Example | Data source |
| --- | --- | --- |
| **Traffic alerts** (shown + spoken) | "Traffic, 2 o'clock, 3 miles, 500 ft above" | Portable ADS-B receiver (Stratux, Sentry) via GDL 90 |
| **Nearest airport, always on** | Direction + distance for emergency landing | Airport database + GPS |
| **Destination weather** | Wind speed/direction, best runway for headwind | FIS-B weather from the same receiver (US) |
| **Voice callouts for everything** | — | Makes the app work on non-display Ray-Ban Meta too (much bigger installed base) |

## Hobby-pilot extras (candidates for later)

- **Automatic logbook** — detects takeoffs, landings, flight time; warns "Passenger currency expires in 10 days" (3 takeoffs/landings in 90 days rule). ⭐ strong
- **Hands-free flight photos/video** — "Hey Meta, capture"; overlays speed, altitude, location; ready for social media. Doubles as free marketing. ⭐ strong
- **Sightseeing callouts** — "Lake Tahoe on your left, 8 miles." Audio/text, not precise graphics.
- **Airspace warnings** — "Restricted airspace 3 miles ahead."
- **Voice checklists** — glasses read checklist, pilot says "check".
- **Fuel and timer reminders** — "Switch fuel tanks", "40 minutes of fuel left at this rate."

## Out of scope for MVP

- Head-tracked 3D overlays ("city on your left" as graphics) — display can't align accurately.
- Moving maps, airspace boundary drawings — too much for a small screen.
- Airspeed and engine data — needs aircraft wiring.
- Any IFR use — highest regulatory risk.
- Gaming features (separate "Pokemon Go for pilots" idea).

## Pricing (proposed)

- Free tier + monthly/annual subscription with free trial (pilots already pay for EFB subscriptions).
- Price point to be tested; ~$100/year used only as an illustration in [04-market-size.md](04-market-size.md).

## Architecture sketch

```
[Phone GPS] ──┐
              ├──► [Phone app (iOS/Android)] ──DAT──► [Meta glasses: display + audio]
[ADS-B receiver: Stratux/Sentry] ──Wi-Fi, GDL 90 (UDP)──┘
[Airport + weather data] ──┘
```
