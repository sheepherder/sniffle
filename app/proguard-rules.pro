# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Strip debug/verbose logs from release builds
-maximumremovedandroidloglevel DEBUG
