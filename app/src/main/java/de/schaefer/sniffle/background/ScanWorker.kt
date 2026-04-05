package de.schaefer.sniffle.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("sniffle_settings", Context.MODE_PRIVATE)
        val durationMs = prefs.getLong("scan_duration_ms", 60_000L)
        val bleScan = prefs.getBoolean("ble_scan", true)
        val classicScan = prefs.getBoolean("classic_scan", true)
        val showSummary = prefs.getBoolean("scan_summary", false)

        val intent = Intent(applicationContext, ScanService::class.java).apply {
            putExtra("duration_ms", durationMs)
            putExtra("ble", bleScan)
            putExtra("classic", classicScan)
            putExtra("summary", showSummary)
        }

        try {
            ContextCompat.startForegroundService(applicationContext, intent)
        } catch (_: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sniffle_bg_scan"

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<ScanWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
