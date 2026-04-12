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
import de.schaefer.sniffle.ble.ProcessedDevice
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.FastPairLookup
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.deleteStale
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.data.includesBle
import de.schaefer.sniffle.data.includesClassic
import de.schaefer.sniffle.util.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ListMode { LIVE, ALL }

data class DisplayDevice(
    val entity: DeviceEntity,
    val isLive: Boolean = false,
    val rssi: Int? = null,
    val values: Map<String, Any> = emptyMap(),
    val pingCount: Int = 0,
    val lastPingMs: Long = 0,
    val agoSec: Int = 0,
)

data class ScanState(
    val mode: ListMode = ListMode.LIVE,
    val grouped: Map<Section, List<DisplayDevice>> = emptyMap(),
    val onceExpanded: Boolean = false,
    val totalCount: Int = 0,
    val bleActive: Boolean = true,
    val classicActive: Boolean = true,
    val bleCount: Int = 0,
    val classicCount: Int = 0,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Preferences(application)
    private val app = application as App
    private val dao = app.database.deviceDao()
    private val coordinator = app.scanCoordinator
    private val processor = ScanProcessor(dao)
    private val locationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private var dbEntities = mapOf<String, DeviceEntity>()
    private val liveData = mutableMapOf<String, LiveInfo>()

    private var refreshJob: Job? = null
    private var lastRefreshMs = 0L
    private var locationCallback: LocationCallback? = null
    private var bleJob: Job? = null
    private var classicJob: Job? = null
    private var scanningActive = false

    private data class LiveInfo(
        val entity: DeviceEntity,
        val rssi: Int,
        val values: Map<String, Any>,
        val pingCount: Int,
        val lastPingMs: Long,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            OuiLookup.init(application)
            FastPairLookup.init(application)
        }
        viewModelScope.launch {
            dao.observeAllDevices().collect { devices ->
                dbEntities = devices.associateBy { it.mac }
                buildState()
            }
        }
        // Tick every second to advance "seconds since last ping" counters and
        // expire stale entries from the live view, even when no adverts arrive.
        // Only needed in LIVE mode — ALL mode doesn't display per-device timers.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (liveData.isNotEmpty() && _state.value.mode == ListMode.LIVE) buildState()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        scanningActive = true
        startLocationUpdates()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteStale()
            }
            restartScans()
        }
    }

    fun stopScanning() {
        scanningActive = false
        bleJob?.cancel(); bleJob = null
        classicJob?.cancel(); classicJob = null
        refreshJob?.cancel(); refreshJob = null
        stopLocationUpdates()
    }

    /**
     * Reconciles active scan coroutines with current preferences.
     * Safe to call externally (e.g. from SettingsScreen on pref change):
     * no-ops when ScanScreen is not currently in STARTED state.
     */
    fun restartScans() {
        if (!scanningActive) return
        val bleEnabled = prefs.bleScan
        val classicEnabled = prefs.classicScan

        if (bleEnabled && coordinator.bleAvailable && bleJob == null) {
            bleJob = viewModelScope.launch {
                coordinator.bleResults.collect { advert ->
                    onScanResult(processor.processBle(advert))
                }
            }
        } else if (!bleEnabled && bleJob != null) {
            bleJob?.cancel()
            bleJob = null
        }

        if (classicEnabled && coordinator.classicAvailable && classicJob == null) {
            classicJob = viewModelScope.launch {
                coordinator.classicResults.collect { device ->
                    onScanResult(processor.processClassic(device))
                }
            }
        } else if (!classicEnabled && classicJob != null) {
            classicJob?.cancel()
            classicJob = null
        }

        _state.value = _state.value.copy(
            bleActive = bleEnabled && coordinator.bleAvailable,
            classicActive = classicEnabled && coordinator.classicAvailable,
        )
    }

    fun setMode(mode: ListMode) {
        _state.value = _state.value.copy(mode = mode)
        buildState()
    }

    fun toggleOnceExpanded() {
        _state.value = _state.value.copy(onceExpanded = !_state.value.onceExpanded)
    }

    private fun onScanResult(result: ProcessedDevice) {
        val mac = result.mac
        val prev = liveData[mac]
        val mergedValues = (prev?.values ?: emptyMap()).toMutableMap().apply {
            if (result.values.isNotEmpty()) putAll(result.values)
        }
        liveData[mac] = LiveInfo(
            entity = result.toEntity(),
            rssi = result.rssi,
            values = mergedValues,
            pingCount = (prev?.pingCount ?: 0) + 1,
            lastPingMs = System.currentTimeMillis(),
        )
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefreshMs
        if (elapsed >= 100) {
            refreshJob?.cancel()
            lastRefreshMs = now
            buildState()
        } else if (refreshJob == null || refreshJob?.isActive != true) {
            refreshJob = viewModelScope.launch {
                delay(100 - elapsed)
                lastRefreshMs = System.currentTimeMillis()
                buildState()
            }
        }
    }

    private fun buildState() {
        val now = System.currentTimeMillis()
        val cutoff = now - 60_000
        val mode = _state.value.mode

        liveData.entries.removeAll { it.value.lastPingMs < cutoff }

        fun agoSec(lastPingMs: Long) =
            if (lastPingMs > 0) ((now - lastPingMs) / 1000).toInt() else 0

        val source = buildList {
            for (entity in dbEntities.values) {
                val live = liveData[entity.mac]
                if (mode == ListMode.LIVE && live == null) continue
                add(DisplayDevice(
                    entity = entity,
                    isLive = live != null,
                    rssi = live?.rssi,
                    values = live?.values ?: emptyMap(),
                    pingCount = live?.pingCount ?: 0,
                    lastPingMs = live?.lastPingMs ?: 0,
                    agoSec = agoSec(live?.lastPingMs ?: 0),
                ))
            }
            for ((mac, live) in liveData) {
                if (mac in dbEntities) continue
                add(DisplayDevice(
                    entity = live.entity,
                    isLive = true,
                    rssi = live.rssi,
                    values = live.values,
                    pingCount = live.pingCount,
                    lastPingMs = live.lastPingMs,
                    agoSec = agoSec(live.lastPingMs),
                ))
            }
        }

        val modeComparator: Comparator<DisplayDevice> = when (mode) {
            ListMode.LIVE -> compareByDescending { it.pingCount }
            ListMode.ALL -> compareByDescending { it.entity.latestSeenMs }
        }

        val sorted = source.sortedWith(
            compareBy<DisplayDevice> { it.entity.section }.then(modeComparator)
        )

        var bleCount = 0
        var classicCount = 0
        for (d in source) {
            if (d.entity.transport.includesBle) bleCount++
            if (d.entity.transport.includesClassic) classicCount++
        }

        _state.value = _state.value.copy(
            grouped = sorted.groupBy { it.entity.section },
            totalCount = source.size,
            bleCount = bleCount,
            classicCount = classicCount,
        )
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

    private fun stopLocationUpdates() {
        locationCallback?.let { locationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }
}

private fun ProcessedDevice.toEntity() = DeviceEntity(
    mac = mac, name = name, brand = brand, model = model,
    deviceType = type, transport = transport,
    hasSensorData = hasSensorData, promoted = promoted,
    appearance = appearance, company = company,
)
