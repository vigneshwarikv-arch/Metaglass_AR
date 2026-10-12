# [BrandName] — iPhone app (MVP 1: "What's that plane?")

SwiftUI app with Meta glasses support through the Meta Wearables Device Access Toolkit (DAT) 1.0.1
for iOS. Same features and answers as the Android app in `app/android`.

| Folder | What it is |
| --- | --- |
| `PlanesKit/` | The engine, as a Swift package: geometry, "which plane am I pointing at?", spoken answers, head-direction maths, OpenSky / adsbdb / sample data. A port of the Android `core` + `data` modules with the same tests. |
| `Planes/` | The app: screens (`UI/`), iPhone location + compass (`Sensors.swift`), Meta glasses (`Glasses/`). |
| `project.yml` | Xcode project definition (XcodeGen). The `.xcodeproj` is generated from it. |
| `Config/` | Build settings. Your own IDs and keys go in `Secrets.xcconfig` (git-ignored). |

## What you need

- A **Mac** with **Xcode 26.4 or newer** (Meta's SDK requires it).
- An **iPhone** on **iOS 17.2 or newer**.
- An **Apple ID**. A free one lets you install on your own iPhone (the app expires after 7 days; just
  run it from Xcode again). Sharing with others through TestFlight needs the paid Apple Developer
  Program ($99/year).
- For glasses: the **Meta AI** app on the iPhone with your glasses paired.

## Run it on your iPhone

```bash
brew install xcodegen          # once (needs Homebrew: https://brew.sh)
git clone https://github.com/vigneshwarikv-arch/Metaglass_AR.git
cd Metaglass_AR/app/ios
cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig
xcodegen generate
open Planes.xcodeproj
```

Then in Xcode:

1. Put your **Team ID** in `Config/Secrets.xcconfig` (`DEVELOPMENT_TEAM = ...`), or pick your team in
   **Planes target → Signing & Capabilities**. If the bundle ID `com.metaglassar.planes` is taken for a
   free account, change `PRODUCT_BUNDLE_IDENTIFIER` in `project.yml` (and in the Meta Developer Center).
2. Connect the iPhone by cable, select it at the top of Xcode, press **Run** (▶).
3. First time only, on the iPhone: **Settings → Privacy & Security → Developer Mode → On**
   (restarts the phone), then **Settings → General → VPN & Device Management → trust your Apple ID**.

Xcode downloads Meta's SDK automatically the first time (Swift Package Manager).

Run `xcodegen generate` again after pulling changes that add or remove files.

### Optional: OpenSky account

More live updates (every 10 s instead of 30 s): create a free API client at opensky-network.org and
put it in `Config/Secrets.xcconfig`:

```
OPENSKY_CLIENT_ID = ...
OPENSKY_CLIENT_SECRET = ...
```

Never commit `Secrets.xcconfig`.

## Meta glasses

1. In the **Meta AI** app: **Settings → your glasses → Developer Mode → On**.
2. In the [Wearables Developer Center](https://wearables.developer.meta.com) project, add the iOS bundle ID
   `com.metaglassar.planes`, URL scheme `metaglassar`, and turn on **Motion**, **Speech** and
   **Voice Invocations** (experimental: allowed for development and release-channel testing).
3. In the app: **Settings → Connect glasses** (Meta AI opens; approve), then **Allow glasses microphone**.
4. Test with the checklist in [`app/android/README.md`](../android/README.md#checklist-test-on-real-glasses);
   it applies to iPhone too.

**No glasses?** **Settings → Simulated glasses** runs the whole glasses flow with Meta's MockDeviceKit,
on the iPhone or in the Xcode Simulator.

## Tests

```bash
cd app/ios/PlanesKit && swift test
```

GitHub runs these tests and an iPhone build on every push (see the **Actions** tab), so a change that
breaks the build shows up even if you don't have a Mac at hand.

## Not verified yet (needs a real iPhone)

The code builds on GitHub's Mac, but nobody has run it on an iPhone yet. Please check especially:

- **Compass:** on the Point screen, point the iPhone at a known direction (use the Compass app) and
  compare. The maths uses gravity + the magnetometer, like the glasses.
- **Glasses audio:** does the spoken answer play in the glasses?
- **Head direction:** look level → about 0°; look up → positive (same as Android).
