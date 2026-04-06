package de.schaefer.sniffle.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.BleScanner
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ProcessedDevice
import de.schaefer.sniffle.ble.ScanProcessor
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.data.DeviceCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveState(
    val sensors: List<ProcessedDevice> = emptyList(),
    val devices: List<ProcessedDevice> = emptyList(),
    val mystery: List<ProcessedDevice> = emptyList(),
    val once: List<ProcessedDevice> = emptyList(),
    val onceExpanded: Boolean = false,
    val totalCount: Int = 0,
)

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()
    private val bleScanner = BleScanner(application)
    private val classicScanner = ClassicScanner(application)
    private val processor = ScanProcessor(dao)

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private val liveDevices = mutableMapOf<String, ProcessedDevice>()
    private val lastSeenAt = mutableMapOf<String, Long>()
    private var refreshJob: Job? = null

    init {
        OuiLookup.init(application)
    }

    fun startScanning() {
        if (bleScanner.isAvailable) {
            viewModelScope.launch {
                bleScanner.scan().collect { advert ->
                    val result = processor.processBle(advert)
                    liveDevices[result.mac] = result
                    lastSeenAt[result.mac] = System.currentTimeMillis()
                    scheduleRefresh()
                }
            }
        }
        if (classicScanner.isAvailable) {
            viewModelScope.launch {
                classicScanner.scan().collect { device ->
                    val result = processor.processClassic(device)
                    liveDevices[result.mac] = result
                    lastSeenAt[result.mac] = System.currentTimeMillis()
                    scheduleRefresh()
                }
            }
        }
    }

    fun toggleOnceExpanded() {
        _state.value = _state.value.copy(onceExpanded = !_state.value.onceExpanded)
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(250) // debounce: max ~4 UI updates/sec
            val cutoff = System.currentTimeMillis() - 60_000
            liveDevices.keys.removeAll { mac -> (lastSeenAt[mac] ?: 0) < cutoff }

            val all = liveDevices.values.toList()
            _state.value = _state.value.copy(
                sensors = all.filter { it.category == DeviceCategory.SENSOR }.sortedByDescending { it.rssi },
                devices = all.filter { it.category == DeviceCategory.DEVICE }.sortedByDescending { it.rssi },
                mystery = all.filter { it.category == DeviceCategory.MYSTERY }.sortedByDescending { it.rssi },
                once = all.filter { it.category == DeviceCategory.ONCE }.sortedByDescending { it.rssi },
                totalCount = all.size,
            )
        }
    }
}
