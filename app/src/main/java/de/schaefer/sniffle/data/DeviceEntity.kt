package de.schaefer.sniffle.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeviceCategory { SENSOR, DEVICE, MYSTERY, ONCE }
enum class Transport { BLE, CLASSIC, BOTH }

val Transport.includesBle: Boolean get() = this == Transport.BLE || this == Transport.BOTH
val Transport.includesClassic: Boolean get() = this == Transport.CLASSIC || this == Transport.BOTH

data class NoteEntry(val mac: String, val note: String)

@Entity(
    tableName = "devices",
    indices = [Index("category"), Index("latestSeenMs")]
)
data class DeviceEntity(
    @PrimaryKey val mac: String,
    val name: String? = null,          // BLE advertised name
    val classicName: String? = null,   // Classic BT device name
    val brand: String? = null,
    val model: String? = null,
    val modelId: String? = null,
    val deviceType: String? = null,
    val transport: Transport = Transport.BLE,
    val category: DeviceCategory = DeviceCategory.ONCE,
    val appearance: String? = null,
    val company: String? = null,
    val firstSeenMs: Long = 0L,
    val latestSeenMs: Long = 0L,
    val note: String? = null,
    val notified: Boolean = false,
) {
    val displayName: String get() = model ?: name ?: classicName ?: mac
}
