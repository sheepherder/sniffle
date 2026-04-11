package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.ui.SniffleTopBar
import de.schaefer.sniffle.ui.theme.color
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.ui.map.ClusterMapMarker
import de.schaefer.sniffle.ui.map.ShowAllChip
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
        groupSightingMarkers(state.sightings, section)
    }
    val markers = if (showAll) allMarkers else allMarkers.filter { it.isLatest }

    Scaffold(
        topBar = {
            SniffleTopBar(title = state.device?.displayName ?: mac, onBack = onBack)
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

            ShowAllChip(
                showAll = showAll,
                onClick = { showAll = !showAll },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
    }
}

private fun desaturate(color: Int): Int {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val grey = (r * 0.3 + g * 0.59 + b * 0.11).toInt()
    fun mix(c: Int) = (c * 0.4 + grey * 0.6).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}

internal fun groupSightingMarkers(sightings: List<SightingEntity>, section: Section?): List<ClusterMapMarker> {
    val baseColor = (section?.color ?: Section.DEVICE.color).toArgb()
    // Group by exact GPS location
    val grouped = mutableMapOf<Pair<Double, Double>, MutableList<SightingEntity>>()
    for (s in sightings) {
        val lat = s.latitude ?: continue
        val lon = s.longitude ?: continue
        grouped.getOrPut(lat to lon) { mutableListOf() }.add(s)
    }
    if (grouped.isEmpty()) return emptyList()
    // Sightings are sorted DESC — the first location seen is the latest
    val latestLoc = grouped.entries.first().key
    return grouped.map { (loc, entries) ->
        val newest = entries.first() // sorted DESC
        val isLatest = loc == latestLoc
        ClusterMapMarker(
            id = newest.mac,
            lat = loc.first,
            lon = loc.second,
            title = formatTimestampLong(newest.timestamp),
            snippet = "${entries.size} Sichtungen — ${newest.rssi} dBm",
            color = if (isLatest) baseColor else desaturate(baseColor),
            count = entries.size,
            isLatest = isLatest,
        )
    }.sortedBy { it.isLatest } // grey first, latest on top
}
