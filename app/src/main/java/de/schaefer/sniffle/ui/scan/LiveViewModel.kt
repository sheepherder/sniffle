package de.schaefer.sniffle.ui.scan

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.BleScanner
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ProcessedDevice
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.util.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveState(
    val sensors: List<DeviceEntity> = emptyList(),
    val devices: List<DeviceEntity> = emptyList(),
    val mystery: List<DeviceEntity> = emptyList(),
    val once: List<DeviceEntity> = emptyList(),
    val onceExpanded: Boolean = false,
    val totalCount: Int = 0,
    val allMacs: Set<String> = emptySet(),
    val rssiMap: Map<String, Int> = emptyMap(),
    val valuesMap: Map<String, Map<String, Any>> = emptyMap(),
    val bleActive: Boolean = true,
    val classicActive: Boolean = true,
    val bleCount: Int = 0,
    val classicCount: Int = 0,
)

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Preferences(application)
    private val dao = (application as App).database.deviceDao()
    private val bleScanner = BleScanner(application)
    private val classicScanner = ClassicScanner(application)
    private val processor = ScanProcessor(dao)
    private val locationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private val liveDevices = mutableMapOf<String, ProcessedDevice>()
    private val lastSeenAt = mutableMapOf<String, Long>()
    private var refreshJob: Job? = null
    private var locationCallback: LocationCallback? = null
    private var bleJob: Job? = null
    private var classicJob: Job? = null

    init {
        OuiLookup.init(application)
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        startLocationUpdates()
        restartScans()
    }

    /** Re-read settings and restart/stop scans accordingly. */
    fun restartScans() {
        val bleEnabled = prefs.bleScan
        val classicEnabled = prefs.classicScan

        // BLE
        if (bleEnabled && bleScanner.isAvailable && bleJob == null) {
            bleJob = viewModelScope.launch {
                bleScanner.scan().collect { advert ->
                    val result = processor.processBle(advert)
                    liveDevices[result.mac] = result
                    lastSeenAt[result.mac] = System.currentTimeMillis()
                    scheduleRefresh()
                }
            }
        } else if (!bleEnabled && bleJob != null) {
            bleJob?.cancel()
            bleJob = null
        }

        // Classic BT
        if (classicEnabled && classicScanner.isAvailable && classicJob == null) {
            classicJob = viewModelScope.launch {
                classicScanner.scan().collect { device ->
                    val result = processor.processClassic(device)
                    liveDevices[result.mac] = result
                    lastSeenAt[result.mac] = System.currentTimeMillis()
                    scheduleRefresh()
                }
            }
        } else if (!classicEnabled && classicJob != null) {
            classicJob?.cancel()
            classicJob = null
        }

        _state.value = _state.value.copy(
            bleActive = bleEnabled && bleScanner.isAvailable,
            classicActive = classicEnabled && classicScanner.isAvailable,
        )
    }

    fun toggleOnceExpanded() {
        _state.value = _state.value.copy(onceExpanded = !_state.value.onceExpanded)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationCallback != null) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000)
            .setMinUpdateIntervalMillis(10_000)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    processor.latitude = loc.latitude
                    processor.longitude = loc.longitude
                }
            }
        }
        locationCallback = callback
        try {
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        locationCallback?.let { locationClient.removeLocationUpdates(it) }
        kotlinx.coroutines.runBlocking { processor.flush() }
        super.onCleared()
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(250)
            val cutoff = System.currentTimeMillis() - 60_000
            liveDevices.keys.removeAll { mac -> (lastSeenAt[mac] ?: 0) < cutoff }

            val all = liveDevices.values.toList()
            val entities = all.map { it.toEntity() }
            val rssiMap = all.associate { it.mac to it.rssi }
            val valuesMap = all.filter { it.values.isNotEmpty() }.associate { it.mac to it.values }

            _state.value = _state.value.copy(
                sensors = entities.filter { it.category == DeviceCategory.SENSOR }.sortedByDescending { rssiMap[it.mac] },
                devices = entities.filter { it.category == DeviceCategory.DEVICE }.sortedByDescending { rssiMap[it.mac] },
                mystery = entities.filter { it.category == DeviceCategory.MYSTERY }.sortedByDescending { rssiMap[it.mac] },
                once = entities.filter { it.category == DeviceCategory.ONCE }.sortedByDescending { rssiMap[it.mac] },
                totalCount = entities.size,
                allMacs = liveDevices.keys.toSet(),
                rssiMap = rssiMap,
                valuesMap = valuesMap,
                bleCount = all.count { it.transport == Transport.BLE },
                classicCount = all.count { it.transport == Transport.CLASSIC },
            )
        }
    }
}

private fun ProcessedDevice.toEntity() = DeviceEntity(
    mac = mac, name = name, brand = brand, model = model,
    deviceType = type, transport = transport, category = category,
    appearance = appearance, company = company,
)
