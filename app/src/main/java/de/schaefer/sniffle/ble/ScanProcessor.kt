package de.schaefer.sniffle.ble

import de.schaefer.sniffle.classify.AppearanceResolver
import de.schaefer.sniffle.classify.DeviceClassifier
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.classify.ServiceUuidResolver
import de.schaefer.sniffle.data.DeviceDao
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.decoder.DecodedDevice
import de.schaefer.sniffle.decoder.DecoderChain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

data class ProcessedDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val hasSensorData: Boolean,
    val promoted: Boolean,
    val brand: String?,
    val model: String?,
    val type: String?,
    val values: Map<String, Any> = emptyMap(),
    val company: String?,
    val appearance: String?,
    val serviceHints: List<String> = emptyList(),
    val transport: Transport = Transport.BLE,
    val guessedType: String? = null,
    val wasPromoted: Boolean = false,
)

class ScanProcessor(
    private val dao: DeviceDao,
    @Volatile internal var latitude: Double? = null,
    @Volatile internal var longitude: Double? = null,
    private val persistIntervalMs: Long = 30_000L,
    private val onNotify: (suspend (device: DeviceEntity, values: String?) -> Unit)? = null,
) {
    val newDeviceCount = AtomicInteger()
    val sensorCount = AtomicInteger()
    val uniqueCount: Int get() = knownDevices.size

    private val knownDevices = mutableMapOf<String, DeviceEntity?>()
    private val lastPersistedAt = mutableMapOf<String, Long>()
    private val loadMutex = Mutex()

    private fun mergeTransport(existing: DeviceEntity?, scanTransport: Transport): Transport =
        when {
            existing?.transport == Transport.BOTH -> Transport.BOTH
            existing?.transport == Transport.CLASSIC && scanTransport == Transport.BLE -> Transport.BOTH
            existing?.transport == Transport.BLE && scanTransport == Transport.CLASSIC -> Transport.BOTH
            else -> scanTransport
        }

    suspend fun processBle(advert: ParsedAdvert): ProcessedDevice {
        val existing = getOrLoadDevice(advert.mac)
        val decoded = DecoderChain.decode(advert)
        val ouiVendor = if (existing?.company != null) null else OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = if (existing?.appearance != null) null else AppearanceResolver.resolve(advert.appearance)
        val serviceHints = if (existing != null) emptyList() else ServiceUuidResolver.resolve(advert.serviceUuids)
        val guessedType = if (existing != null) null else DeviceClassifier.guessTypeFromName(advert.name)
        val hasSensorData = resolveHasSensorData(existing, decoded)
        val nowMs = System.currentTimeMillis()
        val transport = mergeTransport(existing, Transport.BLE)

        val merged = DeviceEntity(
            mac = advert.mac,
            name = advert.name ?: existing?.name,
            classicName = existing?.classicName,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            modelId = decoded?.modelId ?: existing?.modelId,
            deviceType = decoded?.type ?: existing?.deviceType,
            transport = transport,
            hasSensorData = hasSensorData,
            promoted = existing?.promoted ?: false,
            appearance = appearance ?: existing?.appearance,
            company = company ?: existing?.company,
            firstSeenMs = existing?.firstSeenMs ?: nowMs,
            latestSeenMs = nowMs,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )

        if (existing == null && decoded?.hasSensorData == true) sensorCount.incrementAndGet()
        if (existing == null && hasSensorData) newDeviceCount.incrementAndGet()

        val valuesJson by lazy {
            decoded?.values?.takeIf { it.isNotEmpty() }?.let { vals ->
                try { Json.encodeToString(vals.mapValues { it.value.toString() }) } catch (_: Exception) { null }
            }
        }
        val (finalEntity, wasPromoted) = persistAndPromote(advert.mac, merged, existing == null, nowMs, { valuesJson }) {
            persistSighting(advert.mac, advert.rssi, valuesJson)
        }

        return ProcessedDevice(
            mac = advert.mac, name = finalEntity.name ?: finalEntity.classicName, rssi = advert.rssi,
            hasSensorData = hasSensorData, promoted = finalEntity.promoted,
            brand = finalEntity.brand, model = finalEntity.model,
            type = finalEntity.deviceType, values = decoded?.values ?: emptyMap(),
            company = finalEntity.company, appearance = finalEntity.appearance,
            serviceHints = serviceHints, transport = transport,
            guessedType = guessedType, wasPromoted = wasPromoted,
        )
    }

    suspend fun processClassic(device: ClassicDevice): ProcessedDevice {
        val existing = getOrLoadDevice(device.mac)
        val nowMs = System.currentTimeMillis()
        val company = if (existing?.company != null) existing.company else OuiLookup.lookup(device.mac)
        val className = device.deviceClassName

        val transport = mergeTransport(existing, Transport.CLASSIC)

        val merged = DeviceEntity(
            mac = device.mac,
            name = existing?.name,
            classicName = device.name ?: existing?.classicName,
            brand = existing?.brand,
            model = existing?.model ?: className,
            modelId = existing?.modelId,
            deviceType = existing?.deviceType,
            transport = transport,
            hasSensorData = existing?.hasSensorData ?: false,
            promoted = existing?.promoted ?: false,
            appearance = existing?.appearance ?: className,
            company = company,
            firstSeenMs = existing?.firstSeenMs ?: nowMs,
            latestSeenMs = nowMs,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )

        val (finalEntity, wasPromoted) = persistAndPromote(device.mac, merged, existing == null, nowMs, { null }) {
            persistSighting(device.mac, device.rssi, null)
        }

        return ProcessedDevice(
            mac = device.mac, name = finalEntity.name ?: finalEntity.classicName, rssi = device.rssi,
            hasSensorData = finalEntity.hasSensorData, promoted = finalEntity.promoted,
            brand = finalEntity.brand, model = finalEntity.model,
            type = finalEntity.deviceType, values = emptyMap(), company = company,
            appearance = finalEntity.appearance, serviceHints = emptyList(),
            transport = transport,
            guessedType = DeviceClassifier.guessTypeFromName(device.name),
            wasPromoted = wasPromoted,
        )
    }

    private suspend fun persistAndPromote(
        mac: String, merged: DeviceEntity, isNew: Boolean, nowMs: Long,
        encodeValues: () -> String?,
        sighting: suspend () -> Unit,
    ): Pair<DeviceEntity, Boolean> {
        var wasPromoted = false
        if (shouldPersist(mac, nowMs, isNew)) {
            persistDevice(merged, isNew)
            sighting()
            if (!merged.promoted && !merged.hasSensorData) {
                wasPromoted = checkPromotion(mac, nowMs)
                if (wasPromoted) newDeviceCount.incrementAndGet()
            }
        }
        val finalEntity = if (wasPromoted) merged.copy(promoted = true) else merged

        // Notify for new sensors or newly promoted devices (once per device)
        val shouldMark = wasPromoted || (merged.hasSensorData && !merged.notified)
        if (shouldMark) {
            dao.markNotified(mac)
            onNotify?.invoke(finalEntity, encodeValues())
        }

        knownDevices[mac] = if (shouldMark) finalEntity.copy(notified = true) else finalEntity
        return finalEntity to wasPromoted
    }

    private suspend fun persistDevice(entity: DeviceEntity, isNew: Boolean) {
        if (isNew) {
            val inserted = dao.insertDevice(entity)
            if (inserted != -1L) return
        }
        val updated = dao.updateFromScan(
            mac = entity.mac, name = entity.name, classicName = entity.classicName,
            brand = entity.brand, model = entity.model, modelId = entity.modelId,
            deviceType = entity.deviceType, transport = entity.transport,
            hasSensorData = entity.hasSensorData, promoted = entity.promoted,
            appearance = entity.appearance, company = entity.company,
            latestSeenMs = entity.latestSeenMs,
        )
        if (updated == 0) {
            dao.insertDevice(entity)
        }
    }

    private fun shouldPersist(mac: String, now: Long, isNew: Boolean): Boolean {
        if (isNew) {
            lastPersistedAt[mac] = now
            return true
        }
        val last = lastPersistedAt[mac] ?: 0L
        if (now - last >= persistIntervalMs) {
            lastPersistedAt[mac] = now
            return true
        }
        return false
    }

    private suspend fun getOrLoadDevice(mac: String): DeviceEntity? {
        if (mac in knownDevices) return knownDevices[mac]
        return loadMutex.withLock {
            if (mac in knownDevices) return knownDevices[mac]
            dao.getDevice(mac).also { knownDevices[mac] = it }
        }
    }

    private companion object {
        const val PROMOTION_COUNT = 4
        const val PROMOTION_INTERVAL_MS = 1L * 60 * 60 * 1000
    }

    private suspend fun checkPromotion(mac: String, nowMs: Long): Boolean {
        val prior = dao.countPriorSightings(
            mac, nowMs, PROMOTION_INTERVAL_MS, PROMOTION_COUNT - 1,
        )
        if (prior < PROMOTION_COUNT - 1) return false
        dao.setPromoted(mac)
        return true
    }

    private fun resolveHasSensorData(
        existing: DeviceEntity?, decoded: DecodedDevice?,
    ): Boolean = existing?.hasSensorData == true || DeviceClassifier.hasSensorData(decoded)

    private suspend fun persistSighting(mac: String, rssi: Int, valuesJson: String?) {
        try {
            dao.insertSighting(SightingEntity(
                mac = mac, timestamp = System.currentTimeMillis(),
                latitude = latitude, longitude = longitude,
                rssi = rssi, decodedValues = valuesJson,
            ))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {}
    }
}
