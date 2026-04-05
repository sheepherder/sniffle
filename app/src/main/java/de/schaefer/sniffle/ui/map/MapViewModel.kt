package de.schaefer.sniffle.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MapDevice(
    val mac: String,
    val name: String?,
    val category: DeviceCategory,
    val latitude: Double,
    val longitude: Double,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.deviceDao()

    val devices: Flow<List<MapDevice>> = dao.observeAllDevices().map { entities ->
        // For each device, get the latest sighting with GPS
        // This is simplified — in production we'd use a JOIN query
        entities.mapNotNull { entity ->
            // We'll use a combined query later; for now use entity dates as proxy
            null // placeholder - will be populated via direct query
        }
    }

    // Simple approach: query sightings with GPS for all promoted devices
    val mapDevices: Flow<List<DeviceEntity>> = dao.observeAllDevices().map { all ->
        all.filter { it.category != DeviceCategory.ONCE }
    }
}
