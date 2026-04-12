package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.ui.SniffleTopBar
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.ui.map.ShowAllChip
import de.schaefer.sniffle.ui.map.groupSightingMarkers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailMapScreen(
    mac: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.refreshLocation() }

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
                myLocation = currentLocation,
                showLocationFab = true,
                onLocationRequest = { viewModel.refreshLocation() },
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

