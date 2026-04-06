package de.schaefer.sniffle.ui.map

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.ui.DeviceColor
import de.schaefer.sniffle.ui.MysteryColor
import de.schaefer.sniffle.ui.SensorColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    val currentLocation = MutableStateFlow<GeoPoint?>(null)

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

    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    loc?.let { currentLocation.value = GeoPoint(it.latitude, it.longitude) }
                }
        } catch (_: Exception) {}
    }
}

@Composable
fun MapScreen(
    onMarkerTap: (String) -> Unit,
    viewModel: FullMapViewModel = viewModel()
) {
    val markers by viewModel.markers.collectAsStateWithLifecycle(emptyList())
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.refreshLocation()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    mapView = this
                }
            },
            update = { map ->
                map.overlays.clear()

                // Current location marker (blue dot)
                currentLocation?.let { loc ->
                    val myMarker = Marker(map)
                    myMarker.position = loc
                    myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    myMarker.title = "Mein Standort"
                    myMarker.icon = createDotDrawable(0xFF2196F3.toInt(), 24)
                    myMarker.setOnMarkerClickListener { _, _ -> false }
                    map.overlays.add(myMarker)

                    // Center on first load
                    if (markers.isEmpty()) {
                        map.controller.setCenter(loc)
                    }
                }

                // Device markers
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

                map.invalidate()
            }
        )

        // Center-on-me FAB
        FloatingActionButton(
            onClick = {
                viewModel.refreshLocation()
                currentLocation?.let { loc ->
                    mapView?.controller?.animateTo(loc)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Mein Standort")
        }
    }
}

private fun createDotDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val dot = GradientDrawable()
    dot.shape = GradientDrawable.OVAL
    dot.setColor(color)
    dot.setSize(sizeDp, sizeDp)
    val border = GradientDrawable()
    border.shape = GradientDrawable.OVAL
    border.setColor(0xFFFFFFFF.toInt())
    border.setSize(sizeDp + 4, sizeDp + 4)
    return LayerDrawable(arrayOf(border, dot)).apply {
        setLayerInset(1, 2, 2, 2, 2)
    }
}
