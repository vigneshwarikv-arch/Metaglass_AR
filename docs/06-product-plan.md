# 06 — Product Plan: Two Products, One Engine

*Status: **decided direction** (October 2026). Brand name: **TBD** — see [10-naming.md](10-naming.md).*

## Why two products

The original idea (a heads-up display for pilots on Meta glasses) targets a small market (~180,000 US hobby pilots) and needs flight testing before launch. A consumer "what's that plane?" app reaches far more people, has no FAA/safety risk, needs no extra hardware, and can be built and tested in India. Both share the same core technology.

| | **MVP 1 — Consumer "What's that plane?"** | **MVP 2 — Pilot mode** |
| --- | --- | --- |
| Who | Plane spotters, aviation fans, families, people near airports, travellers | US VFR hobby / private / sport pilots |
| Where used | On the ground | In the cockpit |
| Core question | "What plane is that up there?" | "What traffic is around me, and where am I going?" |
| Data | Internet flight data (OpenSky for prototype → commercial licence before paid launch) | Phone GPS + portable ADS-B receiver over Wi-Fi (GDL 90) |
| Devices | Phone + Meta glasses (voice); watches later | Phone + Meta glasses (Ray-Ban Display screen, Ray-Ban Meta audio) |
| Risk | Crowded phone market | Safety, regulation, needs flight testing |
| Detail | [07-mvp1-consumer.md](07-mvp1-consumer.md) | [08-mvp2-pilot-mode.md](08-mvp2-pilot-mode.md) |

## Shared core engine

Both products are the same engine with different inputs and rules:

```
            ┌──────────── DATA CONNECTORS ────────────┐
            │ Internet API (OpenSky / licensed feed)  │
            │ ADS-B receiver: GDL 90 over Wi-Fi       │──┐
            │ Phone GPS + compass + tilt              │  │
            └─────────────────────────────────────────┘  │
                                                         ▼
                                   ┌──────────────── CORE ENGINE ────────────────┐
                                   │ Aircraft model (one common format)          │
                                   │ Geometry: distance, bearing, elevation,      │
                                   │   "which aircraft is in that direction?"     │
                                   │ Aircraft/route enrichment                    │
                                   │ Alert + callout rules (consumer / pilot)     │
                                   └──────────────────────────────────────────────┘
                                                         │
            ┌──────────── OUTPUT CONNECTORS ──────────────┴─┐
            │ Phone screen   │ Meta glasses (voice, Display) │ Watch (later) │
            └────────────────────────────────────────────────┘
```

**Rule:** the core engine never knows which device or data source it is talking to. Each data source and each device gets its own small connector. Adding Garmin receivers, Even Realities glasses or Apple Watch later = one new connector, not a rewrite.

## Release strategy

1. Build the shared engine first.
2. **Launch MVP 1 publicly** (phone app + glasses voice). Glasses distribution starts through Meta test channels (up to 100 testers) until Meta's discovery surfaces open.
3. **Announce pilot mode on day one** with a beta sign-up — claim the space without shipping untested features.
4. **Closed pilot beta:** US partner flight-tests (VFR, daytime, with a safety pilot), then 20–50 real pilots via Meta release channel.
5. **Public pilot mode** only after flight tests and beta feedback.
6. Optional early pilot features with low risk: auto logbook, flight video with data overlay, pre-flight traffic view on the ground.

Not decided: releasing MVP 2 publicly within a week of MVP 1 — rejected because flight testing can't be done safely in a week.

## Platform choices

- **Glasses first:** Meta (94% of display-less AI glasses shipments in H1 2026; one integration covers Ray-Ban Display and Ray-Ban Meta). Second: Even Realities. Watch: Android XR, Apple glasses (2027+).
- **Phone first:** Android (testable in India, DAT on Maven Central). iOS next — many US pilots use iPhone/iPad; check DAT App Store status.
- **Lenses for test glasses:** non-polarized (clear or transitions).
