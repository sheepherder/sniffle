package de.schaefer.sniffle.ui.map

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.schaefer.sniffle.App
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.coroutines.flow.*
import org.osmdroid.util.GeoPoint

class FullMapViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as App).database.deviceDao()
    private var locationCts = CancellationTokenSource()

    val currentLocation = MutableStateFlow<GeoPoint?>(null)
    val showAll = MutableStateFlow(false)

    private val devices = dao.observeAllDevices()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val markers: Flow<List<ClusterMapMarker>> = showAll.flatMapLatest { all ->
        val sightings = if (all) dao.observeAllGeoSightings() else dao.observeLatestGeoSightings()
        combine(devices, sightings) { devs, sights ->
            val deviceMap = devs.associateBy { it.mac }
            sights.mapNotNull { s ->
                val dev = deviceMap[s.mac] ?: return@mapNotNull null
                if (!all && dev.section == Section.TRANSIENT) return@mapNotNull null
                sightingToClusterMarker(s, dev)
            }
        }
    }

    private fun sightingToClusterMarker(s: SightingEntity, dev: DeviceEntity): ClusterMapMarker? {
        val lat = s.latitude ?: return null
        val lon = s.longitude ?: return null
        return ClusterMapMarker(
            id = s.mac,
            lat = lat,
            lon = lon,
            title = dev.displayName,
            snippet = "${dev.section.label} — ${s.mac}",
            color = dev.section.color.toArgb(),
        )
    }

    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
            client.lastLocation.addOnSuccessListener { loc ->
                loc?.let { currentLocation.value = GeoPoint(it.latitude, it.longitude) }
            }
            locationCts.cancel()
            locationCts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, locationCts.token)
                .addOnSuccessListener { loc ->
                    loc?.let { currentLocation.value = GeoPoint(it.latitude, it.longitude) }
                }
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        locationCts.cancel()
        super.onCleared()
    }
}

@Composable
fun MapScreen(
    onMarkerTap: (String) -> Unit,
    viewModel: FullMapViewModel = viewModel()
) {
    val showAll by viewModel.showAll.collectAsStateWithLifecycle()
    val markers by viewModel.markers.collectAsStateWithLifecycle(emptyList())
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshLocation()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ClusterMap(
            markers = markers,
            modifier = Modifier.fillMaxSize(),
            myLocation = currentLocation,
            showLocationFab = true,
            onLocationRequest = { viewModel.refreshLocation() },
            onInfoWindowTap = { marker -> onMarkerTap(marker.id) },
        )

        if (currentLocation == null && markers.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        FilterChip(
            selected = showAll,
            onClick = { viewModel.showAll.value = !showAll },
            label = { Text(if (showAll) "Alle" else "Letzte") },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
    }
}
