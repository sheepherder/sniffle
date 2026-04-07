package de.schaefer.sniffle

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import de.schaefer.sniffle.ui.navigation.SniffleApp
import de.schaefer.sniffle.ui.theme.SniffleTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO_MAC = "navigate_to_mac"
    }

    private val deepLinkMac = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            deepLinkMac.value = intent.getStringExtra(EXTRA_NAVIGATE_TO_MAC)
        }
        setContent {
            SniffleTheme {
                SniffleApp(
                    deepLinkMac = deepLinkMac.value,
                    onDeepLinkConsumed = { deepLinkMac.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkMac.value = intent.getStringExtra(EXTRA_NAVIGATE_TO_MAC)
    }
}
