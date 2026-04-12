# Sniffle — Passive BLE & Bluetooth Scanner for Android

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-brightgreen.svg)](https://developer.android.com/about/versions/13)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)

Sniffle is an Android app that passively scans for BLE advertisements and Classic Bluetooth devices, automatically decodes and classifies them, and shows their historical locations on a map.

**Privacy-first:** no cloud, no accounts, no API keys, no telemetry. Everything stays on your device.

## Features

- **Passive scanning** — BLE advertisements and Classic Bluetooth discovery, no pairing, no connections
- **10-stage decoder cascade** — automatically identifies 120+ device types (sensors, trackers, headphones, smartwatches, …)
- **4 device categories** with automatic promotion from transient to known:
  - 📡 **Sensors** — anything with measurement data (temperature, humidity, CO₂, battery, …). Promoted immediately on first sighting with sensor data.
  - 📱 **Devices** — repeatedly observed with a stable identity (name, manufacturer, appearance). Promoted after 4 sightings ≥1h apart.
  - 👻 **Mystery** — repeatedly observed but unidentifiable (randomized MACs, no name). Same 4-sighting rule.
  - 💨 **Transient** — everything else, auto-cleaned after 90 days.
- **OpenStreetMap integration** — location history per device, marker clustering, priority-based coloring
- **Sensor charts** — time-series charts with shared zoom/scroll (inline + fullscreen landscape)
- **Background scanning** via WorkManager, foreground service with unobtrusive notification while scanning
- **"Hide on map"** toggle — exclude devices you carry with you so the map stays meaningful
- **Notifications** once per device: new sensor found, or transient promoted
- **Material You** — dynamic colors, dark/light theme

## Privacy & Data Handling

- **Scan data never leaves the phone.** Devices, sightings, locations — all stored locally in a SQLite database.
- The only outbound traffic is **OpenStreetMap tile requests** (only while the map is visible) and Play Services Location talking to the GPS.
- All other lookups (OUI vendor database, Fast Pair model IDs) are bundled at build time.
- No analytics, no crash reporting, no account system.

## Permissions

| Permission | Why |
|------------|-----|
| `BLUETOOTH_SCAN` | Passive BLE advertisement scanning |
| `BLUETOOTH_CONNECT` | Reading Classic Bluetooth device name/class |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Tagging sightings with GPS coordinates for the map |
| `ACCESS_BACKGROUND_LOCATION` | Allows the scheduled background scan to record GPS while the app is not open |
| `POST_NOTIFICATIONS` | Scan summary + new-device alerts |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_LOCATION` | Required by Android for background BLE/location access |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional; lets the user whitelist the app from Doze so periodic scans run reliably |
| `INTERNET` | Loading OpenStreetMap map tiles only — no scan data is ever uploaded |

The app never *connects* to any Bluetooth device — scanning is passive only.

## Install

Source-only for now. Build instructions below. Releases will be published to the GitHub [Releases](https://github.com/sheepherder/sniffle/releases) page once the app stabilizes.

## Build

Requirements: Android SDK (compileSdk 36), NDK 29.0.14206865, CMake 3.31.6, Java 21.

```bash
# Clone with submodules (TheengsDecoder C++ library)
git clone --recurse-submodules https://github.com/sheepherder/sniffle.git
cd sniffle

# Or initialize submodules after cloning
git submodule update --init --recursive

# Build debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/sniffle-<version>-debug.apk
```

Set `sdk.dir` in `local.properties` if your Android SDK is not in a standard location:
```properties
sdk.dir=/path/to/your/android-sdk
```

## Architecture

```
Scan (BLE + Classic BT)
  → AdvertParser     (ScanResult → ParsedAdvert)
  → DecoderChain     (Theengs → BTHome → Ruuvi → Eddystone → iBeacon → … → Fallback)
  → DeviceClassifier (SENSOR / DEVICE / MYSTERY / TRANSIENT)
  → Room DB          (DeviceEntity + SightingEntity)
  → UI               (Scan / History / Detail / Map / Settings)
```

### Decoder Cascade

Each BLE advertisement is run through these decoders in order until one matches:

1. **TheengsDecoder** (C++/JNI) — 120+ devices (Aranet, Govee, Xiaomi, Switchbot, Oral-B, …)
2. **BTHome** — v1/v2, ~60 object types (Shelly BLU, PVVX/ATC, DIY ESP32)
3. **Ruuvi** — RAWv2 Format 5
4. **Eddystone** — UID, URL, TLM
5. **iBeacon** — UUID/Major/Minor
6. **Apple Continuity** — Proximity Pairing (AirPods), Nearby Info (iPhone), Find My (AirTags)
7. **Google Fast Pair** — Model ID lookup (475+ devices)
8. **Google FMDN** — Find My Device Network trackers
9. **Microsoft CDP** — Connected Devices Platform (Xbox, Windows, Surface)
10. **Fallback** — OUI lookup (39k entries), BLE Appearance, name patterns, Service UUIDs

### Stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 (Dynamic Colors) |
| Database | Room (SQLite, KSP) |
| Background | WorkManager |
| Location | Google Play Services Location |
| Maps | osmdroid + osmbonuspack |
| Charts | Vico 3.1 |
| Serialization | kotlinx-serialization-json |
| Native | NDK 29 + CMake 3.31 (TheengsDecoder C++) |

Key design choices:
- **TheengsDecoder via JNI** — reuses the mature C++ library instead of reimplementing 120+ decoders in Kotlin
- **BTHome additionally as native Kotlin** — open standard, easy to parse, and Theengs doesn't cover all BTHome devices
- **osmdroid instead of Google Maps** — no API key required, free, OpenStreetMap tiles
- **WorkManager for background scanning** — the worker scans directly with ForegroundInfo, skips when the app is in the foreground
- **Destructive DB migrations** — while the app is pre-release, schema bumps drop the old data

## Asset Data Sources

- **oui.csv** — MAC address vendor lookup from the [IEEE MA-L public registry](https://standards-oui.ieee.org/)
- **fastpair_models.json** — Google Fast Pair model IDs compiled from [Flipper Zero](https://github.com/flipperdevices/flipperzero-firmware) and [PentHertz](https://github.com/nicpmusic/bt_ids) open-source databases

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE) for details.

GPL v3 is required because [TheengsDecoder](https://github.com/theengs/decoder) (the C++ BLE decoder library linked via JNI) is GPL-3.0-or-later.

See [THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES) for a full list of dependencies and their licenses.

### Note on Google Play Services

This app uses `play-services-location` for GPS. Google Play Services is proprietary but comes pre-installed on Android devices. Under GPL v3 Section 1, it qualifies as a "System Library" exemption as a standard component of the platform on which the app runs.

## Contributing

Contributions welcome — please open an issue first to discuss what you'd like to change.

The app UI is currently **German only**. Translations are appreciated.
