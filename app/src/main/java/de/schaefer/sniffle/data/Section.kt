package de.schaefer.sniffle.data

import androidx.compose.ui.graphics.Color

/** Display-only section — derived from entity facts, never stored in DB. */
enum class Section(val icon: String, val label: String, val color: Color) {
    SENSOR("📡", "Sensoren", Color(0xFF4CAF50)),
    DEVICE("📱", "Geräte", Color(0xFF2196F3)),
    MYSTERY("👻", "Mystery", Color(0xFF9C27B0)),
    TRANSIENT("💨", "Flüchtige", Color(0xFF9E9E9E)),
}
