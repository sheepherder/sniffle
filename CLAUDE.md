# Sniffle — BLE Scanner Android App

## Übersicht

Android-App (Kotlin/Jetpack Compose) die BLE-Advertisements und Classic Bluetooth passiv scannt, Geräte automatisch dekodiert und klassifiziert, auf einer Karte anzeigt, und im Hintergrund periodisch scannt mit Notifications.

## Build

```bash
# Voraussetzungen: Android SDK, NDK 27.0.12077973, CMake 3.22.1
# SDK unter /opt/android-sdk (local.properties)
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Git-Submodules nach frischem Clone initialisieren:
```bash
git submodule update --init --recursive
```

## Architektur

```
Scan (BLE + Classic BT)
  → AdvertParser (ScanResult → ParsedAdvert)
  → DecoderChain (Theengs → BTHome → Ruuvi → Eddystone → iBeacon → Fallback)
  → DeviceClassifier (SENSOR / DEVICE / MYSTERY / ONCE)
  → Room DB (DeviceEntity + SightingEntity)
  → UI (LiveScreen / HistoryScreen / DetailScreen / MapScreen / SettingsScreen)
```

## Decoder-Kaskade

1. **TheengsDecoder** (C++/JNI) — 120+ Geräte (Aranet, Govee, Xiaomi, Switchbot, Oral-B, ...)
2. **BTHome** (Kotlin) — v1/v2, ~60 Objekttypen (Shelly BLU, PVVX/ATC, DIY-ESP32)
3. **Ruuvi** (Kotlin) — RAWv2 Format 5
4. **Eddystone** (Kotlin) — UID, URL, TLM
5. **iBeacon** (Kotlin) — UUID/Major/Minor
6. **Fallback** — OUI-Lookup (39k Einträge), BLE Appearance, Name-Patterns, Service-UUIDs

## 4 Kategorien mit Promotion

- **📡 Sensoren**: Sofort bei Erstfund wenn Messdaten vorhanden
- **📱 Geräte**: Promotion aus Flüchtige nach 3 Tagen + Identität (Name/OUI/Appearance)
- **👻 Mystery**: Promotion aus Flüchtige nach 3 Tagen OHNE Identität
- **💨 Flüchtige**: Alles andere. Auto-Cleanup nach 90 Tagen.

## Projekt-Struktur

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt          # Baut TheengsDecoder static + JNI bridge shared
│   ├── jni_bridge.cpp          # 3 JNI-Funktionen: decodeBLE, getProperties, getAttribute
│   └── decoder/                # Git submodule: theengs/decoder (inkl. ArduinoJson)
├── java/de/schaefer/sniffle/
│   ├── App.kt                  # Application, NotificationChannels, Room DB init
│   ├── MainActivity.kt         # Single Activity, Compose setContent
│   ├── ble/
│   │   ├── BleScanner.kt       # BluetoothLeScanner → Flow<ParsedAdvert>
│   │   ├── ClassicScanner.kt   # BluetoothAdapter.startDiscovery → Flow<ClassicDevice>
│   │   ├── AdvertParser.kt     # ScanResult → ParsedAdvert (inkl. TheengsDecoder JSON)
│   │   └── TheengsDecoder.kt   # JNI-Wrapper (object, loadLibrary "sniffle")
│   ├── decoder/
│   │   ├── DecoderChain.kt     # Interface Decoder, DecoderChain, TheengsDecoderAdapter
│   │   ├── BtHomeDecoder.kt    # BTHome v1/v2, ~60 Object IDs
│   │   ├── RuuviDecoder.kt     # Ruuvi RAWv2 Format 5
│   │   ├── EddystoneDecoder.kt # UID, URL, TLM frames
│   │   └── IBeaconDecoder.kt   # Apple 0x004C + 02 15 prefix
│   ├── classify/
│   │   ├── DeviceClassifier.kt # Kategorie-Bestimmung, Promotion-Check, Name-Patterns
│   │   ├── OuiLookup.kt        # MAC OUI → Hersteller (aus assets/oui.csv)
│   │   ├── ServiceUuidResolver.kt  # BLE Service UUID → Beschreibung
│   │   └── AppearanceResolver.kt   # BLE Appearance Code → Label (deutsch)
│   ├── data/
│   │   ├── AppDatabase.kt      # Room DB "sniffle.db"
│   │   ├── DeviceEntity.kt     # PK=mac, category, transport, note, notified
│   │   ├── SightingEntity.kt   # FK=mac, timestamp, lat/lon, rssi, decodedValues (JSON)
│   │   └── DeviceDao.kt        # Queries inkl. countDistinctDays, deleteStaleOnce
│   ├── background/
│   │   ├── ScanWorker.kt       # WorkManager PeriodicWork → startet ScanService
│   │   ├── ScanService.kt      # ForegroundService: BLE+BT scan, GPS, persist, notify
│   │   └── NotificationHelper.kt  # 3 Channels: devices, summary, service
│   └── ui/
│       ├── theme/Theme.kt      # Material3 Dynamic Colors, System Dark/Light
│       ├── navigation/NavGraph.kt  # 4 Tabs + Detail-Route, Onboarding-Gate
│       ├── onboarding/OnboardingScreen.kt  # 4-Step Permission Flow (deutsch)
│       ├── scan/
│       │   ├── LiveScreen.kt       # Echtzeit-Liste, 4 Kategorien, RSSI-Bars
│       │   └── LiveViewModel.kt    # Scan→Decode→Classify→Persist→Promote Pipeline
│       ├── history/
│       │   ├── HistoryScreen.kt    # Alle Funde, gleiche Kategorien
│       │   └── HistoryViewModel.kt
│       ├── detail/
│       │   ├── DetailScreen.kt     # Mini-Karte, Notiz, Werte-Verlauf, Sichtungen, Löschen
│       │   └── DetailViewModel.kt
│       ├── map/
│       │   ├── MapScreen.kt        # osmdroid Vollbild-Karte mit Markern
│       │   └── MapViewModel.kt
│       └── settings/
│           └── SettingsScreen.kt   # BG-Scan Toggle, Intervall, Dauer, BLE/BT, Notifications
├── assets/
│   └── oui.csv                 # 38962 OUI-Einträge (MAC→Hersteller)
└── res/
    └── values/strings.xml      # Deutsche Strings
```

## Dependencies (Kurzform)

- Jetpack Compose + Material3 (Dynamic Colors)
- Room (SQLite, KSP)
- WorkManager (Hintergrund-Scan)
- Google Play Services Location (GPS)
- osmdroid (OpenStreetMap Karte)
- Vico (Charts — eingebunden aber noch nicht voll genutzt, aktuell Text-basierter Verlauf)
- kotlinx-serialization-json
- NDK 27 + CMake 3.22 (für TheengsDecoder C++)

## Bekannte offene Punkte

- App wurde noch nie auf einem echten Gerät getestet
- Vico-Charts sind eingebunden aber DetailScreen nutzt noch Text-basierten Verlauf statt echte Graphen
- Sensor-Werte-Labels kommen noch roh aus dem Decoder (z.B. "tempc" statt "Temperatur")
- Kein App-Icon gesetzt (nutzt Android-Default)
- Kein Export (CSV/JSON) implementiert
- Die Deprecation-Warning in ClassicScanner.kt (getParcelableExtra) ist harmlos aber sollte irgendwann gefixt werden
- SettingsScreen: Chips für Intervall/Dauer könnten auf kleinen Screens umbrechen — Layout evtl. anpassen
- MapScreen: Center ist hardcoded auf Mitteleuropa (48.2, 11.8) — sollte auf letzte bekannte Position setzen
- Onboarding: Background Location Permission wird auf manchen Android-Versionen nicht direkt im Dialog angeboten, User muss manuell in Einstellungen gehen

## Entscheidungen & Kontext

- **Kein MQTT, kein Home Assistant** — reiner Scanner, wird nie ein Gateway
- **TheengsDecoder via JNI** statt Kotlin-Reimplementation — weil die C++-Library 120+ Geräte unterstützt
- **BTHome zusätzlich nativ** — weil offener Standard, einfach zu parsen, und Theengs kennt nicht alle BTHome-Geräte
- **Room statt Realm/ObjectBox** — Standard, gut genug für 500k rows/year
- **osmdroid statt Google Maps** — kein API-Key nötig, kostenlos, OpenStreetMap
- **WorkManager + ForegroundService** — zuverlässigster Ansatz für periodische BLE-Scans im Hintergrund auf Android 12+
- **Alle Geräte speichern** (auch namenlose Random-MACs) — weil sie sich als stabil herausstellen könnten (→ Mystery)
- **3-Tage-Schwelle für Promotion** (nicht 2) — um zufällige Doppelsichtungen zu filtern
- **Notifications nur 1x pro Gerät** — nie wieder, Flag in DB
