# 02 — Competitive Landscape: Head-Up Displays for Pilots

*Researched: 3 October 2026.*

## Summary

The idea has been tried and a serious product is coming to market now — but **as far as we found, nobody has built it on Meta's consumer glasses.** Our angle: cheaper ($800 Meta Display vs ~$4,000 dedicated hardware), lighter, normal-looking glasses.

## Competitors and prior attempts

| Product | Status | What it does | Price | Notes |
| --- | --- | --- | --- | --- |
| **APEX HUD** (EyeLights + Adventure Pilot + AURA AERO) | Unveiled AirVenture Oshkosh, July 2026; commercial availability expected 2027 | Customizable nav, flight-planning and in-flight data from iFly EFB; iOS/Android/Windows; Garmin G3X integration with head tracking; 80 g, over prescription glasses, wired | ~$3,993 show pre-order | **Main competitor.** Has an established flight-app partner. |
| **UL VISION** (UL Control, France) | Featured June 2026 | Wireless pitot probe (DART) sends altitude, airspeed, vertical speed, AoA, slip/skid to AR glasses | — | Ultralight/light aviation focus |
| **Aero Glass** | 2014–2018, shut down | 3D AR overlays: traffic (ADS-B), airports, cities, airspace, terrain, route, on Epson Moverio / ODG glasses | — | Almost exactly our feature list. Testers complained about fit; hardware wasn't ready. |
| **Military & airline HUDs** (e.g., F-35 helmet) | Mature | Full HUD | Very expensive | Shows the concept works |

## Existing phone apps we'd complement (not replace)

- **iPad EFB apps:** ForeFlight, Garmin Pilot, iFly EFB — pilots already pay for these.
- **ADS-B receivers:** Stratux (DIY/cheap), Sentry, etc. Output standard **GDL 90** over Wi-Fi (UDP port 4000 by default).
- **AeroADSB** (iOS): reads GDL 90 traffic + AHRS — proof that the data pipeline to a phone is straightforward.

## Lessons for us

1. Don't promise precise 3D AR overlays — consumer glasses can't align accurately with the outside world (Aero Glass struggled; experts note lack of collimation and boresighting).
2. Fit and comfort matter as much as software.
3. Integrate with existing EFBs / receivers rather than competing head-on with ForeFlight or APEX HUD.

## Technical limits of consumer HUD glasses (expert view)

- No focus-to-infinity optics (collimation).
- Can't be electronically aligned (boresighted) with the aircraft.
- Brightness/dimming may not cover bright sun to dark night.
- Meta Display = single small right-eye display.

## Sources

- https://generalaviationnews.com/2026/07/17/apex-hud-aviation-glasses/
- https://avweb.com/air-shows-events/airventure/apex-hud-glasses-pair-with-ifly-efb/
- https://aero-news.net/index.cfm?do=main.textpost&id=CFBDB97E-0E26-4273-97BF-A7587B81886A
- https://ulcontrol.com/en/ul-vision-in-the-spotlight-in-aviation-et-pilote/
- https://www.fastcompany.com/3033884/regular-pilots-can-now-have-superhuman-augmented-reality-fighter-pilot-vision
- https://www.euroga.org/forums/maintenance-avionics/2785-aero-glass
- https://www.flyingmag.com/news-google-release-head-glasses/
- https://apps.apple.com/us/app/aeroadsb/id1560168705
