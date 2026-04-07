package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.util.formatTimestampLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mac: String,
    onBack: () -> Unit,
    onOpenMap: () -> Unit = {},
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
                title = { Text(device?.displayName ?: mac, maxLines = 1) },
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
            // Mini map — tap opens fullscreen
            val geoSightings = state.sightings.filter { it.latitude != null && it.longitude != null }
            if (geoSightings.isNotEmpty()) {
                item {
                    val miniMarkers = remember(geoSightings, device?.category) {
                        geoSightings.mapNotNull { it.toClusterMarker(device?.category) }
                    }
                    Card(
                        onClick = onOpenMap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp)
                    ) {
                        ClusterMap(
                            markers = miniMarkers,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Note field
            item {
                var showSaved by remember { mutableStateOf(false) }
                var editCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(editCount) {
                    if (editCount > 0) {
                        showSaved = true
                        delay(2000)
                        showSaved = false
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it); editCount++ },
                    label = { Text("Notiz") },
                    placeholder = { Text("z.B. Geberit im Café Stadtpark") },
                    supportingText = {
                        if (showSaved) {
                            Text("Gespeichert", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Wird automatisch gespeichert")
                        }
                    },
                    trailingIcon = if (showSaved) {
                        { Icon(Icons.Default.Check, "Gespeichert", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
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
        if (device?.name != null && device.classicName != null && device.name != device.classicName) {
            InfoRow("Name (BLE)", device.name)
            InfoRow("Name (BT)", device.classicName)
        } else {
            (device?.name ?: device?.classicName)?.let { InfoRow("Name", it) }
        }
        device?.brand?.let { InfoRow("Hersteller", it) }
        device?.model?.let { InfoRow("Modell", it) }
        device?.company?.let { InfoRow("Firma (OUI)", it) }
        device?.appearance?.let { InfoRow("Appearance", it) }
        device?.deviceType?.let { InfoRow("Typ", it) }
        device?.transport?.let {
            val label = when (it) {
                Transport.BLE -> "BLE"
                Transport.CLASSIC -> "Classic BT"
                Transport.BOTH -> "BLE + Classic BT"
            }
            InfoRow("Transport", label)
        }
        if (device != null && device.firstSeenMs != 0L) InfoRow("Erstmals", formatTimestampLong(device.firstSeenMs))
        if (device != null && device.latestSeenMs != 0L) InfoRow("Zuletzt", formatTimestampLong(device.latestSeenMs))
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
    val allParsed = remember(sightings) {
        sightings.take(50).reversed().map { it.timestamp to parseValues(it.decodedValues) }
            .filter { it.second.isNotEmpty() }
    }
    if (allParsed.isEmpty()) return

    // Collect all numeric keys across sightings
    val keys = remember(allParsed) {
        val seen = linkedSetOf<String>()
        for ((_, vals) in allParsed) {
            for ((k, v) in vals) {
                if (v.toDoubleOrNull() != null) seen.add(k)
            }
        }
        seen.toList().take(4)
    }
    if (keys.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Werte-Verlauf", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val fitDataRange = remember {
            object : CartesianLayerRangeProvider {
                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                    if (minY == maxY) minY - 1 else minY
                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                    if (minY == maxY) maxY + 1 else maxY
            }
        }

        for (key in keys) {
            val points = remember(allParsed, key) {
                allParsed.mapNotNull { (ts, vals) ->
                    vals[key]?.toDoubleOrNull()?.let { ts to it }
                }
            }
            if (points.size < 2 || points.first().first == points.last().first) continue

            Text(
                key,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )

            val modelProducer = remember(key) { CartesianChartModelProducer() }
            LaunchedEffect(points) {
                modelProducer.runTransaction {
                    lineSeries { series(x = points.map { it.first.toDouble() }, y = points.map { it.second }) }
                }
            }

            val xFormatter = remember(points) {
                CartesianValueFormatter { _, x, _ ->
                    timeFormat.format(Date(x.toLong()))
                }
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(rangeProvider = fitDataRange),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
        }
    }
}

@Composable
private fun SightingRow(sighting: SightingEntity) {
    val time = formatTimestampLong(sighting.timestamp)
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
