# CLAUDE.md — context for Claude sessions on this repo

## Project
Pilot heads-up display app for Meta AI glasses (Ray-Ban Display; audio callouts on Ray-Ban Meta). Target users: US general aviation hobby/private pilots, VFR only. Stage: research → prototype.

Read `docs/` first, especially `docs/03-mvp-features.md` (proposed MVP) and `docs/05-risks-and-next-steps.md`.

## Team
Two founders: one in India (product/research), one in the US (pilot network, hardware testing, owns the Meta developer account because India isn't a Meta AI-glasses supported country).

## Technical direction
- Phone app + Meta Wearables Device Access Toolkit (DAT) 1.0 — Swift (iOS) / Kotlin (Android). Web apps (HTML/JS) possible for Ray-Ban Display only.
- Meta provides a Claude Code plugin and a docs MCP server: see https://github.com/facebook/meta-wearables-dat-ios (and `-android`) and `https://mcp.developer.meta.com/wearables`.
- Traffic/weather from portable ADS-B receivers (Stratux, Sentry) via GDL 90 over UDP (default port 4000).

## Rules
- Safety first: never present the app as a primary or certified instrument; label GPS altitude as GPS.
- Keep glasses screens minimal and glanceable; prefer audio.
- No secrets in the repo.
- Mark doc changes as *proposed* unless the team has decided.
