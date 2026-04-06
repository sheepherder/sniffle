package de.schaefer.sniffle.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sniffle_settings", Context.MODE_PRIVATE)

    var bleScan: Boolean
        get() = prefs.getBoolean(KEY_BLE_SCAN, true)
        set(v) = prefs.edit { putBoolean(KEY_BLE_SCAN, v) }

    var classicScan: Boolean
        get() = prefs.getBoolean(KEY_CLASSIC_SCAN, true)
        set(v) = prefs.edit { putBoolean(KEY_CLASSIC_SCAN, v) }

    var bgEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_ENABLED, false)
        set(v) = prefs.edit { putBoolean(KEY_BG_ENABLED, v) }

    var intervalMin: Long
        get() = prefs.getLong(KEY_INTERVAL_MIN, 30L)
        set(v) = prefs.edit { putLong(KEY_INTERVAL_MIN, v) }

    var scanDurationMs: Long
        get() = prefs.getLong(KEY_SCAN_DURATION_MS, 60_000L)
        set(v) = prefs.edit { putLong(KEY_SCAN_DURATION_MS, v) }

    var notifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(v) = prefs.edit { putBoolean(KEY_NOTIFICATIONS, v) }

    var scanSummary: Boolean
        get() = prefs.getBoolean(KEY_SCAN_SUMMARY, false)
        set(v) = prefs.edit { putBoolean(KEY_SCAN_SUMMARY, v) }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, v) }

    companion object {
        private const val KEY_BLE_SCAN = "ble_scan"
        private const val KEY_CLASSIC_SCAN = "classic_scan"
        private const val KEY_BG_ENABLED = "bg_enabled"
        private const val KEY_INTERVAL_MIN = "interval_min"
        private const val KEY_SCAN_DURATION_MS = "scan_duration_ms"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_SCAN_SUMMARY = "scan_summary"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
