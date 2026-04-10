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
            hasSensorData = :hasSensorData,
            promoted = :promoted,
            appearance = COALESCE(:appearance, appearance),
            company = COALESCE(:company, company),
            latestSeenMs = :latestSeenMs
        WHERE mac = :mac
    """)
    suspend fun updateFromScan(
        mac: String, name: String?, classicName: String?,
        brand: String?, model: String?, modelId: String?,
        deviceType: String?, transport: Transport, hasSensorData: Boolean, promoted: Boolean,
        appearance: String?, company: String?,
        latestSeenMs: Long,
    ): Int

    @Insert
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("SELECT * FROM devices WHERE mac = :mac")
    suspend fun getDevice(mac: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE mac = :mac")
    fun observeDevice(mac: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE hasSensorData = 1 OR promoted = 1 ORDER BY latestSeenMs DESC")
    fun observePromotedDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY latestSeenMs DESC")
    fun observeAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM sightings WHERE mac = :mac ORDER BY timestamp DESC")
    fun observeSightings(mac: String): Flow<List<SightingEntity>>

    /**
     * Counts how many prior sightings exist that are each at least [intervalMs] apart,
     * starting from [nowMs] (the current sighting, not yet counted).
     *
     * Uses a recursive CTE that walks backwards through sightings:
     * 1. Find the latest sighting ≥ [intervalMs] before [nowMs]
     * 2. From that, find the latest sighting ≥ [intervalMs] before it
     * 3. Repeat up to [remaining] times (early stop — we don't need more)
     *
     * Returns 0..[remaining]. Promotion triggers when result ≥ [remaining].
     * Each step is a single index lookup on (mac, timestamp) — O(remaining), not O(rows).
     */
    @Query("""
        WITH RECURSIVE chain(ts, depth) AS (
            SELECT MAX(timestamp), 1 FROM sightings
            WHERE mac = :mac AND timestamp <= :nowMs - :intervalMs
            UNION ALL
            SELECT (SELECT MAX(timestamp) FROM sightings
                    WHERE mac = :mac AND timestamp <= chain.ts - :intervalMs),
                   depth + 1
            FROM chain WHERE depth < :remaining AND ts IS NOT NULL
        )
        SELECT COALESCE(MAX(depth), 0) FROM chain WHERE ts IS NOT NULL
    """)
    suspend fun countPriorSightings(mac: String, nowMs: Long, intervalMs: Long, remaining: Int): Int

    @Query("UPDATE devices SET note = :note WHERE mac = :mac")
    suspend fun updateNote(mac: String, note: String?)

    @Query("SELECT mac, note FROM devices WHERE note IS NOT NULL")
    fun observeNotes(): Flow<List<NoteEntry>>

    @Query("UPDATE devices SET promoted = 1, notified = 0 WHERE mac = :mac")
    suspend fun setPromoted(mac: String)

    @Query("UPDATE devices SET notified = 1 WHERE mac = :mac")
    suspend fun markNotified(mac: String)

    @Query("DELETE FROM devices WHERE mac = :mac")
    suspend fun deleteDevice(mac: String)

    @Query("""
        DELETE FROM devices
        WHERE hasSensorData = 0 AND promoted = 0
        AND latestSeenMs < :cutoffMs
    """)
    suspend fun deleteStaleOnce(cutoffMs: Long)

    /** All sightings with GPS (for map "Alle" mode). */
    @Query("""
        SELECT s.* FROM sightings s
        INNER JOIN devices d ON s.mac = d.mac
        WHERE s.latitude IS NOT NULL AND (d.hasSensorData = 1 OR d.promoted = 1)
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

private const val STALE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

suspend fun DeviceDao.deleteStale() =
    deleteStaleOnce(System.currentTimeMillis() - STALE_RETENTION_MS)
