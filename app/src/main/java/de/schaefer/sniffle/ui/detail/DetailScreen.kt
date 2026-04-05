package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mac: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val device = state.device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.model ?: device?.name ?: mac, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    if (device?.category != DeviceCategory.ONCE) {
                        IconButton(onClick = { viewModel.delete() }) {
                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Mini map
            val geoSightings = state.sightings.filter { it.latitude != null && it.longitude != null }
            if (geoSightings.isNotEmpty()) {
                item {
                    MiniMap(
                        sightings = geoSightings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp)
                    )
                }
            }

            // Note field
            item {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it) },
                    label = { Text("Notiz") },
                    placeholder = { Text("z.B. Geberit im Café Stadtpark") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Device info
            item {
                DeviceInfo(device, mac)
            }

            // Sensor chart placeholder
            val sensorSightings = state.sightings.filter { !it.decodedValues.isNullOrEmpty() }
            if (sensorSightings.isNotEmpty()) {
                item {
                    SensorChart(sensorSightings)
                }
            }

            // Sighting list
            item {
                Text(
                    "Sichtungen (${state.sightings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            items(state.sightings.take(50)) { sighting ->
                SightingRow(sighting)
            }
            if (state.sightings.size > 50) {
                item {
                    Text(
                        "… und ${state.sightings.size - 50} weitere",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMap(sightings: List<SightingEntity>, modifier: Modifier) {
    val points = sightings.mapNotNull { s ->
        if (s.latitude != null && s.longitude != null) GeoPoint(s.latitude, s.longitude)
        else null
    }.distinctBy { "${it.latitude.toInt()},${it.longitude.toInt()}" }

    Card(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    if (points.isNotEmpty()) {
                        controller.setCenter(points.last())
                    }
                }
            },
            update = { map ->
                map.overlays.clear()
                for (point in points) {
                    val marker = Marker(map)
                    marker.position = point
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                }
                if (points.size > 1) {
                    val lats = points.map { it.latitude }
                    val lons = points.map { it.longitude }
                    map.zoomToBoundingBox(
                        BoundingBox(
                            lats.max(), lons.max(), lats.min(), lons.min()
                        ),
                        true, 50
                    )
                }
                map.invalidate()
            }
        )
    }
}

@Composable
private fun DeviceInfo(device: de.schaefer.sniffle.data.DeviceEntity?, mac: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Geräteinfo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val categoryLabel = when (device?.category) {
            DeviceCategory.SENSOR -> "📡 Sensor"
            DeviceCategory.DEVICE -> "📱 Gerät"
            DeviceCategory.MYSTERY -> "👻 Mystery"
            DeviceCategory.ONCE -> "💨 Flüchtig"
            null -> "?"
        }

        InfoRow("Kategorie", categoryLabel)
        InfoRow("MAC", mac)
        device?.brand?.let { InfoRow("Hersteller", it) }
        device?.model?.let { InfoRow("Modell", it) }
        device?.company?.let { InfoRow("Firma (OUI)", it) }
        device?.appearance?.let { InfoRow("Appearance", it) }
        device?.deviceType?.let { InfoRow("Typ", it) }
        device?.transport?.let { InfoRow("Transport", it.name) }
        device?.firstSeenDate?.let { InfoRow("Erstmals", it) }
        device?.latestSeenDate?.let { InfoRow("Zuletzt", it) }
        device?.modelId?.let { InfoRow("Model ID", it) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SensorChart(sightings: List<SightingEntity>) {
    // Parse first sighting to get available keys
    val firstValues = parseValues(sightings.first().decodedValues)
    if (firstValues.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Werte-Verlauf", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Simple text-based chart for now — Vico integration in a later pass
        val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.GERMAN)
        val recentSightings = sightings.take(20).reversed()

        for (key in firstValues.keys.take(4)) {
            Text(
                key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            for (s in recentSightings) {
                val vals = parseValues(s.decodedValues)
                val v = vals[key] ?: continue
                val time = dateFormat.format(Date(s.timestamp))
                Text(
                    "$time  $v",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SightingRow(sighting: SightingEntity) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN) }
    val time = dateFormat.format(Date(sighting.timestamp))
    val values = parseValues(sighting.decodedValues)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(130.dp),
        )
        Text(
            "${sighting.rssi}dBm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        if (values.isNotEmpty()) {
            Text(
                values.entries.joinToString("  ") { "${it.key}=${it.value}" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (sighting.latitude != null) {
            Text(
                "📍",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun parseValues(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    return try {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        obj.mapValues { it.value.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyMap()
    }
}
