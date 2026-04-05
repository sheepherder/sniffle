package de.schaefer.sniffle.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.data.DeviceEntity

@Composable
fun HistoryScreen(
    onDeviceTap: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle(HistoryState())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (state.sensors.isNotEmpty()) {
            item { SectionHeader("📡", "Sensoren", state.sensors.size, Color(0xFF4CAF50)) }
            items(state.sensors, key = { it.mac }) { device ->
                HistoryRow(device, onDeviceTap)
            }
        }
        if (state.devices.isNotEmpty()) {
            item { SectionHeader("📱", "Geräte", state.devices.size, Color(0xFF2196F3)) }
            items(state.devices, key = { it.mac }) { device ->
                HistoryRow(device, onDeviceTap)
            }
        }
        if (state.mystery.isNotEmpty()) {
            item { SectionHeader("👻", "Mystery", state.mystery.size, Color(0xFF9C27B0)) }
            items(state.mystery, key = { it.mac }) { device ->
                HistoryRow(device, onDeviceTap)
            }
        }
        if (state.once.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleOnceExpanded() }
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💨", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Flüchtige (${state.once.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9E9E9E)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (state.onceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (state.onceExpanded) {
                items(state.once, key = { it.mac }) { device ->
                    HistoryRow(device, onDeviceTap)
                }
            }
        }

        val total = state.sensors.size + state.devices.size + state.mystery.size + state.once.size
        if (total == 0) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Noch keine Funde", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: String, label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "$label ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun HistoryRow(device: DeviceEntity, onTap: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(device.mac) }
            .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        val title = device.model ?: device.name ?: device.mac
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

        val details = buildList {
            device.brand?.let { add(it) }
            device.company?.let { if (it != device.brand) add(it) }
            device.appearance?.let { add(it) }
            add("Zuletzt ${device.latestSeenDate}")
        }
        Text(
            details.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        device.note?.let {
            Text(
                "📝 $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
