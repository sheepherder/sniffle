package de.schaefer.sniffle.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import de.schaefer.sniffle.background.ScanWorker

private val INTERVALS = listOf(
    15L to "15 Minuten",
    30L to "30 Minuten",
    60L to "1 Stunde",
    120L to "2 Stunden",
    240L to "4 Stunden",
    480L to "8 Stunden",
)

private val DURATIONS = listOf(
    30_000L to "30 Sekunden",
    60_000L to "1 Minute",
    120_000L to "2 Minuten",
)

@Composable
fun SettingsScreen(onScanSettingsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sniffle_settings", Context.MODE_PRIVATE) }

    var bgEnabled by remember { mutableStateOf(prefs.getBoolean("bg_enabled", false)) }
    var intervalMin by remember { mutableLongStateOf(prefs.getLong("interval_min", 30L)) }
    var durationMs by remember { mutableLongStateOf(prefs.getLong("scan_duration_ms", 60_000L)) }
    var bleScan by remember { mutableStateOf(prefs.getBoolean("ble_scan", true)) }
    var classicScan by remember { mutableStateOf(prefs.getBoolean("classic_scan", true)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var scanSummary by remember { mutableStateOf(prefs.getBoolean("scan_summary", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))
        SectionTitle("Hintergrund-Scan")

        SettingSwitch("Hintergrund-Scan aktiv", bgEnabled) { enabled ->
            bgEnabled = enabled
            prefs.edit { putBoolean("bg_enabled", enabled) }
            if (enabled) ScanWorker.schedule(context, intervalMin) else ScanWorker.cancel(context)
        }

        if (bgEnabled) {
            Spacer(Modifier.height(8.dp))
            Text("Intervall", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegment(INTERVALS, intervalMin) { min ->
                intervalMin = min
                prefs.edit { putLong("interval_min", min) }
                ScanWorker.schedule(context, min)
            }

            Spacer(Modifier.height(12.dp))
            Text("Scan-Dauer", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegment(DURATIONS, durationMs) { ms ->
                durationMs = ms
                prefs.edit { putLong("scan_duration_ms", ms) }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Scan-Typen")
        SettingSwitch("BLE-Scan", bleScan) {
            bleScan = it
            prefs.edit { putBoolean("ble_scan", it) }
            onScanSettingsChanged()
        }
        SettingSwitch("Classic Bluetooth", classicScan) {
            classicScan = it
            prefs.edit { putBoolean("classic_scan", it) }
            onScanSettingsChanged()
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Benachrichtigungen")
        SettingSwitch("Geräte-Benachrichtigungen", notifications) {
            notifications = it
            prefs.edit { putBoolean("notifications", it) }
        }
        SettingSwitch("Scan-Zusammenfassung (leise)", scanSummary) {
            scanSummary = it
            prefs.edit { putBoolean("scan_summary", it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun <T> SingleChoiceSegment(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
