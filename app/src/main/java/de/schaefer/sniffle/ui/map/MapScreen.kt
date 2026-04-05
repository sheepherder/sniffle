package de.schaefer.sniffle.ui.map

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class MapMarker(
    val mac: String,
    val name: String?,
    val category: DeviceCategory,
    val lat: Double,
    val lon: Double,
)

class FullMapViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as App).database.deviceDao()

    val markers: Flow<List<MapMarker>> = combine(
        dao.observeAllDevices(),
        dao.observeLatestGeoSightings()
    ) { devices, sightings ->
        val deviceMap = devices.associateBy { it.mac }
        sightings.mapNotNull { s ->
            val dev = deviceMap[s.mac] ?: return@mapNotNull null
            if (dev.category == DeviceCategory.ONCE) return@mapNotNull null
            MapMarker(
                mac = s.mac,
                name = dev.model ?: dev.name ?: dev.mac,
                category = dev.category,
                lat = s.latitude ?: return@mapNotNull null,
                lon = s.longitude ?: return@mapNotNull null,
            )
        }
    }
}

@Composable
fun MapScreen(
    onMarkerTap: (String) -> Unit,
    viewModel: FullMapViewModel = viewModel()
) {
    val markers by viewModel.markers.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                // Default center: roughly central Europe
                controller.setCenter(GeoPoint(48.2, 11.8))
            }
        },
        update = { map ->
            map.overlays.clear()
            for (m in markers) {
                val marker = Marker(map)
                marker.position = GeoPoint(m.lat, m.lon)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = m.name
                marker.snippet = m.mac
                marker.setOnMarkerClickListener { _, _ ->
                    onMarkerTap(m.mac)
                    true
                }
                map.overlays.add(marker)
            }
            if (markers.isNotEmpty()) {
                val last = markers.last()
                map.controller.setCenter(GeoPoint(last.lat, last.lon))
            }
            map.invalidate()
        }
    )
}
