package de.schaefer.sniffle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.util.formatTimestamp
import kotlin.math.max
import kotlin.math.min

val SensorColor = Color(0xFF4CAF50)
val DeviceColor = Color(0xFF2196F3)
val MysteryColor = Color(0xFF9C27B0)
val OnceColor = Color(0xFF9E9E9E)

data class CategoryInfo(val icon: String, val label: String, val color: Color)

val CATEGORIES = mapOf(
    DeviceCategory.SENSOR to CategoryInfo("📡", "Sensoren", SensorColor),
    DeviceCategory.DEVICE to CategoryInfo("📱", "Geräte", DeviceColor),
    DeviceCategory.MYSTERY to CategoryInfo("👻", "Mystery", MysteryColor),
    DeviceCategory.ONCE to CategoryInfo("💨", "Flüchtige", OnceColor),
)

/**
 * Renders a full 4-category device list into a LazyListScope.
 * Used by both LiveScreen and HistoryScreen.
 *
 * @param liveMacs set of MACs currently live (for live indicator)
 * @param showRssi whether to show RSSI bars (live) or last-seen time (history)
 */
fun LazyListScope.deviceListContent(
    sensors: List<DeviceEntity>,
    devices: List<DeviceEntity>,
    mystery: List<DeviceEntity>,
    once: List<DeviceEntity>,
    liveMacs: Set<String>,
    showRssi: Boolean,
    liveRssi: Map<String, Int>,
    liveValues: Map<String, Map<String, Any>>,
    onceExpanded: Boolean,
    onToggleOnce: () -> Unit,
    onDeviceTap: (String) -> Unit,
    emptyText: String,
    statusLine: String? = null,
) {
    if (statusLine != null) {
        item(key = "status") {
            Text(
                statusLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }

    for (category in listOf(DeviceCategory.SENSOR, DeviceCategory.DEVICE, DeviceCategory.MYSTERY)) {
        val list = when (category) {
            DeviceCategory.SENSOR -> sensors
            DeviceCategory.DEVICE -> devices
            DeviceCategory.MYSTERY -> mystery
            else -> emptyList()
        }
        if (list.isNotEmpty()) {
            val info = CATEGORIES[category]!!
            item(key = "header_$category") { CategoryHeader(info.icon, info.label, list.size, info.color) }
            items(list, key = { "${category}_${it.mac}" }) { device ->
                DeviceRow(
                    device = device,
                    isLive = device.mac in liveMacs,
                    showRssi = showRssi,
                    rssi = liveRssi[device.mac],
                    values = liveValues[device.mac],
                    onTap = onDeviceTap,
                )
            }
        }
    }

    if (once.isNotEmpty()) {
        item(key = "header_once") {
            CollapsibleHeader(
                count = once.size,
                summary = buildOnceSummary(once),
                expanded = onceExpanded,
                onToggle = onToggleOnce,
            )
        }
        if (onceExpanded) {
            items(once, key = { "once_${it.mac}" }) { device ->
                DeviceRow(
                    device = device,
                    isLive = device.mac in liveMacs,
                    showRssi = showRssi,
                    rssi = liveRssi[device.mac],
                    values = liveValues[device.mac],
                    onTap = onDeviceTap,
                )
            }
        }
    }

    val total = sensors.size + devices.size + mystery.size + once.size
    if (total == 0) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(emptyText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun CategoryHeader(icon: String, label: String, count: Int, color: Color) {
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
fun CollapsibleHeader(
    count: Int, summary: String, expanded: Boolean, onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💨", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Flüchtige ($count)",
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
fun DeviceRow(
    device: DeviceEntity,
    isLive: Boolean,
    showRssi: Boolean,
    rssi: Int?,
    values: Map<String, Any>?,
    onTap: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(device.mac) }
            .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showRssi && rssi != null) {
                RssiBar(rssi)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${rssi}dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(Modifier.width(4.dp))
            }

            if (isLive && !showRssi) {
                Text("●", color = SensorColor, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
            }

            val title = device.model ?: device.name ?: device.mac
            val transportTag = if (device.transport == Transport.CLASSIC) " (BT)" else ""
            Text(
                "$title$transportTag",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        // Sensor values (from live data)
        val displayValues = values ?: emptyMap()
        if (displayValues.isNotEmpty()) {
            Text(
                displayValues.entries.joinToString("  ") { (k, v) ->
                    val formatted = if (v is Double) "%.1f".format(v) else v.toString()
                    "$k $formatted"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (showRssi) 72.dp else 20.dp),
                maxLines = 2,
            )
        }

        // Extra info line
        val extras = buildList {
            device.brand?.let { add(it) }
            device.company?.let { if (it != device.brand) add(it) }
            device.appearance?.let { add(it) }
            if (!showRssi && device.latestSeenMs > 0) {
                add("Zuletzt ${formatTimestamp(device.latestSeenMs)}")
            }
        }
        if (extras.isNotEmpty() && displayValues.isEmpty()) {
            Text(
                extras.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (showRssi) 72.dp else 20.dp),
            )
        }

        device.note?.let {
            Text(
                "📝 $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = if (showRssi) 72.dp else 20.dp),
            )
        }
    }
}

@Composable
fun RssiBar(rssi: Int) {
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

fun buildOnceSummary(devices: List<DeviceEntity>): String {
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

