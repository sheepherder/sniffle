package de.schaefer.sniffle.data

/** Display-only section — derived from entity facts, never stored in DB. */
enum class Section(val icon: String, val label: String) {
    SENSOR("📡", "Sensoren"),
    DEVICE("📱", "Geräte"),
    MYSTERY("👻", "Mystery"),
    TRANSIENT("💨", "Flüchtige"),
}
