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
import de.schaefer.sniffle.ui.theme.color
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.coroutines.flow.*
import org.osmdroid.util.GeoPoint

class FullMapViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as App).database.deviceDao()
    private var locationCts = CancellationTokenSource()

    val currentLocation = MutableStateFlow<GeoPoint?>(null)
    private val _showAll = MutableStateFlow(false)
    val showAll: StateFlow<Boolean> = _showAll

    fun toggleShowAll() { _showAll.value = !_showAll.value }

    private val devices = dao.observePromotedDevices()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val markers: Flow<List<ClusterMapMarker>> = _showAll.flatMapLatest { all ->
        val sightings = if (all) dao.observeAllGeoSightings() else dao.observeLatestGeoSightings()
        combine(devices, sightings) { devs, sights ->
            val deviceMap = devs.associateBy { it.mac }
            val latestPerDevice = mutableMapOf<String, Pair<Double, Double>>()
            val grouped = mutableMapOf<Pair<Double, Double>, MutableList<Pair<SightingEntity, DeviceEntity>>>()
            // Single pass: build latestPerDevice + grouped
            for (s in sights) {
                val lat = s.latitude ?: continue
                val lon = s.longitude ?: continue
                val dev = deviceMap[s.mac] ?: continue
                if (!all && dev.section == Section.TRANSIENT) continue
                latestPerDevice.putIfAbsent(s.mac, lat to lon)
                grouped.getOrPut(lat to lon) { mutableListOf() }.add(s to dev)
            }
            grouped.map { (loc, entries) ->
                val uniqueDevices = entries.distinctBy { it.second.mac }.map { it.second }
                val first = uniqueDevices.first()
                val isLatest = uniqueDevices.any { latestPerDevice[it.mac] == loc }
                val baseColor = first.section.color.toArgb()
                ClusterMapMarker(
                    lat = loc.first,
                    lon = loc.second,
                    title = if (uniqueDevices.size == 1) first.displayName
                            else "${uniqueDevices.size} Geräte",
                    color = if (isLatest) baseColor else desaturate(baseColor),
                    count = uniqueDevices.size,
                    deviceIds = uniqueDevices.map { it.mac to it.displayName },
                    isLatest = isLatest,
                )
            }.sortedBy { it.isLatest }
        }
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
            onDeviceTap = { mac -> onMarkerTap(mac) },
        )

        if (currentLocation == null && markers.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        ShowAllChip(
            showAll = showAll,
            onClick = { viewModel.toggleShowAll() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
    }
}
