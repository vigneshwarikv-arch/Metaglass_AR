# 09 — Background: ADS-B, Data Sources and Devices

Plain-language notes so both founders share the same understanding.

## What is ADS-B?

- Aircraft use GPS to know their position and **broadcast** it by radio about once per second (position, altitude, speed, ID).
- **ADS-B Out** (transmitter) — required by the FAA since 2020 in most busy US airspace. Most aircraft have it.
- **ADS-B In** (receiver) — optional. Shows *other* aircraft to the pilot. Many planes don't have it.
- In the US, receivers also get free weather (**FIS-B**: radar, airport weather, winds, TFRs). FIS-B is US-only.
- India: airliners broadcast on 1090 MHz, so receivers and OpenSky have Indian data too.

## Three ways to get traffic data

| Way | How | Lag | Use |
| --- | --- | --- | --- |
| Internet API (OpenSky, ADS-B Exchange) | Ground receivers → server → internet → app | Seconds; no internet in flight | Consumer app (MVP 1), testing, demos, pre-flight |
| Portable receiver (Stratux, Stratus, Sentry, Garmin GDL 52) | Box in cockpit hears aircraft → Wi-Fi → phone | None meaningful | Pilot mode |
| Panel receiver with wireless (GTX 345, Lynx, Dynon…) | Aircraft's built-in receiver → Wi-Fi/Bluetooth → phone | None meaningful | Pilot mode (often needs maker cooperation) |

## Why pilots carry portable receivers

Free in-flight weather (often the main reason), traffic, backup GPS + attitude, CO detector (Sentry). Portable $390–720 vs panel ~$6,500+ installed. Renters can't install equipment.

## What "open format" (GDL 90) means

Garmin published the GDL 90 message format as a public document. Any app can read it without permission. Stratux and Stratus 4 use it → our app can read them from day one. Sentry and Garmin's own link are closed.

## How ForeFlight / Garmin Pilot work

They are apps, not gadgets. ForeFlight: iPad/iPhone only; reads Sentry, Stratus, Stratux (via its extended GDL 90 spec), and Garmin/Avidyne/Lynx/Dynon/FreeFlight panels. Garmin Pilot: iOS + Android; reads Garmin GDL 50/52 and Garmin panels. Most GA pilots fly with an iPad on the yoke/kneeboard and iPhone as backup.

## Smart glasses landscape (Oct 2026)

| Glasses | Status | Plan |
| --- | --- | --- |
| Meta Ray-Ban Display / Ray-Ban Meta / Oakley Meta | 94% of display-less shipments; 37% of display glasses; DAT 1.0 SDK | **First** |
| Even Realities G1/G2 | 11% of display glasses; SDK (Even Hub) | Second |
| Rokid | 41% of display glasses, mostly China | Later (Asia) |
| Android XR (Google/Samsung) | Announced; check consumer availability | Watch |
| Apple | Display-less glasses reportedly 2027 | Watch |
| Snap, Xreal, Vuzix | Ecosystem-locked / video / enterprise | Not now |

## Sources

- FAA Civil Airmen Statistics 2025; ForeFlight Connect page; PilotMall Sentry vs Stratus (2026); Aviation News Talk receivers 2026; Houma Avionics GTX 345 pricing; 9to5Mac AI glasses H1 2026 report; Meta Connect 2026 developer recap.
