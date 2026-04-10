package de.schaefer.sniffle.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> step++ }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> step++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            0 -> {
                Text(
                    "📡 Sniffle",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Sniffle scannt Bluetooth-Geräte in deiner Umgebung " +
                    "und erkennt Sensoren, IoT-Geräte und mehr.\n\n" +
                    "Dafür braucht die App ein paar Berechtigungen.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { step = 1 }) {
                    Text("Los geht's")
                }
            }

            1 -> {
                Text("Bluetooth & Standort", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Bluetooth wird zum Scannen benötigt.\n" +
                    "Der Standort wird gespeichert, damit du " +
                    "auf der Karte siehst wo du Geräte gefunden hast.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                }) {
                    Text("Berechtigung erteilen")
                }
            }

            2 -> {
                Text("Hintergrund-Standort", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Damit der Hintergrund-Scan deinen Standort " +
                    "speichern kann, braucht die App die Berechtigung " +
                    "\"Immer erlauben\" für den Standort.\n\n" +
                    "Du kannst das auch später in den Einstellungen aktivieren.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { step = 3 }) {
                        Text("Überspringen")
                    }
                    Button(onClick = {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }) {
                        Text("Berechtigung erteilen")
                    }
                }
            }

            3 -> {
                Text("Benachrichtigungen", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sniffle benachrichtigt dich wenn neue " +
                    "Sensoren oder interessante Geräte gefunden werden.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }) {
                    Text("Berechtigung erteilen")
                }
            }

            else -> {
                LaunchedEffect(Unit) {
                    de.schaefer.sniffle.util.Preferences(context).onboardingDone = true
                    onComplete()
                }
            }
        }
    }
}

fun needsOnboarding(context: Context): Boolean {
    if (de.schaefer.sniffle.util.Preferences(context).onboardingDone) return false
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_SCAN
    ) != PackageManager.PERMISSION_GRANTED
}
