package de.schaefer.sniffle.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeviceCategory { SENSOR, DEVICE, MYSTERY, ONCE }
enum class Transport { BLE, CLASSIC }

@Entity(
    tableName = "devices",
    indices = [Index("category"), Index("latestSeenDate")]
)
data class DeviceEntity(
    @PrimaryKey val mac: String,
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val modelId: String? = null,
    val deviceType: String? = null,
    val transport: Transport = Transport.BLE,
    val category: DeviceCategory = DeviceCategory.ONCE,
    val appearance: String? = null,
    val company: String? = null,
    val firstSeenDate: String = "",
    val latestSeenDate: String = "",
    val note: String? = null,
    val notified: Boolean = false,
)
