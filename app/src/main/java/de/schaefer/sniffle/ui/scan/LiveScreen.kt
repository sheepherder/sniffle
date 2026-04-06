package de.schaefer.sniffle.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.ui.deviceListContent

@Composable
fun LiveScreen(
    onDeviceTap: (String) -> Unit,
    viewModel: LiveViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Status bar
        item {
            val parts = buildList {
                if (state.bleActive) add("BLE: ${state.bleCount}")
                if (state.classicActive) add("BT: ${state.classicCount}")
                if (!state.bleActive && !state.classicActive) add("Scan deaktiviert")
            }
            Text(
                parts.joinToString("  •  ") + "  •  ${state.totalCount} gesamt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        deviceListContent(
            sensors = state.sensors,
            devices = state.devices,
            mystery = state.mystery,
            once = state.once,
            liveMacs = state.allMacs,
            showRssi = true,
            liveRssi = state.rssiMap,
            liveValues = state.valuesMap,
            onceExpanded = state.onceExpanded,
            onToggleOnce = viewModel::toggleOnceExpanded,
            onDeviceTap = onDeviceTap,
            emptyText = "Suche nach Geräten…",
        )
    }
}
