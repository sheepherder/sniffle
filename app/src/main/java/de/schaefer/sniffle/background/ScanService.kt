package de.schaefer.sniffle.background

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.AdvertParser
import de.schaefer.sniffle.ble.ClassicDevice
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.data.DeviceCategory
import kotlinx.coroutines.*
import java.time.LocalDate

class ScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processor: ScanProcessor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = intent?.getLongExtra("duration_ms", 60_000L) ?: 60_000L
        val bleScan = intent?.getBooleanExtra("ble", true) ?: true
        val classicScan = intent?.getBooleanExtra("classic", true) ?: true
        val showSummary = intent?.getBooleanExtra("summary", false) ?: false

        startForeground(1, NotificationHelper.serviceNotification(this))

        val dao = (application as App).database.deviceDao()
        OuiLookup.init(this)

        // Get GPS once for this scan session
        var lat: Double? = null
        var lon: Double? = null
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    lat = loc?.latitude
                    lon = loc?.longitude
                    processor = ScanProcessor(dao, lat, lon)
                }
        } catch (_: Exception) {}

        // Fallback if location fails
        if (processor == null) processor = ScanProcessor(dao)

        scope.launch {
            val proc = processor!!

            if (bleScan) {
                val scanner = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)
                    ?.adapter?.bluetoothLeScanner
                if (scanner != null) {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            scope.launch { proc.processBle(AdvertParser.parse(result)) }
                        }
                    }
                    scanner.startScan(null, settings, callback)
                    launch {
                        delay(durationMs)
                        scanner.stopScan(callback)
                    }
                }
            }

            if (classicScan) {
                val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                if (adapter != null) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                                val device = intent.getParcelableExtra<BluetoothDevice>(
                                    BluetoothDevice.EXTRA_DEVICE
                                ) ?: return
                                val rssi = intent.getShortExtra(
                                    BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                                ).toInt()
                                scope.launch {
                                    proc.processClassic(ClassicDevice(
                                        mac = device.address,
                                        name = device.name,
                                        rssi = rssi,
                                        deviceClass = device.bluetoothClass?.deviceClass,
                                        deviceClassName = ClassicScanner.classToName(device.bluetoothClass),
                                    ))
                                }
                            }
                        }
                    }
                    registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                    adapter.startDiscovery()
                    launch {
                        delay(durationMs)
                        adapter.cancelDiscovery()
                        unregisterReceiver(receiver)
                    }
                }
            }

            delay(durationMs + 500)

            // Cleanup stale ONCE entries
            dao.deleteStaleOnce(LocalDate.now().minusDays(90).toString())

            // Notifications for promoted/new devices
            // (handled via processor.wasPromoted in a future iteration)

            if (showSummary) {
                NotificationHelper.notifyScanSummary(
                    this@ScanService, proc.totalCount, proc.sensorCount, proc.newDeviceCount
                )
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
