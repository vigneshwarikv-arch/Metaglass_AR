# 07 — MVP 1: Consumer "What's That Plane?"

*Status: **proposed scope**, first product to build.*

## One-line pitch

Look up, ask "What's that plane?", and hear the answer — airline, aircraft, route, altitude — hands-free on your smart glasses, or by pointing your phone at the sky.

## Who it's for

Plane spotters, aviation fans, families with kids, people living near airports or under flight paths, travellers and people meeting someone at the airport.

## MVP 1 features (keep to these)

| # | Feature | Description | Device |
| --- | --- | --- | --- |
| 1 | **Planes near me** | List + simple map of aircraft within ~50 km, sorted by distance: airline, flight number/callsign, aircraft type, altitude, speed, distance, direction | Phone |
| 2 | **Point to identify** | Hold the phone toward the sky; compass + tilt pick the aircraft in that direction and highlight it with its details | Phone |
| 3 | **Ask your glasses** ⭐ | "Hey Meta, what's that plane?" → uses head direction (DAT motion streaming) to pick the aircraft and answers by voice: *"IndiGo A320, Delhi to Chennai, 32,000 feet, 25 km north."* On Ray-Ban Display, also a small card. | Meta glasses |
| 4 | **Plane details** | Route (origin → destination), aircraft type, registration, altitude, speed, heading, where data is available | Phone + glasses |

⭐ = the differentiator. The phone features are table stakes (competitors already have them); **hands-free voice on glasses is what nobody else does yet.**

## Out of scope for MVP 1 (future paid features)

- Flight history / "what flew over earlier"
- Alerts: "a 787 is about to fly over your house", rare / military / favourite aircraft
- Spotting collection / logbook ("you've spotted 37 aircraft types")
- Aircraft photos
- AR overlays, VR mode, watch app
- Social / sharing

## Data

| Need | Prototype | Before paid launch |
| --- | --- | --- |
| Aircraft positions | OpenSky Network REST API (free, **non-commercial only**, rate-limited) | Commercial licence: ADS-B Exchange, Flightradar24 API, or FlightAware AeroAPI |
| Route (origin/destination) | Free community callsign→route lookup (verify terms) | Paid schedule/route data |
| Aircraft type / registration | Same as above | Same |

Data costs grow with users — check pricing per request before choosing a provider.

## How point-to-identify works

1. Get user location (phone GPS).
2. For each aircraft compute **bearing** (direction from north) and **elevation angle** (how high above the horizon) from user to aircraft using lat/lon/altitude.
3. Read where the user is pointing: phone compass + tilt, or glasses head direction.
4. Pick the aircraft with the smallest angular difference (within a tolerance, e.g. ±10–15°). If none, say "No aircraft in that direction; nearest is … to your left."

## Competitors (phone)

| App | What it does | Price |
| --- | --- | --- |
| Overhead – AR Flight Spotter (iOS, Jan 2025) | Point phone at sky → flight, type, route, distance; collection | Pro $4.99/mo or $39.99/yr |
| What Plane? (iOS, Android early access) | Nearest-plane widget with compass ring; Pro alerts + AI chat | Free + Pro |
| Overhead (Mac, Aug 2026) | Aircraft over a chosen place; "Siri, what's overhead" | Free |
| Plane Finder / Flightradar24 | Big trackers; Plane Finder AR since 2010 | Free + tiers |
| SkyGlass (Android) | 3D tracking | Free + subscription |
| Meta AI (built into glasses) | May guess aircraft type from camera; likely no live route — **test this** | — |

Takeaway: people pay ~$40/yr for this on phones. No one found doing it hands-free on smart glasses yet. Risk: Flightradar24 / Meta AI could add it — speed matters.

## Monetization (later, not in MVP)

- Free: planes near me, point to identify, basic voice answers
- Pro (~$20–40/yr, to test): glasses voice unlimited, history, flyover alerts, rare-aircraft alerts, collection
- B2B later: airport viewing areas, aviation museums, tourism

## Success metrics for MVP 1

- ~1,000 users
- Weekly returning users
- Sign-ups to a "Pro coming soon" list
- Glasses testers' feedback: is the voice answer fast and correct?

## Testing

- Phone app: India (OpenSky has data for Indian airspace via 1090 MHz ADS-B).
- Glasses: US partner (Meta glasses not officially supported in India).
- Quick validation: share a simple phone version in plane-spotting communities (Reddit, Facebook groups).
