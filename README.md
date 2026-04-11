# Sniffle — Passive BLE & Bluetooth Scanner for Android

Sniffle is an Android app that passively scans for BLE advertisements and Classic Bluetooth devices, automatically decodes and classifies them, shows them on a map, and runs periodic background scans with notifications.

Built with Kotlin and Jetpack Compose. No cloud, no accounts, no API keys — everything stays on your device.

## Features

- **Passive BLE & Classic Bluetooth scanning** — no pairing, no connections
- **10-stage decoder cascade** — automatically identifies 120+ device types (sensors, trackers, headphones, smartwatches, ...)
- **4 device categories** with automatic promotion:
  - **Sensors** — devices with measurement data (temperature, humidity, CO2, ...)
  - **Devices** — repeatedly seen devices with identity (name, manufacturer, ...)
  - **Mystery** — repeatedly seen but unidentifiable (randomized MACs, no name)
  - **Transient** — everything else, auto-cleaned after 90 days
- **OpenStreetMap integration** — all sightings on a map with marker clustering
- **Sensor charts** — time-series charts for sensor values (inline + fullscreen landscape)
- **Background scanning** via WorkManager with configurable interval and duration
- **Notifications** for newly promoted devices
- **Material You** — dynamic colors, dark/light theme

## Decoder Cascade

Each BLE advertisement is run through these decoders in order until one matches:

1. **TheengsDecoder** (C++/JNI) — 120+ devices (Aranet, Govee, Xiaomi, Switchbot, Oral-B, ...)
2. **BTHome** — v1/v2, ~60 object types (Shelly BLU, PVVX/ATC, DIY ESP32)
3. **Ruuvi** — RAWv2 Format 5
4. **Eddystone** — UID, URL, TLM
5. **iBeacon** — UUID/Major/Minor
6. **Apple Continuity** — Proximity Pairing (AirPods), Nearby Info (iPhone), Find My (AirTags)
7. **Google Fast Pair** — Model ID lookup (475+ devices)
8. **Google FMDN** — Find My Device Network trackers
9. **Microsoft CDP** — Connected Devices Platform (Xbox, Windows, Surface)
10. **Fallback** — OUI lookup (39k entries), BLE Appearance, name patterns, Service UUIDs

## Requirements

- Android SDK (compileSdk 36)
- NDK 29.0.14206865
- CMake 3.31.6
- Java 21 (JVM toolchain)

The app targets **Android 13+** (minSdk 33).

## Build

```bash
# Clone with submodules (TheengsDecoder C++ library)
git clone --recurse-submodules https://github.com/schaefer/sniffle.git
cd sniffle

# Or initialize submodules after cloning
git submodule update --init --recursive

# Build debug APK
./gradlew assembleDebug

# APK output: app/build/outputs/apk/debug/app-debug.apk
```

Set `sdk.dir` in `local.properties` if your Android SDK is not in a standard location:
```properties
sdk.dir=/path/to/your/android-sdk
```

## Architecture

```
Scan (BLE + Classic BT)
  -> AdvertParser (ScanResult -> ParsedAdvert)
  -> DecoderChain (Theengs -> BTHome -> Ruuvi -> Eddystone -> iBeacon -> Fallback)
  -> DeviceClassifier (SENSOR / DEVICE / MYSTERY / TRANSIENT)
  -> Room DB (DeviceEntity + SightingEntity)
  -> UI (ScanScreen / HistoryScreen / DetailScreen / MapScreen / SettingsScreen)
```

Key design decisions:
- **TheengsDecoder via JNI** — reuses the C++ library (120+ devices) instead of reimplementing in Kotlin
- **BTHome in addition to Theengs** — open standard, easy to parse, and Theengs doesn't cover all BTHome devices
- **osmdroid instead of Google Maps** — no API key required, free, OpenStreetMap tiles
- **Room for persistence** — standard, good enough for ~500k rows/year
- **WorkManager for background scanning** — worker scans directly with ForegroundInfo, skips when the app is active

## Tech Stack

- **UI**: Jetpack Compose + Material 3 (Dynamic Colors)
- **Database**: Room (SQLite, KSP)
- **Background**: WorkManager
- **Location**: Google Play Services Location
- **Maps**: osmdroid + osmbonuspack (OpenStreetMap, marker clustering)
- **Charts**: Vico 3.1
- **Serialization**: kotlinx-serialization-json
- **Native**: NDK 29 + CMake 3.31 (TheengsDecoder C++)

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for details.

GPL v3 is required because [TheengsDecoder](https://github.com/theengs/decoder) (the C++ BLE decoder library linked via JNI) is GPL-3.0-or-later.

See [THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES) for a full list of dependencies and their licenses.

### Note on Google Play Services

This app uses `play-services-location` for GPS. Google Play Services is proprietary but comes pre-installed on Android devices. Under GPL v3 Section 1, it qualifies as a "System Library" exemption since it is a standard component of the platform on which the app runs.

## Asset Data Sources

- **oui.csv** — MAC address vendor lookup from the [IEEE MA-L public registry](https://standards-oui.ieee.org/)
- **fastpair_models.json** — Google Fast Pair model IDs compiled from [Flipper Zero](https://github.com/flipperdevices/flipperzero-firmware) and [PentHertz](https://github.com/nicpmusic/bt_ids) open-source databases

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

The app UI is currently in German only. Translations are appreciated.
