package de.schaefer.sniffle.ui.theme

import androidx.compose.ui.graphics.Color
import de.schaefer.sniffle.data.Section

val Section.color: Color get() = when (this) {
    Section.SENSOR -> Color(0xFF4CAF50)
    Section.DEVICE -> Color(0xFFFF9800)
    Section.MYSTERY -> Color(0xFF9C27B0)
    Section.TRANSIENT -> Color(0xFF9E9E9E)
}
