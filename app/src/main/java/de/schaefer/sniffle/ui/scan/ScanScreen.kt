package de.schaefer.sniffle.ui.scan

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.ui.deviceListContent

@Composable
fun ScanScreen(
    onDeviceTap: (String) -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startScanning()
    }

    val status = buildList {
        if (state.bleActive) add("BLE: ${state.bleCount}")
        if (state.classicActive) add("BT: ${state.classicCount}")
        if (!state.bleActive && !state.classicActive) add("Scan deaktiviert")
        add("${state.totalCount} gesamt")
    }.joinToString("  •  ")

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = state.mode == ListMode.LIVE,
                onClick = { viewModel.setMode(ListMode.LIVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Live") }
            SegmentedButton(
                selected = state.mode == ListMode.ALL,
                onClick = { viewModel.setMode(ListMode.ALL) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Alle") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            deviceListContent(
                grouped = state.grouped,
                onceExpanded = state.onceExpanded,
                onToggleOnce = viewModel::toggleOnceExpanded,
                onDeviceTap = onDeviceTap,
                emptyText = if (state.mode == ListMode.LIVE) "Suche nach Geräten…" else "Noch keine Funde",
                statusLine = status,
            )
        }
    }
}
