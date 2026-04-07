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
│   ├── App.kt                  # Application, NotificationChannels, Room DB init, isScanning-Flag, Worker-Scheduling
│   ├── MainActivity.kt         # Single Activity, Compose setContent
│   ├── ble/
│   │   ├── BleScanner.kt       # BluetoothLeScanner → Flow<ParsedAdvert>, Auto-Restart alle 25min
│   │   ├── ClassicScanner.kt   # BluetoothAdapter.startDiscovery → Flow<ClassicDevice>
│   │   ├── AdvertParser.kt     # ScanResult → ParsedAdvert (inkl. TheengsDecoder JSON)
│   │   ├── ScanProcessor.kt    # Zentrale Scan-Pipeline: Decode→Classify→Persist→Promote, DB-Throttle 30s
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
│   │   ├── AppDatabase.kt      # Room DB "sniffle.db", Version 4, destructive Migration
│   │   ├── DeviceEntity.kt     # PK=mac, category, transport, classicName, modelId, firstSeenMs, latestSeenMs, note, notified
│   │   ├── SightingEntity.kt   # FK=mac, timestamp, lat/lon, rssi, decodedValues (JSON), Cascade-Delete
│   │   └── DeviceDao.kt        # Queries inkl. countDistinctDays (localtime), deleteStaleOnce, observeLatestGeoSightings, observeAllGeoSightings
│   ├── background/
│   │   ├── ScanWorker.kt       # WorkManager PeriodicWork, scannt direkt (kein separater Service), überspringt wenn App aktiv
│   │   └── NotificationHelper.kt  # 3 Channels: devices, summary, service
│   └── ui/
│       ├── theme/Theme.kt      # Material3 Dynamic Colors, System Dark/Light
│       ├── navigation/NavGraph.kt  # 4 Tabs + Detail-Route, Onboarding-Gate
│       ├── onboarding/OnboardingScreen.kt  # 4-Step Permission Flow (deutsch)
│       ├── scan/
│       │   ├── LiveScreen.kt       # Echtzeit-Liste, 4 Kategorien, RSSI-Bars
│       │   └── LiveViewModel.kt    # Startet BLE/Classic-Scan, nutzt ScanProcessor, Location-Tracking
│       ├── history/
│       │   ├── HistoryScreen.kt    # Alle Funde, gleiche Kategorien
│       │   └── HistoryViewModel.kt
│       ├── detail/
│       │   ├── DetailScreen.kt     # Mini-Karte (klickbar→Fullscreen), Notiz, RSSI-Trend (Vico), Sichtungsliste, Löschen
│       │   ├── DetailMapScreen.kt  # Fullscreen-Karte für ein Gerät, alle Sightings, Alle/Letzte Toggle
│       │   └── DetailViewModel.kt  # Lädt Device + Sightings, Note-Persistenz
│       ├── map/
│       │   ├── ClusterMap.kt       # Shared Composable: RadiusMarkerClusterer, InfoWindows, Dot-Marker, FAB
│       │   └── MapScreen.kt        # Vollbild-Karte aller Geräte, Alle/Letzte Toggle, Clustering
│       └── settings/
│           └── SettingsScreen.kt   # BG-Scan Toggle, Intervall, Dauer, BLE/BT, Notifications, Batterie-Hinweis, Scan-Summary
├── assets/
│   └── oui.csv                 # 38962 OUI-Einträge (MAC→Hersteller)
└── res/
    ├── drawable/                # Adaptive Icon Foreground/Background (XML)
    ├── mipmap-anydpi-v26/      # Adaptive Icon Launcher
    └── values/strings.xml      # Deutsche Strings
```

## Dependencies (Kurzform)

- Jetpack Compose + Material3 (Dynamic Colors)
- Room (SQLite, KSP)
- WorkManager (Hintergrund-Scan)
- Google Play Services Location (GPS)
- osmdroid + osmbonuspack (OpenStreetMap Karte, Marker-Clustering)
- Vico (Charts — RSSI-Trend im DetailScreen)
- kotlinx-serialization-json
- NDK 27 + CMake 3.22 (für TheengsDecoder C++)

## Bekannte offene Punkte

- App wurde noch nie auf einem echten Gerät getestet
- Sensor-Werte-Labels kommen noch roh aus dem Decoder (z.B. "tempc" statt "Temperatur")
- Kein Export (CSV/JSON) implementiert
- Die Deprecation-Warning in ClassicScanner.kt (getParcelableExtra) ist harmlos aber sollte irgendwann gefixt werden
- SettingsScreen: Chips für Intervall/Dauer könnten auf kleinen Screens umbrechen — Layout evtl. anpassen
- ClusterMap: Fittet initial auf alle Punkte (Marker + eigene Location). Ohne Punkte: Karte zeigt Weltansicht
- Onboarding: Background Location Permission wird auf manchen Android-Versionen nicht direkt im Dialog angeboten, User muss manuell in Einstellungen gehen

## Entscheidungen & Kontext

- **Kein MQTT, kein Home Assistant** — reiner Scanner, wird nie ein Gateway
- **TheengsDecoder via JNI** statt Kotlin-Reimplementation — weil die C++-Library 120+ Geräte unterstützt
- **BTHome zusätzlich nativ** — weil offener Standard, einfach zu parsen, und Theengs kennt nicht alle BTHome-Geräte
- **Room statt Realm/ObjectBox** — Standard, gut genug für 500k rows/year
- **osmdroid + osmbonuspack statt Google Maps** — kein API-Key nötig, kostenlos, OpenStreetMap, RadiusMarkerClusterer
- **WorkManager direkt (kein separater Service)** — Worker scannt selbst mit ForegroundInfo, überspringt wenn App aktiv (isScanning-Flag)
- **Alle Geräte speichern** (auch namenlose Random-MACs) — weil sie sich als stabil herausstellen könnten (→ Mystery)
- **3-Tage-Schwelle für Promotion** (nicht 2) — um zufällige Doppelsichtungen zu filtern
- **Notifications nur 1x pro Gerät** — nie wieder, Flag in DB
- **BLE-Scan Restart alle 25min** — Android drosselt nach 30min auf opportunistic, daher proaktiver Neustart
- **DB-Throttle 30s pro Gerät** — ScanProcessor schreibt max alle 30s in Room, Live-UI aus In-Memory-State
- **ScanProcessor als zentrale Pipeline** — wird sowohl von LiveViewModel als auch ScanWorker genutzt
- **countDistinctDays mit localtime** — Tages-Zählung für Promotion basiert auf Geräte-Zeitzone, nicht UTC
- **Alle Timestamps als Epoch-Long (ms)** — kein Text-Datumsformat in der DB, Anzeige über Formatting.kt
- **Shared ClusterMap Composable** — wird von MapScreen, DetailScreen (MiniMap) und DetailMapScreen (Fullscreen) genutzt
- **ClusterMap: LaunchedEffect statt AndroidView update** — update-Lambda wird bei jeder Recomposition aufgerufen und zerstört Tiles. Marker-Updates daher via LaunchedEffect(markers, myLocation, mapView)
- **osmdroid zoomToBoundingBox erst nach Layout** — ohne map.width/height > 0 geht Projection.getCloserPixel in Endlosschleife (ANR). Retry-Loop mit 50ms Intervall statt blindem Delay
- **destructive DB-Migration** — solange App nicht produktiv, bei Schema-Änderung Version bumpen reicht
