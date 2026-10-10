# CLAUDE.md — context for Claude sessions on this repo

## Project
Voice-first aviation app for smart glasses and phones. Brand name **TBD** — use a placeholder like `[BrandName]`, never invent a final name. Two products on one shared engine:
- **MVP 1 (build first):** consumer "What's that plane?" — planes near me, point-to-identify, "Hey Meta, what's that plane?" voice answer on Meta glasses, plane details. Ground use only. Spec: `docs/07-mvp1-consumer.md`.
- **MVP 2 (later):** pilot mode — GPS readouts, traffic callouts from portable ADS-B receivers (GDL 90), nearest airport. Spec: `docs/08-mvp2-pilot-mode.md`.

Read `docs/06-product-plan.md` first.

## Team
Two founders: one in India (product, phone app testing), one in the US (Meta glasses testing, pilot network, flight testing, owns the Meta developer account — India isn't a Meta AI-glasses supported country).

## Technical direction
- Architecture: shared core engine + pluggable **data connectors** (internet API, GDL 90 receiver, phone sensors) and **output connectors** (phone, Meta glasses, later watch). Core never depends on a specific device or source.
- Android first (Kotlin, Jetpack Compose); iOS later.
- Meta Wearables Device Access Toolkit (DAT) 1.0 for glasses. Meta provides a Claude Code plugin and docs MCP server: https://github.com/facebook/meta-wearables-dat-android, https://github.com/facebook/meta-wearables-dat-ios, `https://mcp.developer.meta.com/wearables`. Verify APIs against these docs; don't guess.
- MVP 1 data: OpenSky Network REST API for prototyping (non-commercial terms, rate-limited). Commercial feed needed before paid launch.
- MVP 2 data: GDL 90 over UDP (default port 4000) from Stratux / Stratus 4.

## Rules
- Safety first for pilot mode: never present it as a primary or certified instrument; label GPS altitude as GPS; VFR only.
- Keep glasses output minimal; prefer short spoken answers.
- No secrets in the repo (API keys, OpenSky credentials, Meta tokens) — use local config / `.env`, which is git-ignored.
- Show data attribution (e.g. OpenSky) where its terms require it.
- Mark doc changes as *proposed* unless the team has decided.
- Website in `site/`; deploy is manual-only (`.github/workflows/pages.yml`).
