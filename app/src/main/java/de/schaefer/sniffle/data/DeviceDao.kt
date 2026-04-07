package de.schaefer.sniffle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    /** Returns -1 if row already exists (conflict ignored). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevice(device: DeviceEntity): Long

    /** Update scan-related fields only, leaving note and notified untouched. Uses COALESCE to never overwrite existing data with null. */
    @Query("""
        UPDATE devices SET
            name = COALESCE(:name, name),
            classicName = COALESCE(:classicName, classicName),
            brand = COALESCE(:brand, brand),
            model = COALESCE(:model, model),
            modelId = COALESCE(:modelId, modelId),
            deviceType = COALESCE(:deviceType, deviceType),
            transport = :transport,
            category = :category,
            appearance = COALESCE(:appearance, appearance),
            company = COALESCE(:company, company),
            latestSeenMs = :latestSeenMs
        WHERE mac = :mac
    """)
    suspend fun updateFromScan(
        mac: String, name: String?, classicName: String?,
        brand: String?, model: String?, modelId: String?,
        deviceType: String?, transport: Transport, category: DeviceCategory,
        appearance: String?, company: String?,
        latestSeenMs: Long,
    ): Int

    @Insert
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("SELECT * FROM devices WHERE mac = :mac")
    suspend fun getDevice(mac: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE mac = :mac")
    fun observeDevice(mac: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE category != 'ONCE' ORDER BY latestSeenMs DESC")
    fun observePromotedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY latestSeenMs DESC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM sightings WHERE mac = :mac ORDER BY timestamp DESC")
    fun observeSightings(mac: String): Flow<List<SightingEntity>>

    @Query("""
        SELECT COUNT(DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime'))
        FROM sightings WHERE mac = :mac
    """)
    suspend fun countDistinctDays(mac: String): Int

    @Query("UPDATE devices SET note = :note WHERE mac = :mac")
    suspend fun updateNote(mac: String, note: String?)

    @Query("SELECT mac, note FROM devices WHERE note IS NOT NULL")
    fun observeNotes(): Flow<List<NoteEntry>>

    @Query("UPDATE devices SET category = :category, notified = 0 WHERE mac = :mac")
    suspend fun updateCategory(mac: String, category: DeviceCategory)

    @Query("DELETE FROM devices WHERE mac = :mac")
    suspend fun deleteDevice(mac: String)

    @Query("""
        DELETE FROM devices
        WHERE category = 'ONCE'
        AND latestSeenMs < :cutoffMs
    """)
    suspend fun deleteStaleOnce(cutoffMs: Long)

    /** All sightings with GPS (for map "Alle" mode). */
    @Query("""
        SELECT s.* FROM sightings s
        INNER JOIN devices d ON s.mac = d.mac
        WHERE s.latitude IS NOT NULL AND d.category != 'ONCE'
        ORDER BY s.timestamp DESC
    """)
    fun observeAllGeoSightings(): Flow<List<SightingEntity>>

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
