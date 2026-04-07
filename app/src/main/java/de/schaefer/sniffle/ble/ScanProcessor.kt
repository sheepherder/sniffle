package de.schaefer.sniffle.ble

import de.schaefer.sniffle.classify.AppearanceResolver
import de.schaefer.sniffle.classify.DeviceClassifier
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.classify.ServiceUuidResolver
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceDao
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.decoder.DecodedDevice
import de.schaefer.sniffle.decoder.DecoderChain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

data class ProcessedDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val category: DeviceCategory,
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

/**
 * Shared scan processing pipeline.
 *
 * Throttles DB writes: each device is persisted at most once per [persistIntervalMs].
 * Live UI updates come from the returned [ProcessedDevice] in memory, not from DB.
 */
class ScanProcessor(
    private val dao: DeviceDao,
    @Volatile var latitude: Double? = null,
    @Volatile var longitude: Double? = null,
    private val persistIntervalMs: Long = 30_000L,
) {
    val newDeviceCount = AtomicInteger()
    val sensorCount = AtomicInteger()
    val uniqueCount: Int get() = knownDevices.size

    /** Accumulated device state per MAC — loaded from DB on first encounter, then updated in-memory. */
    private val knownDevices = mutableMapOf<String, DeviceEntity?>()
    private val lastPersistedAt = mutableMapOf<String, Long>()

    suspend fun processBle(advert: ParsedAdvert): ProcessedDevice {
        val existing = getOrLoadDevice(advert.mac)
        val decoded = DecoderChain.decode(advert)
        val ouiVendor = if (existing?.company != null) null else OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = if (existing?.appearance != null) null else AppearanceResolver.resolve(advert.appearance)
        val serviceHints = if (existing != null) emptyList() else ServiceUuidResolver.resolve(advert.serviceUuids)
        val guessedType = if (existing != null) null else DeviceClassifier.guessTypeFromName(advert.name)
        val category = resolveCategory(existing?.category, advert, decoded)
        val nowMs = System.currentTimeMillis()

        val transport = when (existing?.transport) {
            Transport.CLASSIC, Transport.BOTH -> Transport.BOTH
            else -> Transport.BLE
        }

        // Merged view: current scan data + previously known data (for live UI)
        val merged = DeviceEntity(
            mac = advert.mac,
            name = advert.name ?: existing?.name,
            classicName = existing?.classicName,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            modelId = decoded?.modelId ?: existing?.modelId,
            deviceType = decoded?.type ?: existing?.deviceType,
            transport = transport,
            category = category,
            appearance = appearance ?: existing?.appearance,
            company = company ?: existing?.company,
            firstSeenMs = existing?.firstSeenMs ?: nowMs,
            latestSeenMs = nowMs,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )

        if (decoded?.hasSensorData == true) sensorCount.incrementAndGet()

        var finalCategory = category
        var wasPromoted = false
        val isNew = existing == null
        if (shouldPersist(advert.mac, nowMs, isNew)) {
            persistDevice(merged, isNew)
            persistSighting(advert.mac, advert.rssi, decoded)
            if (isNew && category == DeviceCategory.SENSOR) newDeviceCount.incrementAndGet()

            val (promoted, did) = checkPromotion(
                mac = advert.mac, currentCategory = category,
                name = merged.name ?: merged.classicName, ouiVendor = ouiVendor, company = company,
                appearance = appearance, serviceHints = serviceHints, deviceClassName = null,
            )
            finalCategory = promoted
            wasPromoted = did
        }

        knownDevices[advert.mac] = if (wasPromoted) merged.copy(category = finalCategory) else merged

        return ProcessedDevice(
            mac = advert.mac, name = merged.name ?: merged.classicName, rssi = advert.rssi,
            category = finalCategory, brand = merged.brand, model = merged.model,
            type = merged.deviceType, values = decoded?.values ?: emptyMap(),
            company = merged.company, appearance = merged.appearance,
            serviceHints = serviceHints, transport = transport,
            guessedType = guessedType, wasPromoted = wasPromoted,
        )
    }

    suspend fun processClassic(device: ClassicDevice): ProcessedDevice {
        val existing = getOrLoadDevice(device.mac)
        val category = existing?.category ?: DeviceCategory.ONCE
        val nowMs = System.currentTimeMillis()
        val company = OuiLookup.lookup(device.mac)
        val className = device.deviceClassName

        val transport = when (existing?.transport) {
            Transport.BLE, Transport.BOTH -> Transport.BOTH
            else -> Transport.CLASSIC
        }

        val merged = DeviceEntity(
            mac = device.mac,
            name = existing?.name,
            classicName = device.name ?: existing?.classicName,
            brand = existing?.brand,
            model = className ?: existing?.model,
            modelId = existing?.modelId,
            deviceType = existing?.deviceType,
            transport = transport,
            category = category,
            appearance = className,
            company = company,
            firstSeenMs = existing?.firstSeenMs ?: nowMs,
            latestSeenMs = nowMs,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )


        var finalCategory = category
        var wasPromoted = false
        val isNew = existing == null
        if (shouldPersist(device.mac, nowMs, isNew)) {
            persistDevice(merged, isNew)
            persistSighting(device.mac, device.rssi, null)

            val (promoted, did) = checkPromotion(
                mac = device.mac, currentCategory = category,
                name = merged.name ?: merged.classicName, ouiVendor = company, company = null,
                appearance = className, serviceHints = emptyList(), deviceClassName = className,
            )
            finalCategory = promoted
            wasPromoted = did
        }

        knownDevices[device.mac] = if (wasPromoted) merged.copy(category = finalCategory) else merged

        return ProcessedDevice(
            mac = device.mac, name = merged.name ?: merged.classicName, rssi = device.rssi,
            category = finalCategory, brand = merged.brand, model = merged.model,
            type = merged.deviceType, values = emptyMap(), company = company,
            appearance = className, serviceHints = emptyList(),
            transport = transport,
            guessedType = DeviceClassifier.guessTypeFromName(device.name),
            wasPromoted = wasPromoted,
        )
    }

    private suspend fun persistDevice(entity: DeviceEntity, isNew: Boolean) {
        if (isNew) {
            val inserted = dao.insertDevice(entity)
            if (inserted != -1L) return
            // Race: another thread inserted first → fall through to update
        }
        val updated = dao.updateFromScan(
            mac = entity.mac, name = entity.name, classicName = entity.classicName,
            brand = entity.brand, model = entity.model, modelId = entity.modelId,
            deviceType = entity.deviceType, transport = entity.transport,
            category = entity.category, appearance = entity.appearance,
            company = entity.company,
            latestSeenMs = entity.latestSeenMs,
        )
        if (updated == 0) {
            // Device was deleted while scanning — re-insert
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

    private suspend fun getOrLoadDevice(mac: String): DeviceEntity? =
        knownDevices.getOrPut(mac) { dao.getDevice(mac) }

    private suspend fun checkPromotion(
        mac: String, currentCategory: DeviceCategory,
        name: String?, ouiVendor: String?, company: String?,
        appearance: String?, serviceHints: List<String>, deviceClassName: String?,
    ): Pair<DeviceCategory, Boolean> {
        if (currentCategory != DeviceCategory.ONCE) return currentCategory to false
        val days = dao.countDistinctDays(mac)
        if (days < 2) return DeviceCategory.ONCE to false  // TODO: back to 3 after testing

        val hasId = DeviceClassifier.hasIdentity(name, ouiVendor, appearance, serviceHints, company, deviceClassName)
        val promoted = DeviceClassifier.promotedCategory(hasId)
        dao.updateCategory(mac, promoted)
        newDeviceCount.incrementAndGet()
        return promoted to true
    }

    private fun resolveCategory(
        existingCategory: DeviceCategory?, advert: ParsedAdvert, decoded: DecodedDevice?,
    ): DeviceCategory {
        val fresh = DeviceClassifier.classifyBle(advert, decoded)
        return when {
            existingCategory == null -> fresh
            existingCategory == DeviceCategory.ONCE && fresh == DeviceCategory.SENSOR -> DeviceCategory.SENSOR
            else -> existingCategory
        }
    }

    private suspend fun persistSighting(mac: String, rssi: Int, decoded: DecodedDevice?) {
        val valuesJson = decoded?.values?.takeIf { it.isNotEmpty() }?.let { vals ->
            try { Json.encodeToString(vals.mapValues { it.value.toString() }) } catch (_: Exception) { null }
        }
        try {
            dao.insertSighting(SightingEntity(
                mac = mac, timestamp = System.currentTimeMillis(),
                latitude = latitude, longitude = longitude,
                rssi = rssi, decodedValues = valuesJson,
            ))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Device not yet in DB (race condition) — skip this sighting
        }
    }
}
