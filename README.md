# Dominar App 🏍️

Unofficial Android companion app for the **Bajaj Dominar** instrument cluster — a
community-built replacement for the Bajaj Ride app. Kotlin + Jetpack Compose.

Talks to the bike's cluster over Bluetooth Low Energy: turn-by-turn navigation,
call and notification mirroring, and remote call/volume control from the bike's
switchgear.

## Features

- 🔌 Reverse-engineered BLE protocol — fully documented in
  [Bluetooth_Protocol_Docs.md](Bluetooth_Protocol_Docs.md), pure-Kotlin packet
  builders with unit tests
- 🔁 Robust connection layer: serialized write queue, auto-reconnect with
  exponential backoff
- 🛰 Foreground service keeps the cluster connected with the app in background,
  reconnects after reboot — with a **Stop & disconnect** action right on the
  notification so you can fully shut it down anytime
- 📞 Calls on the cluster: caller name, missed calls, accept/reject from the
  bike's controls
- 💬 Mirrors all phone notifications (SMS & WhatsApp with dedicated cluster icons)
- 🗺 Built-in **Neshan** navigation with a Google-Maps-style UI: place search,
  origin/destination picking (origin defaults to your GPS location), long-press
  to drop a pin, motorcycle routing with distance & ETA

## Neshan setup (required for maps & navigation)

The app is fully wired to [Neshan Platform](https://platform.neshan.org/api/) —
you only need to add your own credentials:

### 1. Map license (SDK key) — renders the map tiles

1. In the [Neshan developers panel](https://platform.neshan.org/) create an
   **Android SDK** key registered with:
   - package name: `com.dominar.ride`
   - the **SHA1** of the keystore that signs your APK
     (for debug builds: `keytool -list -v -keystore ~/.android/debug.keystore`
     with password `android`; for Codemagic builds use the SHA1 of the keystore
     Codemagic signs with)
2. Download the license file from the panel and place it in
   `app/src/main/res/raw/` (keep the file name the panel gives you).

### 2. Web API key — search, reverse-geocode & routing

1. Create a **Web service** (وب سرویس) key in the same panel.
2. Put it in `local.properties` (never committed to git):

   ```properties
   NESHAN_API_KEY=service.xxxxxxxxxxxxxxxx
   ```

   On CI (Codemagic) define an environment variable named `NESHAN_API_KEY`
   instead — the build picks it up automatically. Passing
   `-PNESHAN_API_KEY=...` to Gradle also works.

If the key is missing the app still builds and runs; the navigation screen
shows a banner explaining that search & routing are disabled.

## Architecture

`protocol` (pure Kotlin, tested) → `ble` (connection manager) →
`service` (foreground) + `phone` / `notifications` → `ui` (Compose) +
`navigation` (Neshan search & routing client)

## Build

./gradlew :app:testDebugUnitTest
./gradlew assembleDebug

CI builds run on Codemagic for every push to `master`.

## Disclaimer

Community project, not affiliated with or endorsed by Bajaj Auto.
Use at your own risk.

## License

[MIT](LICENSE)
