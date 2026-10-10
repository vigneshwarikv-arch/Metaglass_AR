# 08 — MVP 2: Pilot Mode

*Status: **proposed scope**, second product. Replaces the earlier [03-mvp-features.md](03-mvp-features.md), which is kept for history.*

## One-line pitch

Keep your eyes outside the cockpit: groundspeed, direction to your destination, nearby traffic and the nearest airport — on your smart glasses and spoken in your ear.

## Who it's for

US VFR private / sport / hobby pilots (~174,000 private + ~7,500 sport pilots), plus renters, flying-club members and student pilots who already carry a portable ADS-B receiver.

## Features

### Free — phone GPS only, no extra hardware

| Feature | Example | Note |
| --- | --- | --- |
| Groundspeed | `112 kt` | Speed over ground |
| Track | `TRK 045°` | Real direction of travel incl. wind drift |
| GPS altitude | `4,500 ft GPS` | **Always labelled GPS** — cross-check only |
| Direct-to | Arrow + `23 NM` + `12 min` | Destination entered before takeoff |

### Paid — needs a compatible ADS-B receiver

| Feature | Example |
| --- | --- |
| Traffic callouts (shown + spoken) | "Traffic, 2 o'clock, 3 miles, 500 ft above" |
| Nearest airport, always on | Direction + distance for emergencies |
| Destination wind / best runway | From FIS-B weather (US only) |
| Voice mode | Everything spoken — works on Ray-Ban Meta without display |

### Low-risk early features (can ship before traffic callouts)

- Automatic logbook (takeoffs, landings, flight time, passenger currency reminder)
- Hands-free flight video/photos with speed, altitude, location overlay
- Pre-flight traffic view on the ground (internet data)

### Later

Sightseeing callouts, airspace warnings, voice checklists, fuel/timer reminders.

### Out of scope

Precise 3D AR overlays, moving maps, airspeed/engine data (needs aircraft wiring), IFR use.

## Traffic data sources

There is **no meaningful lag** from portable or panel receivers — both connect to the phone inside the cockpit over Wi-Fi/Bluetooth. Lag only comes from internet APIs (and there's usually no internet in flight).

| Source | Can our app read it? | Phase |
| --- | --- | --- |
| **Stratux** (portable, ~$390) | **Yes** — open GDL 90 over Wi-Fi, used by ForeFlight, FlyQ, Avare, iFly… | 1 |
| **Stratus 4** (portable) | **Yes** — open GDL 90 (per retailer comparison) | 1 |
| Other panel systems sending standard GDL 90 over Wi-Fi (Dynon, FreeFlight… — check each) | Maybe | 1–2 |
| **Garmin** GDL 52 (~$1,360), GTX 345 (~$6,500 installed), Flight Stream | Needs Garmin partnership (Connext) | 2 |
| **Sentry** Mini / Sentry / Plus ($399–719) | **No** — reportedly works only with ForeFlight | 3 (ForeFlight partnership only) |
| OpenSky / ADS-B Exchange | Testing, demos, pre-flight ground view only | 1 |
| No receiver | GPS-only free mode | 1 |

**Who owns what:** renters/clubs/students buy portable receivers (can't modify aircraft). Owners either installed ADS-B In+Out (e.g. GTX 345) or Out-only + a portable receiver.

## Technical risks to test early (US partner)

1. Two devices reading one Stratux at once (ForeFlight on iPad + our app on iPhone).
2. Background running: app must keep receiving data with the screen off / another app open (strict on iOS).
3. Glasses display readability in bright sun and at night.
4. Which receivers real pilots own — use the waitlist question.

## Safety & regulation

- Supplementary situational aid only — never a primary or certified instrument.
- VFR, daytime first. One tap / voice command to turn off.
- Minimal, glanceable screen; audio-first.
- Check FAA rules on personal electronic devices / head-worn displays before public launch.

## Testing plan

1. Ground tests with recorded GDL 90 data and OpenSky (India + US).
2. Drive tests (GPS features).
3. US partner flight tests: VFR, daytime, with a safety pilot.
4. Closed beta: 20–50 pilots via Meta release channel.
5. Public release.

## Competitors

APEX HUD (EyeLights + Adventure Pilot + AURA AERO): ~$3,993, commercial availability 2027. UL VISION (ultralights). Aero Glass (shut down 2018). See [02-competitive-landscape.md](02-competitive-landscape.md).
