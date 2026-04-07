package de.schaefer.sniffle.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.schaefer.sniffle.background.ScanWorker
import de.schaefer.sniffle.util.Preferences
import de.schaefer.sniffle.util.formatTimestamp

private val INTERVALS = listOf(
    15L to "15 Minuten", 30L to "30 Minuten", 60L to "1 Stunde",
    120L to "2 Stunden", 240L to "4 Stunden", 480L to "8 Stunden",
)

private val DURATIONS = listOf(
    30_000L to "30 Sekunden", 60_000L to "1 Minute", 120_000L to "2 Minuten",
)

@Composable
fun SettingsScreen(onScanSettingsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }

    var bgEnabled by remember { mutableStateOf(prefs.bgEnabled) }
    var intervalMin by remember { mutableLongStateOf(prefs.intervalMin) }
    var durationMs by remember { mutableLongStateOf(prefs.scanDurationMs) }
    var bleScan by remember { mutableStateOf(prefs.bleScan) }
    var classicScan by remember { mutableStateOf(prefs.classicScan) }
    var notifications by remember { mutableStateOf(prefs.notifications) }
    var scanSummary by remember { mutableStateOf(prefs.scanSummary) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Änderungen werden sofort übernommen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        SectionTitle("Hintergrund-Scan")

        SettingSwitch("Hintergrund-Scan aktiv", bgEnabled) { enabled ->
            bgEnabled = enabled; prefs.bgEnabled = enabled
            if (enabled) ScanWorker.schedule(context, intervalMin) else ScanWorker.cancel(context)
        }

        if (bgEnabled) {
            BgScanStatus(prefs)
            BatteryOptimizationHint()
            SettingDropdown("Intervall", INTERVALS, intervalMin) { min ->
                intervalMin = min; prefs.intervalMin = min
                ScanWorker.schedule(context, min)
            }
            SettingDropdown("Scan-Dauer", DURATIONS, durationMs) { ms ->
                durationMs = ms; prefs.scanDurationMs = ms
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Scan-Typen")
        SettingSwitch("BLE-Scan", bleScan) {
            bleScan = it; prefs.bleScan = it; onScanSettingsChanged()
        }
        SettingSwitch("Classic Bluetooth", classicScan) {
            classicScan = it; prefs.classicScan = it; onScanSettingsChanged()
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Benachrichtigungen")
        SettingSwitch("Geräte-Benachrichtigungen", notifications) {
            notifications = it; prefs.notifications = it
        }
        SettingSwitch("Scan-Zusammenfassung anzeigen", scanSummary) {
            scanSummary = it; prefs.scanSummary = it
        }
    }
}

@Composable
private fun BatteryOptimizationHint() {
    val context = LocalContext.current
    var isIgnoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Re-check when returning from system settings
    LifecycleResumeEffect(Unit) {
        isIgnoring = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose {}
    }

    if (!isIgnoring) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Akku-Optimierung aktiv",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Android kann den Hintergrund-Scan verzögern oder stoppen. " +
                    "Deaktiviere die Akku-Optimierung für zuverlässige Scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ))
                }) {
                    Text("Akku-Optimierung deaktivieren")
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun BgScanStatus(prefs: Preferences) {
    var lastMs by remember { mutableLongStateOf(prefs.lastBgScanMs) }
    var summary by remember { mutableStateOf(prefs.lastBgScanSummary) }

    LifecycleResumeEffect(Unit) {
        lastMs = prefs.lastBgScanMs
        summary = prefs.lastBgScanSummary
        onPauseOrDispose {}
    }

    val text = if (lastMs > 0) {
        "Letzter Scan: ${formatTimestamp(lastMs)} — ${summary ?: ""}"
    } else {
        "Noch kein Hintergrund-Scan gelaufen"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingDropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: ""

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).width(180.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for ((value, text) in options) {
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { onSelect(value); expanded = false },
                    )
                }
            }
        }
    }
}
