package de.schaefer.sniffle.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

data class HistoryState(
    val sensors: List<DeviceEntity> = emptyList(),
    val devices: List<DeviceEntity> = emptyList(),
    val mystery: List<DeviceEntity> = emptyList(),
    val once: List<DeviceEntity> = emptyList(),
    val onceExpanded: Boolean = false,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()

    val allDevices: Flow<List<DeviceEntity>> = dao.observeAllDevices()

    private val _onceExpanded = MutableStateFlow(false)

    val state: Flow<HistoryState> = combine(allDevices, _onceExpanded) { devices, expanded ->
        HistoryState(
            sensors = devices.filter { it.category == DeviceCategory.SENSOR }
                .sortedByDescending { it.latestSeenDate },
            devices = devices.filter { it.category == DeviceCategory.DEVICE }
                .sortedByDescending { it.latestSeenDate },
            mystery = devices.filter { it.category == DeviceCategory.MYSTERY }
                .sortedByDescending { it.latestSeenDate },
            once = devices.filter { it.category == DeviceCategory.ONCE }
                .sortedByDescending { it.latestSeenDate },
            onceExpanded = expanded,
        )
    }

    fun toggleOnceExpanded() {
        _onceExpanded.value = !_onceExpanded.value
    }
}
