#!/bin/env bash
pgrep -x scrcpy > /dev/null || scrcpy -w > /dev/null 2>&1 &
./gradlew assembleRelease || exit 1
adb install -r app/build/outputs/apk/release/sniffle-0.1.0-release.apk
adb shell am start -n de.schaefer.sniffle/.MainActivity
