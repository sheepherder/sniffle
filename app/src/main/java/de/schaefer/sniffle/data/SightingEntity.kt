package de.schaefer.sniffle.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sightings",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["mac"],
        childColumns = ["mac"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("timestamp"), Index("mac", "timestamp")]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mac: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rssi: Int,
    val decodedValues: String? = null,
)
