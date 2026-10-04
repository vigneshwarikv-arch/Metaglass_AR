# Metaglass_AR — Pilot Heads-Up Display on Meta AI Glasses

A companion app that shows key flight information to general aviation (hobby/private) pilots on **Meta Ray-Ban Display** glasses, with voice callouts on regular **Ray-Ban Meta** glasses, so pilots can keep their eyes outside the cockpit.

> Status: **Idea / research stage** (October 2026). No code yet.

## The idea in one paragraph

Pilots repeatedly look down at instruments or an iPad for groundspeed, direction, nearby traffic, nearest airport and weather. Dedicated head-up display glasses for general aviation exist (APEX HUD, ~$4,000, shipping 2027), but nobody has built this for Meta's consumer glasses. We want a cheaper, lighter, everyday-looking option: a phone app that reads GPS and a portable ADS-B receiver, then pushes a small glanceable screen and audio alerts to the glasses.

## Repo layout

| Folder | What goes here |
| --- | --- |
| `docs/` | Research, product decisions, specs |
| `app/` | Source code (phone app + glasses integration) |
| `data/` | Datasets: airports, test flight logs, sample ADS-B/GDL 90 captures |
| `design/` | Screen mockups for the glasses display, branding |

## Documents

1. [Platform research — building for Meta glasses](docs/01-platform-research.md)
2. [Competitive landscape — who has tried this](docs/02-competitive-landscape.md)
3. [MVP features — free and paid tiers](docs/03-mvp-features.md)
4. [Market size — how many hobby pilots](docs/04-market-size.md)
5. [Risks, open questions and next steps](docs/05-risks-and-next-steps.md)

## Team

- **@vigneshwarikv-arch** (India) — product, research *(edit name/role)*
- **US partner** — US pilot network, hardware testing *(add your name and role here)*

See [CONTRIBUTING.md](CONTRIBUTING.md) for how we work together.
