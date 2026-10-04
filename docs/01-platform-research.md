# 01 — Platform Research: Building Apps for Meta AI Glasses

*Researched: 3 October 2026. Meta's platform is changing fast — re-check before building.*

## Summary

Yes, third-party apps can be built for Meta's glasses, and it officially opened at Meta Connect (September 2026). It does **not** work like a Play Store app installed on the glasses. In most cases **the phone app does the work**, and the glasses act as its camera, microphone, speaker and (on Display models) screen.

## Three ways to build

| Path | What it is | Glasses supported | Language |
| --- | --- | --- | --- |
| **Wearables Device Access Toolkit (DAT) 1.0** | SDK added to an existing iOS/Android app. App keeps its logic and backend; glasses become a hands-free front end. Launch with "Hey Meta", voice commands via speech recognition, swipe on Meta Neural Band, head-motion streaming, camera capture. | Ray-Ban Meta, Oakley Meta, Meta Ray-Ban Display | Swift (iOS), Kotlin (Android); community Flutter plugin exists |
| **Web apps** | HTML/CSS/JS running directly on the glasses, no native app needed. Can use device position, location, Neural Band wrist motion. UI Toolkit + browser simulator available. WebMCP (preview) lets Meta AI call functions in the app. | Meta Ray-Ban Display only | HTML, CSS, JavaScript |
| **Meta AI connectors** | Expose an API/MCP so Meta AI can call your service by voice. No app to ship. | All AI glasses (and Meta AI app/web) | Any backend |

**Display UI components available (DAT):** text, images, lists, buttons, video playback.

## How it compares to normal Android/iOS apps

- **Coding:** same tools — Swift/Kotlin, Xcode/Android Studio.
- **Distribution:** the phone app still ships through Google Play / Apple App Store; the glasses part rides inside it.
- **Testing:** release channels with up to 100 testers; a mock device simulates permissions and video streaming without hardware.
- **iOS caveat (verify):** a third-party guide reported App Store rejections for DAT apps because of Apple's ExternalAccessory/MFi framework. That report predates DAT 1.0.

## Where users find apps

- No full app store yet.
- Meta announced two discovery surfaces "coming soon": **Explore** (on-glasses app on Ray-Ban Display) and a curated list in the **Meta AI app**. Submissions require a Wearables Developer Center account.
- Community catalogs: MetaGlassApps, GlassApps (Display web apps).
- Known integrations: Spotify, Audible, Twitch (livestreaming), Disney (park guide demo).

## Monetization

Meta has **not** announced a paid glasses store or revenue share. Revenue comes through our own channel:
- Phone app subscription / in-app purchase (Play Store / App Store billing), or
- Own payments for a web app.

## Hardware and country availability

| Device | Price | Where sold |
| --- | --- | --- |
| Meta Ray-Ban Display + Neural Band | ~$799 / £749 / €899 | US, Canada, UK; France, Italy, Germany from 13 Oct 2026 |
| Ray-Ban Meta Gen 2 (no display) | from ₹39,900 in India | Many countries incl. India |

- India is **not** on Meta's list of AI-glasses supported countries. The SDK can be downloaded anywhere, but full toolkit capabilities, including the Developer Center, are limited to supported countries → **the US partner should own the developer account and do device testing.**

## AI-assisted development

- Meta's official repos ship a **Claude Code plugin** (`mwdat-ios`, `mwdat-android`) and a documentation **MCP server** (`https://mcp.developer.meta.com/wearables`).
- The web-app starter kit is set up for Claude Code, Cursor, Copilot.

## Key links

- Developer docs: https://wearables.developer.meta.com/docs/develop/
- Wearables Developer Center: https://developers.meta.com/wearables/
- DAT iOS: https://github.com/facebook/meta-wearables-dat-ios
- DAT Android: https://github.com/facebook/meta-wearables-dat-android
- Display UI Toolkit (web): https://github.com/facebook/meta-ray-ban-display-ui-toolkit-web
- Connect 2026 recap: https://developers.meta.com/blog/meta-connect-recap-ai-glasses/
- Display build guide: https://developers.meta.com/blog/build-for-display-glasses/
- Supported countries: https://www.meta.com/help/ai-glasses/4961066940605960/
