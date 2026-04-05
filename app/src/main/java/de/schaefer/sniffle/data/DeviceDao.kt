package de.schaefer.sniffle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Upsert
    suspend fun upsertDevice(device: DeviceEntity)

    @Insert
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("SELECT * FROM devices WHERE mac = :mac")
    suspend fun getDevice(mac: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE category != 'ONCE' ORDER BY latestSeenDate DESC")
    fun observePromotedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY latestSeenDate DESC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM sightings WHERE mac = :mac ORDER BY timestamp DESC")
    fun observeSightings(mac: String): Flow<List<SightingEntity>>

    @Query("""
        SELECT COUNT(DISTINCT date(timestamp / 1000, 'unixepoch'))
        FROM sightings WHERE mac = :mac
    """)
    suspend fun countDistinctDays(mac: String): Int

    @Query("UPDATE devices SET note = :note WHERE mac = :mac")
    suspend fun updateNote(mac: String, note: String?)

    @Query("UPDATE devices SET category = :category, notified = 0 WHERE mac = :mac")
    suspend fun updateCategory(mac: String, category: DeviceCategory)

    @Query("DELETE FROM devices WHERE mac = :mac")
    suspend fun deleteDevice(mac: String)

    @Query("""
        DELETE FROM devices
        WHERE category = 'ONCE'
        AND latestSeenDate < :cutoffDate
    """)
    suspend fun deleteStaleOnce(cutoffDate: String)

    /** Latest sighting with GPS for each device (for map). */
    @Query("""
        SELECT s.* FROM sightings s
        INNER JOIN (
            SELECT mac, MAX(timestamp) as maxTs
            FROM sightings
            WHERE latitude IS NOT NULL
            GROUP BY mac
        ) latest ON s.mac = latest.mac AND s.timestamp = latest.maxTs
        WHERE s.latitude IS NOT NULL
    """)
    fun observeLatestGeoSightings(): Flow<List<SightingEntity>>
}
