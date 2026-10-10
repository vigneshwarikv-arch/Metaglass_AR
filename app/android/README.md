# [BrandName] — Android app (MVP 1: "What's that plane?")

Kotlin + Jetpack Compose, Gradle (Kotlin DSL). Phone app plus Meta glasses support through the
Meta Wearables Device Access Toolkit (DAT) 1.0.1.

## What's in here

| Module | What it is | Android? |
| --- | --- | --- |
| `core` | The engine: aircraft model, geometry (distance, bearing, elevation incl. Earth curvature), "which plane am I pointing at?", the spoken sentence and Display card, glasses head-direction maths, connector interfaces | No (pure Kotlin) |
| `data` | Data connectors: OpenSky (live positions), adsbdb (airline / type / registration / route), sample data (synthetic flights around Chennai and Milpitas), caching + rate-limit back-off | No (pure Kotlin) |
| `glasses` | Output connector for Meta glasses (DAT): registration, session, head motion, speech, "Hey Meta" launch, Display card, spoken answer | Yes |
| `app` | The phone app: Nearby (list + map), Point to identify, Plane details, Settings | Yes |

`core` never depends on a connector. A new data source (e.g. a GDL 90 receiver for pilot mode) or a new
device (e.g. a watch) is one new connector.

The brand name is a placeholder: change `core/.../Brand.kt` and `app/src/main/res/values/strings.xml`.
The package name `com.metaglassar.planes` is a working name too; change it before the first Play Store upload.

## Setup

1. Install **Android Studio** (it includes the JDK and Android SDK) and open the `app/android` folder,
   or use the command line with JDK 17 and the Android SDK (platform 36.1, build-tools 36).
2. Android Studio creates `local.properties` with `sdk.dir`. If you build from the command line, create it:
   ```properties
   sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
   ```
3. Build and run the tests:
   ```bash
   ./gradlew test assembleDebug
   ```
   (`gradlew.bat` on Windows.) The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

### Add OpenSky credentials (optional, recommended)

Without an account the app uses OpenSky anonymously: about **400 updates a day**, refreshed every 30 s.
With a free account: 4,000 a day, refreshed every 10 s.

1. Create a free account at https://opensky-network.org and open **Account → API client**; create a client.
2. Add these lines to `app/android/local.properties` (this file is git-ignored — **never commit it**):
   ```properties
   opensky.clientId=your-client-id
   opensky.clientSecret=your-client-secret
   ```
3. Rebuild. Settings → Flight data shows "OpenSky account connected".

Note: values in `local.properties` are compiled into the APK's BuildConfig. That's fine for test builds
shared with testers, but a public release needs a small backend instead of shipping the secret.

### Data sources and their terms

| Data | Source | Terms (checked Oct 2026) |
| --- | --- | --- |
| Live positions | [OpenSky Network REST API](https://openskynetwork.github.io/opensky-api/rest.html) | **Non-commercial use only.** Rate limited (see above). The app shows "Flight positions: The OpenSky Network". A commercial feed (ADS-B Exchange, Flightradar24 API, FlightAware AeroAPI) is needed before a paid launch. |
| Airline, type, registration, route | [adsbdb.com](https://www.adsbdb.com) (`/v0/aircraft/{icao24}?callsign=`, falls back to `/v0/callsign/{callsign}`) | Free, no key; the code is MIT. Route data comes from community sources whose owners ask for permission before reuse, so use it for prototyping only and replace it with licensed schedule/route data before launch. Routes can be out of date; the app says so on the details screen. Results are cached 6 h and only the 15 nearest aircraft are looked up. |
| Sample data | `data/src/main/resources/sample/flights.json` | Made up for testing. Real airline and airport names, fake transponder codes and registrations. |

## Using the app

- **Nearby**: planes within the radius (default 50 km), nearest first, with a north-up map. Tap one for details.
- **Point**: hold the phone up and point the back at a plane. The sky view shows planes around where you point.
  The one inside the ring is identified. If nothing is in the ring, it says which way to turn.
  **Ask** speaks the answer (on the glasses if connected).
- **Plane details**: route, type, registration, altitude, speed, heading. Missing values show "Unknown".
- **Settings**: units (km / miles; altitude always in feet), radius, **Live / Sample** data, glasses connection.

**Sample mode** works offline and sets your location to Chennai or Milpitas, so either founder can test
the other's sky.

## Simulated glasses (test without hardware, e.g. in India)

**Settings → Simulated glasses** turns on Meta's MockDeviceKit: a pretend pair of Ray-Ban Meta or
Meta Ray-Ban Display glasses that the app connects to through the real DAT code path. Use it with
**Sample** data or live data:

1. Turn the switch on. Within a few seconds "Meta glasses" shows *Connected to Simulated … · head tracking on · listening*.
2. Move **Turn head** / **Look up**, or tap **Look at the nearest plane**.
3. Tap **Say: "What's that plane?"** (or **Say: "Hey Meta, start [BrandName]"**). The answer appears at the
   top of the screen and is spoken through the phone; with *Meta Ray-Ban Display* the card appears in
   **What the Display shows**.

No Meta account, Meta AI app or Developer Center setup is needed for the simulator. Turn it off before
connecting real glasses.

## For the US partner: test build + Meta glasses

The glasses features can only be tested in a country where Meta AI glasses are supported. They also need
a Meta developer account (you own it).

### 1. Wearables Developer Center (one time)

At https://wearables.developer.meta.com:

1. Create a project for this app. Package name: `com.metaglassar.planes`.
2. Turn on the capabilities the app uses: **Motion**, **Speech** and **Voice Invocations**. All three are
   *experimental* in DAT 1.0: allowed for development and release-channel testing, not yet for production.
3. Voice Invocations: request approval and set the spoken app name (`[BrandName]` for now; this is the
   name in "Hey Meta, start ___").
4. Callback URL scheme: `metaglassar` (this matches `AndroidManifest.xml`).
5. For **release-channel** builds (sharing with up to 100 testers), copy the Application ID and Client
   Token into `local.properties` (not committed):
   ```properties
   mwdat.applicationId=...
   mwdat.clientToken=...
   ```
   For your own development builds, leave them out: the app then uses `0`/`0`, which means Developer Mode.

### 2. Install the test build on your Android phone

- From Android Studio: connect the phone with USB debugging on and press **Run**, or
- From the command line: `./gradlew installDebug`, or
- Send the APK (`app/build/outputs/apk/debug/app-debug.apk`) and open it on the phone (allow "install unknown apps").

Requirements: Android 12 or newer, with the **Meta AI** app installed and your glasses paired in it.

### 3. Enable Developer Mode and connect

1. In the **Meta AI app**, enable **Developer Mode** for your glasses (see Meta's
   [getting-started guide](https://wearables.developer.meta.com/docs/develop/)). It is set per device
   and can reset after app or firmware updates; if connecting stops working, check it first.
2. Open the app → **Settings → Meta glasses → Connect glasses**. The Meta AI app opens; approve.
   Back in the app the status should read "Connected to <glasses> · head tracking on".
3. Tap **Allow glasses microphone** (opens Meta AI again) so you can ask by voice.

### 4. Release channel (sharing with other testers)

In the Wearables Developer Center, create a release channel, add testers (up to 100), and upload a build
signed with the same key each time. Testers install the app, and Meta AI gates access through the channel.
Developer Mode is not needed for release-channel builds that use real IDs.

## Checklist: test on real glasses

Do this outdoors with planes in view, or near an airport. Note the glasses model (Ray-Ban Meta or Meta Ray-Ban Display).

- [ ] **Connect**: Settings shows Connected, head tracking on (and display card on, with Display glasses).
- [ ] **Head direction**: look level at the horizon facing north (use a phone compass app). Point screen → Ask.
      The answer should name a plane roughly in that direction. If directions look mirrored or up/down is
      wrong, the glasses axis convention differs from Meta's sample; see `TODO(glasses)` in `core/.../HeadAim.kt`.
- [ ] **"Hey Meta, start [BrandName]"**: the app comes to the front and the answer is spoken within ~2 s.
- [ ] **"What's that plane?"** (with the microphone allowed and the app open): the answer is spoken.
- [ ] **Audio**: the answer plays in the **glasses** speakers, not the phone speaker. Note if it ducks or interrupts Meta AI.
- [ ] **Display card** (Ray-Ban Display only): a card shows airline · type, route (e.g. MAA → DEL) and altitude · distance.
- [ ] **Correctness**: compare 5 answers with what you see / Flightradar24. Note wrong planes, wrong routes, and how far off the direction was.
- [ ] **Speed**: time from question to start of answer. Target: under 2 s.
- [ ] **Edge cases**: no planes nearby; phone offline; glasses taken off then on again (session should recover).
- [ ] **Battery**: battery used over 30 minutes of use with glasses connected.

## Known gaps / TODOs

- **"Hey Meta, what's that plane?" as one phrase is not possible with DAT 1.0.** Voice Invocations only
  launch the app ("Hey Meta, start [BrandName]"); we answer immediately on launch. The free-form question
  works through DAT Speech while the app is running and the glasses microphone is allowed.
  A Meta AI connector (MCP) could make the exact phrase work later.
- **Spoken audio** uses Android text-to-speech over the glasses' Bluetooth audio. DAT 1.0 has no
  speak-on-glasses API. Needs checking on real glasses (checklist above).
- **Head-direction axes** come from Meta's BirdSpotter sample measurements; confirm on our pair.
- **Compass accuracy**: phones are often 5–10° off near metal or cars. The pointing tolerance is ±12°.
- **Head-direction axes are uncertain.** Meta's BirdSpotter sample is inconsistent: its `GlassesAim.kt`
  says +Y up / −Z forward (what we use), its mock-device code says +X up / +Z forward. Real glasses decide.
- Simulated glasses send motion in *our* axis convention, so they prove the app's pipeline, not the axis choice.
