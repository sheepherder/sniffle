package de.schaefer.sniffle.background

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
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
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.AdvertParser
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.classify.AppearanceResolver
import de.schaefer.sniffle.classify.DeviceClassifier
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.classify.ServiceUuidResolver
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceDao
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.decoder.DecoderChain
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class ScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dao: DeviceDao
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var newDeviceCount = 0
    private var sensorCount = 0
    private var totalCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = intent?.getLongExtra("duration_ms", 60_000L) ?: 60_000L
        val bleScan = intent?.getBooleanExtra("ble", true) ?: true
        val classicScan = intent?.getBooleanExtra("classic", true) ?: true
        val showSummary = intent?.getBooleanExtra("summary", false) ?: false

        startForeground(1, NotificationHelper.serviceNotification(this))

        dao = (application as App).database.deviceDao()
        OuiLookup.init(this)

        // Get GPS
        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    latitude = loc?.latitude
                    longitude = loc?.longitude
                }
        } catch (_: Exception) {}

        scope.launch {
            val jobs = mutableListOf<Job>()

            if (bleScan) {
                val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                val scanner = adapter?.bluetoothLeScanner
                if (scanner != null) {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()

                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            scope.launch { handleBleResult(result) }
                        }
                    }

                    scanner.startScan(null, settings, callback)
                    jobs.add(launch {
                        delay(durationMs)
                        scanner.stopScan(callback)
                    })
                }
            }

            if (classicScan) {
                val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                if (adapter != null) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                                scope.launch { handleClassicResult(device, rssi) }
                            }
                        }
                    }
                    registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                    adapter.startDiscovery()

                    jobs.add(launch {
                        delay(durationMs)
                        adapter.cancelDiscovery()
                        unregisterReceiver(receiver)
                    })
                }
            }

            // Wait for scan duration
            delay(durationMs + 500)

            // Cleanup old ONCE entries
            val cutoff = LocalDate.now().minusDays(90).toString()
            dao.deleteStaleOnce(cutoff)

            // Summary notification
            if (showSummary) {
                NotificationHelper.notifyScanSummary(this@ScanService, totalCount, sensorCount, newDeviceCount)
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleBleResult(result: ScanResult) {
        val advert = AdvertParser.parse(result)
        val decoded = DecoderChain.decode(advert)
        val today = LocalDate.now().toString()
        val existing = dao.getDevice(advert.mac)
        val isNew = existing == null

        val freshCategory = DeviceClassifier.classifyBle(advert, decoded)
        val category = when {
            existing == null -> freshCategory
            existing.category == DeviceCategory.ONCE && freshCategory == DeviceCategory.SENSOR -> DeviceCategory.SENSOR
            else -> existing.category
        }

        val ouiVendor = OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = AppearanceResolver.resolve(advert.appearance)
        val serviceHints = ServiceUuidResolver.resolve(advert.serviceUuids)

        val entity = DeviceEntity(
            mac = advert.mac,
            name = advert.name ?: existing?.name,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            modelId = decoded?.modelId ?: existing?.modelId,
            deviceType = decoded?.type ?: existing?.deviceType,
            transport = Transport.BLE,
            category = category,
            appearance = appearance ?: existing?.appearance,
            company = company ?: existing?.company,
            firstSeenDate = existing?.firstSeenDate ?: today,
            latestSeenDate = today,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )
        dao.upsertDevice(entity)

        val valuesJson = decoded?.values?.takeIf { it.isNotEmpty() }?.let { vals ->
            try { Json.encodeToString(vals.mapValues { it.value.toString() }) } catch (_: Exception) { null }
        }
        dao.insertSighting(SightingEntity(
            mac = advert.mac, timestamp = System.currentTimeMillis(),
            latitude = latitude, longitude = longitude,
            rssi = advert.rssi, decodedValues = valuesJson,
        ))

        totalCount++
        if (decoded?.hasSensorData == true) sensorCount++

        // Notification for new sensors
        if (isNew && category == DeviceCategory.SENSOR && !entity.notified) {
            NotificationHelper.notifyDevice(this, entity, valuesJson)
            dao.upsertDevice(entity.copy(notified = true))
            newDeviceCount++
        }

        // Check promotion
        if (category == DeviceCategory.ONCE) {
            val days = dao.countDistinctDays(advert.mac)
            if (days >= 3) {
                val hasId = DeviceClassifier.hasIdentity(
                    advert.name, company, appearance, serviceHints, company, null
                )
                val newCat = DeviceClassifier.promotedCategory(hasId)
                dao.updateCategory(advert.mac, newCat)
                val promoted = entity.copy(category = newCat)
                NotificationHelper.notifyDevice(this, promoted, null)
                dao.upsertDevice(promoted.copy(notified = true))
                newDeviceCount++
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleClassicResult(device: BluetoothDevice, rssi: Int) {
        val mac = device.address
        val name = device.name
        val today = LocalDate.now().toString()
        val existing = dao.getDevice(mac)
        val isNew = existing == null
        val category = existing?.category ?: DeviceCategory.ONCE
        val className = ClassicScanner.classToName(device.bluetoothClass)

        dao.upsertDevice(DeviceEntity(
            mac = mac, name = name ?: existing?.name,
            model = className ?: existing?.model,
            transport = Transport.CLASSIC, category = category,
            appearance = className, company = OuiLookup.lookup(mac),
            firstSeenDate = existing?.firstSeenDate ?: today,
            latestSeenDate = today, note = existing?.note,
            notified = existing?.notified ?: false,
        ))
        dao.insertSighting(SightingEntity(
            mac = mac, timestamp = System.currentTimeMillis(),
            latitude = latitude, longitude = longitude, rssi = rssi,
        ))

        totalCount++

        if (category == DeviceCategory.ONCE) {
            val days = dao.countDistinctDays(mac)
            if (days >= 3) {
                val hasId = DeviceClassifier.hasIdentity(name, OuiLookup.lookup(mac), className, emptyList(), null, className)
                val newCat = DeviceClassifier.promotedCategory(hasId)
                dao.updateCategory(mac, newCat)
                NotificationHelper.notifyDevice(this, DeviceEntity(
                    mac = mac, name = name, model = className,
                    transport = Transport.CLASSIC, category = newCat,
                    company = OuiLookup.lookup(mac),
                    firstSeenDate = existing?.firstSeenDate ?: today,
                    latestSeenDate = today,
                ), null)
                newDeviceCount++
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
