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
import de.schaefer.sniffle.decoder.DecoderChain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
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
 * Shared scan processing pipeline used by both LiveViewModel and ScanService.
 */
class ScanProcessor(
    private val dao: DeviceDao,
    @Volatile var latitude: Double? = null,
    @Volatile var longitude: Double? = null,
) {
    val newDeviceCount = AtomicInteger()
    val sensorCount = AtomicInteger()
    val totalCount = AtomicInteger()

    // Caches — valid for one scan session, avoid repeated DB reads
    private val deviceCache = mutableMapOf<String, DeviceEntity?>()
    private val distinctDaysCache = mutableMapOf<String, Int>()

    suspend fun processBle(advert: ParsedAdvert): ProcessedDevice {
        val decoded = DecoderChain.decode(advert)
        val ouiVendor = OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = AppearanceResolver.resolve(advert.appearance)
        val serviceHints = ServiceUuidResolver.resolve(advert.serviceUuids)
        val guessedType = DeviceClassifier.guessTypeFromName(advert.name)

        val existing = cachedGetDevice(advert.mac)
        val category = resolveCategory(existing?.category, advert, decoded)
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()

        val entity = DeviceEntity(
            mac = advert.mac,
            name = advert.name ?: existing?.name,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            modelId = decoded?.modelId ?: existing?.modelId,
            deviceType = decoded?.type ?: existing?.deviceType,
            transport = Transport.BLE,
            category = category,
            appearance = appearance ?: existing?.appearance,
            company = company ?: existing?.company,
            firstSeenDate = existing?.firstSeenDate ?: today,
            latestSeenDate = today,
            latestSeenMs = now,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )
        dao.upsertDevice(entity)
        persistSighting(advert.mac, advert.rssi, decoded)

        totalCount.incrementAndGet()
        if (decoded?.hasSensorData == true) sensorCount.incrementAndGet()
        if (existing == null && category == DeviceCategory.SENSOR) newDeviceCount.incrementAndGet()

        val (finalCategory, wasPromoted) = checkPromotion(
            mac = advert.mac, currentCategory = category,
            name = entity.name, ouiVendor = ouiVendor, company = company,
            appearance = appearance, serviceHints = serviceHints, deviceClassName = null,
        )
        val finalEntity = if (wasPromoted) entity.copy(category = finalCategory) else entity
        deviceCache[advert.mac] = finalEntity

        return ProcessedDevice(
            mac = advert.mac, name = entity.name, rssi = advert.rssi,
            category = finalCategory, brand = entity.brand, model = entity.model,
            type = entity.deviceType, values = decoded?.values ?: emptyMap(),
            company = entity.company, appearance = entity.appearance,
            serviceHints = serviceHints, transport = Transport.BLE,
            guessedType = guessedType, wasPromoted = wasPromoted,
        )
    }

    suspend fun processClassic(device: ClassicDevice): ProcessedDevice {
        val existing = cachedGetDevice(device.mac)
        val category = existing?.category ?: DeviceCategory.ONCE
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val company = OuiLookup.lookup(device.mac)
        val className = device.deviceClassName

        val entity = DeviceEntity(
            mac = device.mac,
            name = device.name ?: existing?.name,
            model = className ?: existing?.model,
            deviceType = existing?.deviceType,
            transport = Transport.CLASSIC,
            category = category,
            appearance = className,
            company = company,
            firstSeenDate = existing?.firstSeenDate ?: today,
            latestSeenDate = today,
            latestSeenMs = now,
            note = existing?.note,
            notified = existing?.notified ?: false,
        )
        dao.upsertDevice(entity)
        persistSighting(device.mac, device.rssi, null)
        totalCount.incrementAndGet()

        val (finalCategory, wasPromoted) = checkPromotion(
            mac = device.mac, currentCategory = category,
            name = device.name, ouiVendor = company, company = null,
            appearance = className, serviceHints = emptyList(), deviceClassName = className,
        )
        val finalEntity = if (wasPromoted) entity.copy(category = finalCategory) else entity
        deviceCache[device.mac] = finalEntity

        return ProcessedDevice(
            mac = device.mac, name = entity.name, rssi = device.rssi,
            category = finalCategory, brand = null, model = className,
            type = null, values = emptyMap(), company = company,
            appearance = className, serviceHints = emptyList(),
            transport = Transport.CLASSIC,
            guessedType = DeviceClassifier.guessTypeFromName(device.name),
            wasPromoted = wasPromoted,
        )
    }

    private suspend fun cachedGetDevice(mac: String): DeviceEntity? =
        deviceCache.getOrPut(mac) { dao.getDevice(mac) }

    private suspend fun checkPromotion(
        mac: String, currentCategory: DeviceCategory,
        name: String?, ouiVendor: String?, company: String?,
        appearance: String?, serviceHints: List<String>, deviceClassName: String?,
    ): Pair<DeviceCategory, Boolean> {
        if (currentCategory != DeviceCategory.ONCE) return currentCategory to false
        val days = distinctDaysCache.getOrPut(mac) { dao.countDistinctDays(mac) }
        if (days < 3) return DeviceCategory.ONCE to false

        val hasId = DeviceClassifier.hasIdentity(name, ouiVendor, appearance, serviceHints, company, deviceClassName)
        val promoted = DeviceClassifier.promotedCategory(hasId)
        dao.updateCategory(mac, promoted)
        newDeviceCount.incrementAndGet()
        return promoted to true
    }

    private fun resolveCategory(
        existingCategory: DeviceCategory?, advert: ParsedAdvert, decoded: de.schaefer.sniffle.decoder.DecodedDevice?,
    ): DeviceCategory {
        val fresh = DeviceClassifier.classifyBle(advert, decoded)
        return when {
            existingCategory == null -> fresh
            existingCategory == DeviceCategory.ONCE && fresh == DeviceCategory.SENSOR -> DeviceCategory.SENSOR
            else -> existingCategory
        }
    }

    private suspend fun persistSighting(mac: String, rssi: Int, decoded: de.schaefer.sniffle.decoder.DecodedDevice?) {
        val valuesJson = decoded?.values?.takeIf { it.isNotEmpty() }?.let { vals ->
            try { Json.encodeToString(vals.mapValues { it.value.toString() }) } catch (_: Exception) { null }
        }
        dao.insertSighting(SightingEntity(
            mac = mac, timestamp = System.currentTimeMillis(),
            latitude = latitude, longitude = longitude,
            rssi = rssi, decodedValues = valuesJson,
        ))
    }
}
