package de.schaefer.sniffle.background

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ServiceInfo
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.deleteStale
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.FastPairLookup
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.util.Preferences
import de.schaefer.sniffle.util.formatLiveStatus
import de.schaefer.sniffle.util.formatScanSummary
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val prefs = Preferences(applicationContext)
        val durationMs = prefs.scanDurationMs
        val bleScan = prefs.bleScan
        val classicScan = prefs.classicScan
        val showSummary = prefs.scanSummary

        // Show foreground notification
        setForeground(ForegroundInfo(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.serviceNotification(applicationContext),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        ))

        val app = applicationContext as App
        val dao = app.database.deviceDao()
        val coordinator = app.scanCoordinator
        OuiLookup.init(applicationContext)
        FastPairLookup.init(applicationContext)

        val loc = withTimeoutOrNull(3_000L) { getLocation() }
        val notifications = prefs.notifications
        val processor = ScanProcessor(
            dao, loc?.latitude, loc?.longitude, persistIntervalMs = 0,
            onNotify = if (notifications) {{ device, values ->
                NotificationHelper.notifyDevice(applicationContext, device, values)
            }} else null,
        )

        Log.i("ScanWorker", "Starting scan: ble=$bleScan classic=$classicScan duration=${durationMs}ms")

        // Single-threaded dispatcher keeps ScanProcessor maps accessed serially
        val processingDispatcher = Dispatchers.IO.limitedParallelism(1)
        val scope = CoroutineScope(processingDispatcher + SupervisorJob())

        // Collecting from the shared coordinator flows keeps the upstream BLE
        // scan alive for the duration of this Worker; when scope is cancelled,
        // the WhileSubscribed() refcount drops and upstream stops automatically.
        if (bleScan && coordinator.bleAvailable) {
            scope.launch { coordinator.bleResults.collect { processor.processBle(it) } }
        }
        if (classicScan && coordinator.classicAvailable) {
            scope.launch { coordinator.classicResults.collect { processor.processClassic(it) } }
        }

        // Live-update the FGS notification with current counts (skip when unchanged)
        val nm = NotificationManagerCompat.from(applicationContext)
        scope.launch {
            var lastText = ""
            while (true) {
                delay(500)
                val text = formatLiveStatus(
                    processor.signalCount.get(), processor.sightedCount,
                    processor.newDeviceCount.get(), processor.newSensorCount.get(),
                )
                if (text == lastText) continue
                lastText = text
                try {
                    nm.notify(
                        NotificationHelper.SERVICE_NOTIFICATION_ID,
                        NotificationHelper.serviceNotification(applicationContext, text),
                    )
                } catch (_: SecurityException) {}
            }
        }

        try {
            delay(durationMs)
        } finally {
            scope.cancel() // cancels liveUpdateJob, bleConsumer, classicConsumer (all children)
        }

        dao.deleteStale()

        val summary = formatScanSummary(
            signals = processor.signalCount.get(),
            sighted = processor.sightedCount,
            newDevices = processor.newDeviceCount.get(),
            newSensors = processor.newSensorCount.get(),
            promotions = processor.promotedCount.get(),
        )
        Log.i("ScanWorker", "Scan done: $summary")
        prefs.lastBgScanMs = System.currentTimeMillis()
        prefs.lastBgScanSummary = summary

        if (showSummary) {
            NotificationHelper.notifyScanSummary(applicationContext, summary)
        }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        try {
            LocationServices.getFusedLocationProviderClient(applicationContext)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: Exception) {
            cont.resume(null)
        }
    }

    companion object {
        private const val WORK_NAME = "sniffle_bg_scan"

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<ScanWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
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
