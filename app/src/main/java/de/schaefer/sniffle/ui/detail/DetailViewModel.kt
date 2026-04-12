package de.schaefer.sniffle.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.ui.map.MapLocationTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

data class DetailState(
    val device: DeviceEntity? = null,
    val sightings: List<SightingEntity> = emptyList(),
    val note: String = "",
    val deleted: Boolean = false,
) {
    val sensorSightings: List<SightingEntity> =
        sightings.filter { !it.decodedValues.isNullOrEmpty() }
}

@OptIn(FlowPreview::class)
class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()
    private val locationTracker = MapLocationTracker(application)
    val mac: String = savedStateHandle.get<String>("mac") ?: ""

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    val currentLocation: StateFlow<GeoPoint?> = locationTracker.currentLocation
    fun refreshLocation() = locationTracker.refresh()

    private var noteInitialized = false
    private val pendingNote = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(dao.observeDevice(mac), dao.observeSightings(mac)) { device, sightings ->
                device to sightings
            }.collect { (device, sightings) ->
                _state.update { old ->
                    old.copy(
                        device = device,
                        sightings = sightings,
                        note = if (!noteInitialized) {
                            noteInitialized = true
                            device?.note ?: ""
                        } else {
                            old.note
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            pendingNote.filterNotNull().debounce(500).collect { note ->
                dao.updateNote(mac, note.ifBlank { null })
            }
        }
    }

    fun updateNote(note: String) {
        _state.update { it.copy(note = note) }
        pendingNote.value = note
    }

    fun delete() {
        viewModelScope.launch {
            dao.deleteDevice(mac)
            _state.update { it.copy(deleted = true) }
        }
    }

    fun setShowOnMap(show: Boolean) {
        viewModelScope.launch { dao.setShowOnMap(mac, show) }
    }

    override fun onCleared() {
        locationTracker.cancel()
        super.onCleared()
    }
}
