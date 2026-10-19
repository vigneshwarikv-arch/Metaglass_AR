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
