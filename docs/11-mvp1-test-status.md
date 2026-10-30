# 11 — MVP 1 Test Status

*Last updated: 19 Oct 2026. Update this page after each test session.*

## Summary

| # | Feature | iPhone (real device) | Android (real device) | Meta glasses |
| --- | --- | --- | --- | --- |
| 1 | Planes near me | ✅ Passed | Not reported yet | — |
| 2 | Point to identify | ✅ Passed | Not reported yet | — |
| 3 | Ask your glasses | — | — | ⏳ **Not tested yet** |
| 4 | Plane details | ✅ Passed | Not reported yet | — |

Also verified (automatically, on every push to GitHub): engine unit tests for both apps, the iPhone
build, and the Android build. The Android app was also checked end to end on an emulator with live
Chennai data, sample data and simulated glasses (screenshots in `design/screenshots/`).

## Test log

| Date | Who | Device | Result |
| --- | --- | --- | --- |
| Oct 2026 (week of 12 Oct) | US partner | iPhone | Features 1, 2 and 4 look fine. Glasses not tested. |

## Still to test: Ask your glasses (feature 3)

Needs real Meta glasses in a supported country (US partner). Setup is in `app/ios/README.md` →
"Meta glasses" (Developer Mode in the Meta AI app; bundle ID, URL scheme and Motion / Speech /
Voice Invocations enabled in the Wearables Developer Center).

Please record a result for each line:

- [ ] **Connect:** Settings shows *Connected · head tracking on* (and *display card on* with Ray-Ban Display).
- [ ] **Head direction:** look level at the horizon → about 0°; look up → a positive angle; the plane
      named is roughly where you're looking. *This confirms the +X up / +Z forward axis fix.*
- [ ] **"Hey Meta, start [BrandName]":** the app opens and speaks the answer within ~2 s.
- [ ] **"What's that plane?"** (microphone allowed, app open): the answer is spoken.
- [ ] **Audio:** the answer plays in the **glasses** speakers, not the phone.
- [ ] **Display card** (Ray-Ban Display only): airline · type, route, altitude · distance.
- [ ] **Correctness:** 5 answers compared with what you see or Flightradar24.
- [ ] **Speed:** question to start of answer under 2 s.
- [ ] **Edge cases:** no planes nearby; phone offline; glasses off and on again.
- [ ] **Battery:** battery used over 30 minutes.

Quick dry run without glasses: **Settings → Simulated glasses** on the iPhone.

## Known issues to watch

- Free route data (adsbdb) is sometimes wrong for a given flight (seen in Chennai: a plane landing at
  MAA was listed as Rajkot → Delhi). Note how often this happens during testing.

---

# Pilot mode (MVP 2) — test status

*Started 31 Oct 2026. Full checklist: [12-pilot-flight-test-checklist.md](12-pilot-flight-test-checklist.md). Safety rules there apply to every test.*

| # | Feature | Android emulator (India) | Android phone | iPhone | Glasses | Real receiver | Flight |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GS / TRK / GPS ALT (phone GPS) | — (emulator has fixed GPS) | Not yet | Not yet | — | — | Not yet |
| 2 | Direct-to | ✅ Passed (replay) | Not yet | Not yet | Card ✅ (simulated) | Not yet | Not yet |
| 3 | Traffic callouts | ✅ Passed (replay + simulated receiver) | Not yet | Not yet | Card ✅ (simulated) | Not yet | Not yet |
| 4 | Nearest airport | ✅ Passed | Not yet | Not yet | — | Not yet | Not yet |
| 5 | Receiver status | ✅ Passed (replay) | Not yet | Not yet | — | Not yet | — |
| — | Disclaimer, OFF, notification "Turn off", "pilot mode off" | ✅ Passed | Not yet | Not yet | Voice off ✅ (simulated) | — | Not yet |

Also verified automatically on every push: GDL 90 decoding (spec examples), callout rules, scenarios and airports — same tests on
Android and iPhone; the iPhone app build. Emulator screenshots: `design/screenshots/20-28`.

**How the emulator test was run:** `adb emu redir add udp:4000:4000`, then
`python tools/gdl90-replay/gdl90_replay.py scenario converging` (sends to 127.0.0.1:4000) — the app received it through the real
Wi-Fi receiver code path. Then Pilot settings → Simulated receiver → "Busy pattern".

### Pilot mode test log

| Date | Who | Device / receiver | Result |
| --- | --- | --- | --- |
| 31 Oct 2026 | Claude (India) | Android emulator (API 36) + replay tool + simulated receiver + simulated Ray-Ban Display | Features 2–5, disclaimer and all three "off" controls work. Found and fixed: screen closing itself on open; "pilot mode off" not recognised. |

### Not tested yet (needs the US partner)

- A real Stratux / Stratus 4: connection, GPS altitude datum, traffic compared with ForeFlight, two devices at once.
- Phone GPS readouts while moving (drive test), and on a real iPhone.
- Real glasses: card readability in sun, audio over engine noise, "pilot mode off" with noise.
- Android screen-off behaviour on a real phone (foreground service), battery.
