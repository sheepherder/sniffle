package de.schaefer.sniffle.ui.scan

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.ble.ProcessedDevice
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.Transport
import kotlin.math.max
import kotlin.math.min

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
        // Sensors
        if (state.sensors.isNotEmpty()) {
            item { CategoryHeader("📡", "Sensoren", state.sensors.size, SensorColor) }
            items(state.sensors, key = { it.mac }) { device ->
                DeviceRow(device, onDeviceTap)
            }
        }

        // Devices
        if (state.devices.isNotEmpty()) {
            item { CategoryHeader("📱", "Geräte", state.devices.size, DeviceColor) }
            items(state.devices, key = { it.mac }) { device ->
                DeviceRow(device, onDeviceTap)
            }
        }

        // Mystery
        if (state.mystery.isNotEmpty()) {
            item { CategoryHeader("👻", "Mystery", state.mystery.size, MysteryColor) }
            items(state.mystery, key = { it.mac }) { device ->
                DeviceRow(device, onDeviceTap)
            }
        }

        // Once (collapsible)
        if (state.once.isNotEmpty()) {
            item {
                CollapsibleHeader(
                    icon = "💨",
                    label = "Flüchtige",
                    count = state.once.size,
                    summary = buildOnceSummary(state.once),
                    expanded = state.onceExpanded,
                    onToggle = { viewModel.toggleOnceExpanded() },
                )
            }
            if (state.onceExpanded) {
                items(state.once, key = { it.mac }) { device ->
                    DeviceRow(device, onDeviceTap)
                }
            }
        }

        if (state.totalCount == 0) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Suche nach Geräten…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(icon: String, label: String, count: Int, color: Color) {
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
private fun CollapsibleHeader(
    icon: String, label: String, count: Int, summary: String,
    expanded: Boolean, onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "$label ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnceColor
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!expanded) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun DeviceRow(device: ProcessedDevice, onTap: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(device.mac) }
            .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RssiBar(device.rssi)
            Spacer(Modifier.width(8.dp))
            Text(
                "${device.rssi}dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(52.dp)
            )
            Spacer(Modifier.width(4.dp))

            val title = device.model ?: device.name ?: device.guessedType ?: device.mac
            val transportTag = if (device.transport == Transport.CLASSIC) " (BT)" else ""
            Text(
                "$title$transportTag",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        // Sensor values
        if (device.values.isNotEmpty()) {
            Text(
                device.values.entries.joinToString("  ") { (k, v) ->
                    val formatted = if (v is Double) "%.1f".format(v) else v.toString()
                    "$k $formatted"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 72.dp),
                maxLines = 2,
            )
        }

        // Extra info for non-sensor devices
        if (device.values.isEmpty()) {
            val extras = buildList {
                device.company?.let { add(it) }
                device.appearance?.let { add(it) }
                device.guessedType?.let { if (device.model == null) add(it) }
            }
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun RssiBar(rssi: Int) {
    val strength = max(0, min(4, (rssi + 100) / 15))
    val color = when {
        strength >= 3 -> Color(0xFF4CAF50)
        strength >= 2 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Text(
        "█".repeat(strength) + "░".repeat(4 - strength),
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 12.sp,
    )
}

private fun buildOnceSummary(devices: List<ProcessedDevice>): String {
    val groups = mutableMapOf<String, Int>()
    for (d in devices) {
        val label = when {
            d.brand == "Apple" || d.company == "Apple" -> "Apple"
            d.brand == "GENERIC" && d.model == "MS-CDP" -> "Microsoft"
            d.company != null -> d.company
            d.name != null -> "benannt"
            else -> "?"
        }
        groups[label] = (groups[label] ?: 0) + 1
    }
    return groups.entries.sortedByDescending { it.value }
        .joinToString(" • ") { "${it.value}× ${it.key}" }
}

private val SensorColor = Color(0xFF4CAF50)
private val DeviceColor = Color(0xFF2196F3)
private val MysteryColor = Color(0xFF9C27B0)
private val OnceColor = Color(0xFF9E9E9E)
