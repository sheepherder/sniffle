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
import de.schaefer.sniffle.data.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val category: DeviceCategory,
    val brand: String?,
    val model: String?,
    val type: String?,
    val values: Map<String, Any> = emptyMap(),
    val company: String?,
    val appearance: String?,
    val serviceHints: List<String> = emptyList(),
    val transport: Transport = Transport.BLE,
    val guessedType: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
)

data class LiveState(
    val sensors: List<LiveDevice> = emptyList(),
    val devices: List<LiveDevice> = emptyList(),
    val mystery: List<LiveDevice> = emptyList(),
    val once: List<LiveDevice> = emptyList(),
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

    private val liveDevices = mutableMapOf<String, LiveDevice>()

    init {
        OuiLookup.init(application)
    }

    fun startScanning() {
        if (bleScanner.isAvailable) {
            viewModelScope.launch {
                bleScanner.scan().collect { advert ->
                    val result = processor.processBle(advert)
                    updateLive(result)
                }
            }
        }
        if (classicScanner.isAvailable) {
            viewModelScope.launch {
                classicScanner.scan().collect { device ->
                    val result = processor.processClassic(device)
                    updateLive(result)
                }
            }
        }
    }

    fun toggleOnceExpanded() {
        _state.value = _state.value.copy(onceExpanded = !_state.value.onceExpanded)
    }

    private fun updateLive(result: ProcessedDevice) {
        liveDevices[result.mac] = LiveDevice(
            mac = result.mac,
            name = result.name,
            rssi = result.rssi,
            category = result.category,
            brand = result.brand,
            model = result.model,
            type = result.type,
            values = result.values,
            company = result.company,
            appearance = result.appearance,
            serviceHints = result.serviceHints,
            transport = result.transport,
            guessedType = result.guessedType,
        )
        refreshState()
    }

    private fun refreshState() {
        val cutoff = System.currentTimeMillis() - 60_000
        liveDevices.entries.removeIf { it.value.lastSeen < cutoff }

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
