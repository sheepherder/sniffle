package de.schaefer.sniffle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.schaefer.sniffle.ui.navigation.SniffleApp
import de.schaefer.sniffle.ui.theme.SniffleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SniffleTheme {
                SniffleApp()
            }
        }
    }
}
