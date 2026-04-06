package de.schaefer.sniffle.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailState(
    val device: DeviceEntity? = null,
    val sightings: List<SightingEntity> = emptyList(),
    val note: String = "",
    val deleted: Boolean = false,
)

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()
    val mac: String = savedStateHandle.get<String>("mac") ?: ""

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private var noteInitialized = false

    init {
        viewModelScope.launch {
            dao.observeSightings(mac).collect { sightings ->
                val device = dao.getDevice(mac)
                _state.update { old ->
                    old.copy(
                        device = device,
                        sightings = sightings,
                        // Only set note from DB on first load, never overwrite user input
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
    }

    fun updateNote(note: String) {
        _state.update { it.copy(note = note) }
        viewModelScope.launch {
            dao.updateNote(mac, note.ifBlank { null })
        }
    }

    fun delete() {
        viewModelScope.launch {
            dao.deleteDevice(mac)
            _state.update { it.copy(deleted = true) }
        }
    }
}
