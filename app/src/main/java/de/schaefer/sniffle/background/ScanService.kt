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
import android.location.Location
import android.os.IBinder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.AdvertParser
import de.schaefer.sniffle.ble.ClassicDevice
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.OuiLookup
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.LocalDate
import kotlin.coroutines.resume

class ScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        val btManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager

        scope.launch {
            // Get GPS with timeout before starting scan
            val loc = withTimeoutOrNull(3_000L) { getLocation() }
            val processor = ScanProcessor(dao, loc?.latitude, loc?.longitude)

            // Channel to serialize BLE processing (avoid unbounded coroutines)
            val bleChannel = Channel<ParsedAdvert>(Channel.UNLIMITED)
            val classicChannel = Channel<ClassicDevice>(Channel.UNLIMITED)

            // Consumer coroutines
            val bleConsumer = launch { for (advert in bleChannel) processor.processBle(advert) }
            val classicConsumer = launch { for (device in classicChannel) processor.processClassic(device) }

            if (bleScan) {
                val scanner = btManager?.adapter?.bluetoothLeScanner
                if (scanner != null) {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            bleChannel.trySend(AdvertParser.parse(result))
                        }
                    }
                    scanner.startScan(null, settings, callback)
                    launch {
                        delay(durationMs)
                        scanner.stopScan(callback)
                        bleChannel.close()
                    }
                } else {
                    bleChannel.close()
                }
            } else {
                bleChannel.close()
            }

            if (classicScan) {
                val adapter = btManager?.adapter
                if (adapter != null) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                                val device = intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                                ) ?: return
                                val rssi = intent.getShortExtra(
                                    BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                                ).toInt()
                                classicChannel.trySend(ClassicDevice(
                                    mac = device.address,
                                    name = device.name,
                                    rssi = rssi,
                                    deviceClass = device.bluetoothClass?.deviceClass,
                                    deviceClassName = ClassicScanner.classToName(device.bluetoothClass),
                                ))
                            }
                        }
                    }
                    registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                    adapter.startDiscovery()
                    launch {
                        delay(durationMs)
                        adapter.cancelDiscovery()
                        unregisterReceiver(receiver)
                        classicChannel.close()
                    }
                } else {
                    classicChannel.close()
                }
            } else {
                classicChannel.close()
            }

            // Wait for consumers to finish, then flush remaining data to DB
            bleConsumer.join()
            classicConsumer.join()
            processor.flush()

            dao.deleteStaleOnce(LocalDate.now().minusDays(90).toString())

            if (showSummary) {
                NotificationHelper.notifyScanSummary(
                    this@ScanService,
                    processor.totalCount.get(),
                    processor.sensorCount.get(),
                    processor.newDeviceCount.get(),
                )
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: Exception) {
            cont.resume(null)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
