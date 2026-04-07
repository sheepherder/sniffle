package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.ui.DeviceColor
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.ui.map.ClusterMapMarker
import de.schaefer.sniffle.ui.map.categoryColor
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.util.formatTimestampLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailMapScreen(
    mac: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(true) }

    val category = state.device?.category
    val allMarkers = remember(state.sightings, category) {
        state.sightings.mapNotNull { it.toClusterMarker(category) }
    }
    val markers = if (showAll) allMarkers else allMarkers.take(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.device?.displayName ?: mac, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ClusterMap(
                markers = markers,
                modifier = Modifier.fillMaxSize(),
            )

            FilterChip(
                selected = showAll,
                onClick = { showAll = !showAll },
                label = { Text(if (showAll) "Alle" else "Letzte") },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
    }
}

internal fun SightingEntity.toClusterMarker(category: DeviceCategory?): ClusterMapMarker? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return ClusterMapMarker(
        id = id.toString(),
        lat = lat,
        lon = lon,
        title = formatTimestampLong(timestamp),
        snippet = "$rssi dBm",
        color = category?.let { categoryColor(it) } ?: DeviceColor.toArgb(),
    )
}
