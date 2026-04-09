package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.ui.map.ClusterMap
import de.schaefer.sniffle.util.formatTimestampLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mac: String,
    onBack: () -> Unit,
    onOpenMap: () -> Unit = {},
    onOpenChart: (key: String) -> Unit = {},
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
                    if (device?.section != Section.TRANSIENT) {
                        IconButton(onClick = { viewModel.delete() }) {
                            Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val focusManager = LocalFocusManager.current
        val sensorSightings = remember(state.sightings) {
            state.sightings.filter { !it.decodedValues.isNullOrEmpty() }
        }
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
                    val miniMarkers = remember(geoSightings, device?.section) {
                        geoSightings.mapNotNull { it.toClusterMarker(device?.section) }
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
            item(key = "note") {
                var showSaved by remember { mutableStateOf(false) }
                var editCount by remember { mutableIntStateOf(0) }
                var isFocused by remember { mutableStateOf(false) }

                LaunchedEffect(editCount) {
                    if (editCount > 0) {
                        showSaved = true
                        delay(2000)
                        showSaved = false
                    }
                }

                val trailingIcon: (@Composable () -> Unit)? = remember(showSaved, isFocused) {
                    when {
                        showSaved -> {{ Icon(Icons.Default.Check, "Gespeichert", tint = MaterialTheme.colorScheme.primary) }}
                        isFocused -> {{ IconButton(onClick = { focusManager.clearFocus() }) {
                            Icon(Icons.Default.Close, "Schließen")
                        } }}
                        else -> null
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it); editCount++ },
                    label = { Text("Notiz") },
                    placeholder = { Text("Eigene Notiz hinzufügen…") },
                    supportingText = {
                        if (showSaved) {
                            Text("Gespeichert", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Wird automatisch gespeichert")
                        }
                    },
                    trailingIcon = trailingIcon,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            // Device info
            item {
                DeviceInfo(device, mac)
            }

            // Sensor charts
            if (sensorSightings.isNotEmpty()) {
                item {
                    SensorCharts(
                        sightings = sensorSightings,
                        onOpenChart = onOpenChart,
                    )
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

        val section = device?.section
        val sectionLabel = if (section != null) "${section.icon} ${section.label}" else "?"
        InfoRow("Kategorie", sectionLabel)
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
