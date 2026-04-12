package de.schaefer.sniffle.background

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.deleteStale
import de.schaefer.sniffle.ble.AdvertParser
import de.schaefer.sniffle.ble.ClassicDevice
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.FastPairLookup
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.util.Preferences
import de.schaefer.sniffle.util.formatLiveStatus
import de.schaefer.sniffle.util.formatScanSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        // Skip if app is already scanning in foreground
        if ((applicationContext as App).isScanning) {
            Log.i("ScanWorker", "Skipping — app is in foreground")
            return Result.success()
        }

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

        val dao = (applicationContext as App).database.deviceDao()
        OuiLookup.init(applicationContext)
        FastPairLookup.init(applicationContext)

        // Get GPS with timeout
        val loc = withTimeoutOrNull(3_000L) { getLocation() }
        val notifications = prefs.notifications
        val processor = ScanProcessor(
            dao, loc?.latitude, loc?.longitude, persistIntervalMs = 0,
            onNotify = if (notifications) {{ device, values ->
                NotificationHelper.notifyDevice(applicationContext, device, values)
            }} else null,
        )

        Log.i("ScanWorker", "Starting scan: ble=$bleScan classic=$classicScan duration=${durationMs}ms")

        val bleChannel = Channel<ScanResult>(Channel.UNLIMITED)
        val classicChannel = Channel<ClassicDevice>(Channel.UNLIMITED)

        // Single-threaded dispatcher so ScanProcessor maps are accessed serially
        val processingDispatcher = Dispatchers.IO.limitedParallelism(1)
        val scope = CoroutineScope(processingDispatcher + SupervisorJob())
        val bleConsumer = scope.launch {
            for (result in bleChannel) processor.processBle(AdvertParser.parse(result))
        }
        val classicConsumer = scope.launch {
            for (device in classicChannel) processor.processClassic(device)
        }

        val btManager = applicationContext.getSystemService<BluetoothManager>()

        // BLE scan
        var bleCallback: ScanCallback? = null
        if (bleScan) {
            val scanner = btManager?.adapter?.bluetoothLeScanner
            if (scanner != null) {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                bleCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        bleChannel.trySend(result)
                    }
                }
                scanner.startScan(null, settings, bleCallback)
            }
        }

        // Classic BT scan
        var classicReceiver: BroadcastReceiver? = null
        if (classicScan) {
            val adapter = btManager?.adapter
            if (adapter != null) {
                classicReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == BluetoothDevice.ACTION_FOUND) {
                            val device = intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                            ) ?: return
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                            classicChannel.trySend(ClassicDevice(
                                mac = device.address, name = device.name, rssi = rssi,
                                deviceClass = device.bluetoothClass?.deviceClass,
                                deviceClassName = ClassicScanner.classToName(device.bluetoothClass),
                            ))
                        }
                    }
                }
                applicationContext.registerReceiver(classicReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                adapter.startDiscovery()
            }
        }

        // Live-update the FGS notification with current counts (skip when unchanged)
        val nm = NotificationManagerCompat.from(applicationContext)
        val liveUpdateJob = scope.launch {
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
            // Wait for scan duration
            delay(durationMs)
        } finally {
            liveUpdateJob.cancel()
            // Stop scans (also runs on cancellation)
            if (bleCallback != null) {
                try { btManager?.adapter?.bluetoothLeScanner?.stopScan(bleCallback) } catch (_: Exception) {}
            }
            bleChannel.close()

            if (classicReceiver != null) {
                try {
                    btManager?.adapter?.cancelDiscovery()
                    applicationContext.unregisterReceiver(classicReceiver)
                } catch (_: Exception) {}
            }
            classicChannel.close()
        }

        // Wait for consumers to drain remaining buffered items, then clean up scope
        bleConsumer.join()
        classicConsumer.join()
        scope.cancel()

        // Cleanup
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
