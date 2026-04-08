package de.schaefer.sniffle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.ui.scan.DisplayDevice
import de.schaefer.sniffle.util.formatTimestamp
import kotlin.math.max
import kotlin.math.min

fun LazyListScope.deviceListContent(
    grouped: Map<Section, List<DisplayDevice>>,
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

    for (section in Section.entries) {
        val sectionItems = grouped[section] ?: continue
        if (section == Section.TRANSIENT) {
            item(key = "header_transient") {
                CollapsibleHeader(
                    section = section,
                    count = sectionItems.size,
                    summary = buildOnceSummary(sectionItems),
                    expanded = onceExpanded,
                    onToggle = onToggleOnce,
                )
            }
            if (onceExpanded) {
                items(sectionItems, key = { "transient_${it.entity.mac}" }) { device ->
                    DeviceCard(device, onDeviceTap)
                }
            }
        } else {
            item(key = "header_${section.name}") {
                SectionHeader(section, sectionItems.size)
            }
            items(sectionItems, key = { "${section.name}_${it.entity.mac}" }) { device ->
                DeviceCard(device, onDeviceTap)
            }
        }
    }

    if (grouped.isEmpty()) {
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
fun SectionHeader(section: Section, count: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(section.icon, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "${section.label} ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = section.color
        )
    }
}

@Composable
fun CollapsibleHeader(
    section: Section, count: Int, summary: String, expanded: Boolean, onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${section.label} ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = section.color
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
fun DeviceCard(
    device: DisplayDevice,
    onTap: (String) -> Unit,
) {
    val section = device.entity.section

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable { onTap(device.entity.mac) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(section.color)
            )

            val entity = device.entity
            val dn = entity.displayName

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.isLive) {
                        Text("●", color = Section.SENSOR.color, fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                    }

                    val transportTag = when (entity.transport) {
                        Transport.CLASSIC -> " (BT)"
                        Transport.BOTH -> " (BLE+BT)"
                        else -> ""
                    }
                    Text(
                        "$dn$transportTag",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )

                    if (device.rssi != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${device.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = rssiColor(device.rssi),
                        )
                    }
                }

                if (device.values.isNotEmpty()) {
                    Text(
                        device.values.entries.joinToString("  ") { (k, v) ->
                            val formatted = if (v is Double) "%.1f".format(v) else v.toString()
                            "$k $formatted"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                } else {
                    val extras = buildList {
                        entity.brand?.let { if (it != dn) add(it) }
                        entity.company?.let { if (it != entity.brand && it != dn) add(it) }
                        entity.appearance?.let { if (it != dn) add(it) }
                        if (!device.isLive && entity.latestSeenMs > 0) {
                            add("Zuletzt ${formatTimestamp(entity.latestSeenMs)}")
                        }
                    }
                    if (extras.isNotEmpty()) {
                        Text(
                            extras.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                entity.note?.let {
                    Text(
                        "📝 $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun rssiColor(rssi: Int): Color {
    val strength = max(0, min(4, (rssi + 100) / 15))
    return when {
        strength >= 3 -> Color(0xFF4CAF50)
        strength >= 2 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

fun buildOnceSummary(devices: List<DisplayDevice>): String {
    val groups = mutableMapOf<String, Int>()
    for (d in devices) {
        val entity = d.entity
        val label = when {
            entity.brand == "Apple" || entity.company == "Apple" -> "Apple"
            entity.brand == "GENERIC" && entity.model == "MS-CDP" -> "Microsoft"
            entity.company != null -> entity.company
            entity.name != null || entity.classicName != null -> "benannt"
            else -> "?"
        }
        groups[label] = (groups[label] ?: 0) + 1
    }
    return groups.entries.sortedByDescending { it.value }
        .joinToString(" • ") { "${it.value}× ${it.key}" }
}
