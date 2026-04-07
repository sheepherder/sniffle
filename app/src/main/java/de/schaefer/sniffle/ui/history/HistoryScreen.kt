package de.schaefer.sniffle.ui.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.ui.deviceListContent

@Composable
fun HistoryScreen(
    onDeviceTap: (String) -> Unit,
    liveMacs: Set<String> = emptySet(),
    liveRssi: Map<String, Int> = emptyMap(),
    liveValues: Map<String, Map<String, Any>> = emptyMap(),
    statusLine: String? = null,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle(HistoryState())

    val total = state.sensors.size + state.devices.size + state.mystery.size + state.once.size
    val status = statusLine ?: buildList {
        if (state.bleCount > 0) add("BLE: ${state.bleCount}")
        if (state.classicCount > 0) add("BT: ${state.classicCount}")
        add("${total} gesamt")
    }.joinToString("  •  ")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        deviceListContent(
            sensors = state.sensors,
            devices = state.devices,
            mystery = state.mystery,
            once = state.once,
            liveMacs = liveMacs,
            showRssi = false,
            liveRssi = liveRssi,
            liveValues = liveValues,
            onceExpanded = state.onceExpanded,
            onToggleOnce = viewModel::toggleOnceExpanded,
            onDeviceTap = onDeviceTap,
            emptyText = "Noch keine Funde",
            statusLine = status,
        )
    }
}
