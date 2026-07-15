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
  reconnects after reboot
- 📞 Calls on the cluster: caller name, missed calls, accept/reject from the
  bike's controls
- 💬 Mirrors all phone notifications (SMS & WhatsApp with dedicated cluster icons)
- 🗺 Built-in navigation with the Neshan SDK *(in progress)*

## Architecture

`protocol` (pure Kotlin, tested) → `ble` (connection manager) →
`service` (foreground) + `phone` / `notifications` → `ui` (Compose)

## Build

./gradlew :app:testDebugUnitTest
./gradlew assembleDebug

CI builds run on Codemagic for every push to `master`.

## Disclaimer

Community project, not affiliated with or endorsed by Bajaj Auto.
Use at your own risk.

## License

[MIT](LICENSE)
