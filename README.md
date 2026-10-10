# Metaglass_AR — "What's That Plane?" + Pilot Mode

Working repo for a voice-first aviation app for smart glasses and phones. **Brand name: TBD** ([options](docs/10-naming.md)).

> Status: **Planning → building MVP 1** (October 2026).

## The idea

Two products on one shared engine:

1. **MVP 1 — Consumer "What's that plane?"** Look up, ask *"Hey Meta, what's that plane?"*, and hear the airline, aircraft, route and altitude — hands-free on Meta glasses, or by pointing your phone at the sky. For plane spotters, aviation fans, families and travellers. → [docs/07](docs/07-mvp1-consumer.md)
2. **MVP 2 — Pilot mode.** Groundspeed, direction to destination, traffic callouts and nearest airport on the glasses and in the pilot's ear, using phone GPS and a portable ADS-B receiver. For US VFR hobby pilots. → [docs/08](docs/08-mvp2-pilot-mode.md)

MVP 1 launches first. Pilot mode is announced at launch and goes through closed beta and flight testing before public release. → [docs/06](docs/06-product-plan.md)

## Repo layout

| Folder | What goes here |
| --- | --- |
| `docs/` | Research, product decisions, specs |
| `app/` | Source code (phone app + glasses integration) |
| `site/` | Marketing website |
| `data/` | Datasets: airports, sample flight data, recorded ADS-B/GDL 90 |
| `design/` | Screen mockups, branding |
| `prompts/` | Prompts for building with Claude Code |

## Documents

**Current plan**
- [06 — Product plan: two products, one engine](docs/06-product-plan.md)
- [07 — MVP 1: consumer "What's that plane?"](docs/07-mvp1-consumer.md)
- [08 — MVP 2: pilot mode](docs/08-mvp2-pilot-mode.md)
- [09 — Background: ADS-B, data sources, devices](docs/09-data-and-devices.md)
- [10 — Naming (undecided)](docs/10-naming.md)

**Research**
- [01 — Platform research: building for Meta glasses](docs/01-platform-research.md)
- [02 — Competitive landscape: pilot HUDs](docs/02-competitive-landscape.md)
- [03 — Original pilot MVP (superseded by 08)](docs/03-mvp-features.md)
- [04 — Market size: hobby pilots](docs/04-market-size.md)
- [05 — Risks, open questions, next steps](docs/05-risks-and-next-steps.md)

## Team

- **@vigneshwarikv-arch** (India) — product, research, phone app testing *(edit name/role)*
- **US partner** — glasses testing, US pilot network, flight testing *(add your name and role here)*

See [CONTRIBUTING.md](CONTRIBUTING.md) for how we work together.
