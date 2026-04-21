# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep app components referenced in manifest
-keep class de.schaefer.sniffle.MainActivity { *; }

# Keep BLE callback classes (accessed via reflection by Android framework)
-keep class * extends android.bluetooth.le.ScanCallback { *; }

# Keep Google Play Services Location
-keep class com.google.android.gms.location.** { *; }

# Strip debug/verbose logs from release builds
-maximumremovedandroidloglevel DEBUG
