# 05 — Risks, Open Questions and Next Steps

## Key risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| **Regulation (FAA)** | App could be seen as a navigation instrument | Position as supplementary situational aid only; never replace certified instruments; VFR only; legal check before launch |
| **Pilot distraction** | Safety and liability | Minimal, glanceable screen; audio-first; easy off switch |
| **Display limits** | Bright sun / night readability, no alignment with outside world | No precise overlays; test in real cockpits early |
| **Competition** (APEX HUD, EFB apps adding glasses support) | Lose differentiation | Move fast; price low; integrate with existing EFBs/receivers |
| **Meta platform** | Store/discovery not open yet; iOS App Store rejection reports; policy changes | Watch Developer Center; Android-first if iOS blocked |
| **India not a supported country** | Can't use full toolkit/Developer Center from India | US partner owns the Meta developer account and testing hardware |
| **Small market** | Limited revenue | Validate willingness to pay before building; expand to students and other countries |

## Open questions

- [ ] Would hobby pilots pay for "eyes outside" if they already have an iPad EFB? (interviews)
- [ ] What price works? Monthly vs annual?
- [ ] iOS or Android first? (check DAT 1.0 App Store status)
- [ ] Which ADS-B receivers to support first (Stratux, Sentry)?
- [ ] Display glasses only, or audio-first for regular Ray-Ban Meta?
- [ ] Partner with an EFB (ForeFlight / Garmin Pilot / iFly) or stand alone?
- [ ] What do FAA rules say about personal electronic devices / head-worn displays for this use?

## Next steps (proposed)

1. **Validate** — interview 10–20 US hobby pilots and flight instructors (US partner's network).
2. **Accounts** — US partner creates Meta Wearables Developer Center account; both get GitHub access.
3. **Hardware** — US partner gets Ray-Ban Display (or Ray-Ban Meta for audio-only prototype) + a Stratux/Sentry receiver.
4. **Prototype v0 (ground test)** — phone app reads GPS → shows groundspeed, track, direct-to on glasses; test while driving/walking, not flying.
5. **Prototype v1** — add GDL 90 traffic parsing + audio callouts; test with recorded ADS-B data (`data/`).
6. **Flight test** — with a safety pilot, VFR, daytime.
7. **Beta** — release channel (up to 100 testers).
