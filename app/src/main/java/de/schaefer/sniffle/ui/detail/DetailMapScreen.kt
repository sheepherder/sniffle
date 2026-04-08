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
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.ui.map.ClusterMapMarker
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

    val section = state.device?.section
    val allMarkers = remember(state.sightings, section) {
        state.sightings.mapNotNull { it.toClusterMarker(section) }
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

internal fun SightingEntity.toClusterMarker(section: Section?): ClusterMapMarker? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return ClusterMapMarker(
        id = id.toString(),
        lat = lat,
        lon = lon,
        title = formatTimestampLong(timestamp),
        snippet = "$rssi dBm",
        color = (section?.color ?: Section.DEVICE.color).toArgb(),
    )
}
