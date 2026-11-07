# 12 — Pilot Mode Test Checklist (ground → drive → flight)

*Status: **proposed**. For the US partner. Pilot mode is a supplementary aid only: never fly by it.*

Work through the stages in order. Don't start a stage until the one before it passes. Write each result in
[11-mvp1-test-status.md](11-mvp1-test-status.md) → "Pilot mode (MVP 2)", with date, phone, glasses and receiver.

## Safety rules for every test

- **VFR, daytime, good weather only.** No test in IMC, at night, or in busy Class B airspace.
- **Flight tests always with a safety pilot** who flies the aircraft and keeps a lookout. The tester only watches the app.
  The safety pilot is pilot in command and can end the test at any time.
- The app is never used for navigation or collision avoidance during tests. Normal see-and-avoid, ATC, charts and instruments as always.
- Phone and receiver mounted so they can't fall or block controls; cables clear of the controls.
- Check the FAA rules on portable electronic devices and head-worn displays before flying with glasses. Glasses must not block the view.
- Brief before each flight: what is being tested, who says "stop", and that "pilot mode off" / OFF ends it.

## What you need

- Phone with the test build (Android: debug APK from GitHub Actions; iPhone: built from Xcode, see `app/ios/README.md`).
- A Stratux or Stratus 4 (both send standard GDL 90 over Wi-Fi). Note the model and firmware version.
- Optional: Meta Ray-Ban Display and/or Ray-Ban Meta glasses, set up as in `app/android/README.md` → "Meta glasses".
- Optional: an iPad with ForeFlight connected to the same receiver (to compare traffic and to test two devices at once).

## Stage 1 — On the ground, at home or at the airport

1. [ ] **Simulated receiver:** Pilot settings → Simulated receiver on → each scenario. Callouts are spoken; the list and the
       glasses card update. Turn it **off** afterwards.
2. [ ] **Connect the receiver:** power it on, join its Wi-Fi network on the phone, open pilot mode. Status shows
       "<name> · connected". iPhone: allow "Local Network" when asked. Note how long it takes.
3. [ ] **Receiver GPS:** outside with a clear sky, status changes to "receiver GPS OK" and "Position from receiver GPS".
4. [ ] **GPS altitude:** compare the app's GPS ALT with the field elevation. Note the difference and whether the app says
       "ellipsoid" or "MSL". (Stratux usually reports ellipsoid height, ~100 ft below MSL in California.)
5. [ ] **Traffic on the ground:** near a busy airport, traffic appears; compare with ForeFlight on the iPad at the same time.
       Note aircraft shown in one app but not the other.
6. [ ] **Two devices at once:** ForeFlight (iPad) and this app (phone) both receive traffic at the same time. Note any drop-outs.
7. [ ] **Two apps on one phone (expected not to work well):** ForeFlight and this app on the same iPhone. Note what happens.
8. [ ] **Receiver off:** switch the receiver off; within ~3 s status says "no data"; GPS falls back to the phone.
9. [ ] **Off controls:** OFF button; Android notification "Turn off"; "pilot mode off" said to the glasses. Each stops pilot mode.
10. [ ] **Screen off (Android):** with pilot mode on, lock the phone for 5 minutes near traffic. Callouts keep coming.
        (iPhone keeps the screen on in pilot mode; note battery use.)
11. [ ] **Battery:** battery used in 30 minutes of pilot mode with the receiver (and glasses, if used).

## Stage 2 — Drive test (car, passenger operates the phone)

The driver only drives. A passenger holds the phone.

1. [ ] **Groundspeed and track:** compare with the car speedometer (kt = mph × 0.87) and a compass app on straight roads.
2. [ ] **Turns:** track follows within a few seconds; no wild jumps when stopping (track should blank out below 5 kt).
3. [ ] **Direct-to:** pick a nearby airport. Distance goes down as you approach; the arrow points the right way; ETE is sensible.
4. [ ] **Nearest airport:** changes correctly as you drive past airports (receiver connected).
5. [ ] **Phone GPS only:** receiver off, readouts still work from the phone ("Position from phone GPS").

## Stage 3 — Flight test (VFR, daytime, safety pilot)

Short local flight, away from busy airspace, with planned altitudes. The tester notes times and what was heard.

1. [ ] **Before engine start:** pilot mode on, receiver connected, receiver GPS OK, destination set.
2. [ ] **Readouts in flight:** GS and TRK against the aircraft GPS/panel; GPS ALT against the altimeter (expect a difference; note it).
3. [ ] **Traffic callouts:** for each callout, note: clock position, distance and altitude said vs what the safety pilot sees or ATC calls.
       Note false callouts, missed traffic, repeated callouts and delays.
4. [ ] **Never ownship:** the app never calls out our own aircraft (including a TIS-B "shadow").
5. [ ] **Pattern work:** at a non-towered field with traffic, note whether callouts are useful or too many.
6. [ ] **Glasses (if worn):** Display card readable in sun; audio audible over engine noise with a headset; nothing blocks the view.
7. [ ] **Direct-to:** arrow and ETE match the panel GPS.
8. [ ] **Turn off in flight:** "pilot mode off" by voice works with engine noise.

## After each stage

- Record results in docs/11, with screenshots if possible.
- Anything unsafe or confusing → GitHub issue labelled `pilot-safety`, before the next stage.
- Optional: record the receiver's data for replay in India: on a laptop on the receiver's Wi-Fi,
  `python tools/gdl90-replay/gdl90_replay.py record flight1.gdl90` (no personal data, just traffic and positions).
