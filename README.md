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
- 🗺 Built-in **Neshan** navigation (new MapLibre-based SDK) with a
  Google-Maps-style UI: place search, origin/destination picking (origin
  defaults to your GPS location), long-press to drop a pin, motorcycle routing
  with distance & ETA

## Neshan setup (required for maps & navigation)

The app uses the new [MapLibre-based Neshan Android SDK](https://platform.neshan.org/docs/sdk/android/installation/)
— **no license file needed**, just two API keys from the
[Neshan developers panel](https://platform.neshan.org/panel):

### 1. SDK key — renders the map tiles

1. Create an **Android SDK** key registered with:
   - package name: `com.dominar.ride`
   - the **SHA1** of the keystore that signs your APK
2. Enable the **MapLibre mobile tile services** for the key
   (تایل نقشه / poi / ترافیک موبایل — کیت توسعه maplibre).
3. Put it in `local.properties`:

   ```properties
   NESHAN_SDK_KEY=your.sdk.key
   ```

   It is injected into the manifest as `org.maplibre.android.API_KEY` at build
   time, so it is never committed to git.

### 2. Web API key — search, reverse-geocode & routing

1. Create a **Web service** (وب سرویس) key in the same panel.
2. Add it to `local.properties`:

   ```properties
   NESHAN_API_KEY=service.xxxxxxxxxxxxxxxx
   ```

Both keys resolve in this order: `local.properties` > Gradle property
(`-PNESHAN_SDK_KEY=...`) > environment variable. On CI (Codemagic) define
`NESHAN_SDK_KEY` and `NESHAN_API_KEY` as environment variables.

If a key is missing the app still builds and runs; the navigation screen shows
a banner explaining what to configure.

> **Signing note:** the SDK key is validated against the signing certificate's
> SHA1. CI machines generate a fresh debug keystore per build, so commit a
> fixed debug keystore (e.g. `keystore/debug.keystore`) and wire it into
> `signingConfigs` to keep the SHA1 stable across local and CI builds.

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
