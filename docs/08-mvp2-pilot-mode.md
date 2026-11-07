# 08 — MVP 2: Pilot Mode

*Status: **proposed scope**, second product; **Phase 1 built** on branch `mvp2` (Oct 2026), not yet flight-tested. Replaces the earlier [03-mvp-features.md](03-mvp-features.md), which is kept for history.*

## Phase 1 build status (Oct 2026)

| Feature | Status | Notes |
| --- | --- | --- |
| Groundspeed, track (true), GPS altitude | *proposed → built* | Phone GPS (smoothed), or the receiver's ownship when it has a fix. Altitude always labelled GPS; the screen says whether it is MSL or height above the WGS-84 ellipsoid (Stratux sends ellipsoid height). |
| Direct-to | *proposed → built* | Search by ICAO, FAA, IATA or name; arrow (track-up), distance NM, ETE, bearing, "turn left/right". |
| Traffic callouts (shown + spoken) | *proposed → built* | "Traffic, 2 o'clock, 3 miles, 500 above, climbing." Rules below. |
| Nearest airport, always on | *proposed → built* | With a receiver connected. |
| Receiver status | *proposed → built* | Connected / no data / receiver GPS vs phone GPS. |
| Meta glasses | *proposed → built* | Ray-Ban Display: 3-line card (GS · TRK · GPS ALT, direct-to, one traffic alert). Ray-Ban Meta: spoken callouts. "Pilot mode off" by voice. Tested with simulated glasses only. |
| Disclaimer, one-tap off | *built* | One-time disclaimer; OFF button, notification "Turn off" (Android), voice "pilot mode off". |
| Testing without flying | *built* | `tools/gdl90-replay` and an in-app simulated receiver, same three scenarios. |
| Paid gating | *decided for Phase 1* | No payments: traffic and nearest airport unlock whenever a receiver is connected. |
| Background running | *decided for Phase 1* | Android: foreground service (callouts continue with the screen off). iPhone: screen kept on while pilot mode is open; background location later. |

**Traffic callout rules (built, both apps, same tests):** traffic within 5 NM and ±2,000 ft (adjustable in Settings), airborne only;
clock position relative to our track (compass direction when we have no track); distance in whole miles ("less than 1 mile" under 1 NM);
relative altitude to the nearest 100 ft, "same altitude" within 200 ft, "altitude unknown" when missing; "climbing/descending" above 500 fpm.
Converging traffic first, then nearest. One callout at a time, at least 8 s apart; the same aircraft again only after 15 s and when it is
30 % or 1 NM closer. Ownship is never called out (same ICAO address, or within 0.1 NM and 300 ft — e.g. a TIS-B "shadow").
Traffic altitude is pressure altitude; it is compared with ownship pressure altitude from the receiver (or GPS altitude as a fallback).

**Receiver notes:** GDL 90 per Garmin 560-1058-00 Rev A (heartbeat, ownship, ownship geometric altitude, traffic) plus the ForeFlight ID
message. Stratux sends UDP unicast to each Wi-Fi client, so ForeFlight on an iPad and this app on a phone should both work; two GDL 90 apps on
the *same* phone are not reliable (a unicast datagram reaches only one app). To be confirmed with real hardware — see
[12-pilot-flight-test-checklist.md](12-pilot-flight-test-checklist.md).

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
