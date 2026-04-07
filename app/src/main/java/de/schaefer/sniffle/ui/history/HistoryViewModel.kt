package de.schaefer.sniffle.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.includesBle
import de.schaefer.sniffle.data.includesClassic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class HistoryState(
    val sensors: List<DeviceEntity> = emptyList(),
    val devices: List<DeviceEntity> = emptyList(),
    val mystery: List<DeviceEntity> = emptyList(),
    val once: List<DeviceEntity> = emptyList(),
    val onceExpanded: Boolean = false,
    val bleCount: Int = 0,
    val classicCount: Int = 0,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()
    private val _onceExpanded = MutableStateFlow(false)

    val state: Flow<HistoryState> = combine(dao.observeAllDevices(), _onceExpanded) { devices, expanded ->
        HistoryState(
            sensors = devices.filter { it.category == DeviceCategory.SENSOR }.sortedByDescending { it.latestSeenMs },
            devices = devices.filter { it.category == DeviceCategory.DEVICE }.sortedByDescending { it.latestSeenMs },
            mystery = devices.filter { it.category == DeviceCategory.MYSTERY }.sortedByDescending { it.latestSeenMs },
            once = devices.filter { it.category == DeviceCategory.ONCE }.sortedByDescending { it.latestSeenMs },
            onceExpanded = expanded,
            bleCount = devices.count { it.transport.includesBle },
            classicCount = devices.count { it.transport.includesClassic },
        )
    }

    fun toggleOnceExpanded() {
        _onceExpanded.value = !_onceExpanded.value
    }
}
