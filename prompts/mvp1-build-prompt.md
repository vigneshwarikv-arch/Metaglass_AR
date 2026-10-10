# Claude Code prompt — build MVP 1 + website

Paste everything below the line into Claude Code, opened in this repo.

---

You're working in my repo `Metaglass_AR`. Before doing anything, read `CLAUDE.md`, `docs/06-product-plan.md` and `docs/07-mvp1-consumer.md` — they are the source of truth for scope. The brand name is not decided: use the placeholder `[BrandName]` everywhere (one constant/variable so it's easy to replace later).

## Goal

Build **MVP 1 — the consumer "What's that plane?" app** and a **marketing website** whose first page presents all MVP 1 features.

## MVP 1 features (build all four, nothing more)

1. **Planes near me** — list + simple map of aircraft within ~50 km of the user, sorted by distance, showing airline, flight number/callsign, aircraft type, altitude, speed, distance and compass direction.
2. **Point to identify** — user holds the phone toward the sky; using compass + tilt, highlight the aircraft in that direction and show its details. If none is within the tolerance, say which aircraft is nearest and which way to turn.
3. **Ask your glasses** — on Meta glasses, the user says "Hey Meta, what's that plane?"; use the head direction from the glasses to pick the aircraft and answer by voice in one short sentence, e.g. "IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north." On Meta Ray-Ban Display, also show a small card.
4. **Plane details** — route (origin → destination), aircraft type, registration, altitude, speed, heading, where data is available; show "unknown" cleanly when it isn't.

Out of scope: history, alerts, collections, photos, AR overlays, VR, watch app, accounts, payments, anything pilot-mode.

## Part A — website (do this first, it's quick)

- Static site in `site/` (plain HTML/CSS/JS, no build step). Move the current pilot-focused `site/index.html` to `site/pilot.html` and keep `site/landing.html` in sync with the new home page as `site/index.html` is rebuilt.
- **First screen of the home page:** headline + one-line pitch ("Look up. Ask. Know the plane."-style, your wording), a visual mock of the glasses voice answer, and **all four MVP 1 features** clearly listed with a one-line description each. No scrolling needed to see the feature list on desktop.
- Below the fold: how it works (phone + glasses, data from live flight-tracking networks), who it's for (spotters, families, travellers, people near airports), FAQ (which glasses are supported: Meta Ray-Ban Display and Ray-Ban Meta; phone: Android first), and a **"Pilot mode — coming soon"** section linking to `pilot.html` with a beta sign-up.
- Email sign-up form for early access: no backend yet — keep the `FORM_ENDPOINT` pattern from the current page (empty = show "not connected yet" message).
- Mobile-friendly, accessible, light/dark mode, fast. Original design — don't copy competitor sites. Don't use Meta's or Ray-Ban's logos; mention product names in text only, with a "not affiliated" footer line.
- Don't change `.github/workflows/pages.yml` (deploy stays manual).

## Part B — Android app

**Stack:** Kotlin, Jetpack Compose, Gradle (Kotlin DSL), in `app/android/`. Use the Meta Wearables Device Access Toolkit (DAT) 1.0 for Android for the glasses — install Meta's Claude Code plugin / use the docs MCP server listed in `CLAUDE.md` and follow the official docs and sample app. **Don't guess DAT APIs**; if something (e.g. voice invocation, head-motion streaming, display cards) isn't clear from the docs, implement it behind an interface with a clearly marked TODO and tell me.

**Architecture (required):**
- `core` — pure Kotlin, no Android imports. Aircraft model; geometry (distance, bearing, elevation angle from user to aircraft using lat/lon/altitude); target selection ("which aircraft is closest to this bearing + elevation, within a tolerance"); sentence builder for the spoken answer (units: km and feet by default, setting for miles).
- `data` connectors behind one interface:
  - OpenSky Network REST API for positions (respect its non-commercial terms and rate limits; cache responses; optional credentials read from `local.properties`, never committed).
  - A callsign → route / aircraft-type lookup service (pick a free one for prototyping, check and note its terms in the README).
  - A **sample-data connector** with recorded flights so the app works offline and in tests.
- `output` connectors: phone UI, and Meta glasses (voice answer + Display card) via DAT. Core must not depend on any connector.

**Phone app screens:** Nearby list + map, Point-to-identify view (live compass/tilt indicator), Plane details, Settings (units, radius, data source: live/sample, glasses connection status). Show data attribution (OpenSky) where required. Handle: no location permission, no internet, no aircraft nearby, rate-limited API.

**Tests:** unit tests for geometry and target selection with known cases (e.g. aircraft due north 10 km away at 3,000 m → bearing ≈ 0°, elevation ≈ 16.7°), and for the spoken sentence builder.

## How to work

1. First, show me a short plan (files you'll create, libraries, which free route-lookup API, open questions). **Wait for my OK.**
2. Build Part A, show me the page, then commit.
3. Build `core` + tests, then data connectors, then phone UI, then glasses connector.
4. If the Android SDK isn't available in your environment, still write the code, run the `core` tests on the JVM, and tell me exactly what you couldn't build or test.
5. Work on a branch `mvp1`, commit in small steps with clear messages, and open a pull request when done.
6. Write `app/android/README.md`: setup steps, how to add OpenSky credentials locally, how my US partner installs a test build and pairs Meta glasses (Meta developer mode / release channel), and a checklist of what to test on real glasses.

## Done when

- `site/index.html` shows all four MVP 1 features on the first screen and works on phone and desktop.
- `core` tests pass; the app builds (`./gradlew assembleDebug`) or you've clearly explained why it couldn't build here.
- The app works end-to-end with sample data, and with live OpenSky data when online.
- Glasses voice flow is implemented per DAT docs, or stubbed behind an interface with TODOs listed in the PR.
- Nothing secret is committed.
