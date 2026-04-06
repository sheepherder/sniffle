package de.schaefer.sniffle.ble

import android.content.Context
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
import java.time.LocalDate

/**
 * Result of processing a single scan result.
 * Used by LiveViewModel for UI state, ignored by ScanService.
 */
data class ProcessedDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val category: DeviceCategory,
    val brand: String?,
    val model: String?,
    val type: String?,
    val values: Map<String, Any>,
    val company: String?,
    val appearance: String?,
    val serviceHints: List<String>,
    val transport: Transport,
    val guessedType: String?,
    val wasPromoted: Boolean,
    val entity: DeviceEntity,
)

/**
 * Shared scan processing pipeline used by both LiveViewModel and ScanService.
 * Handles: decode → classify → persist → promote → notify.
 */
class ScanProcessor(
    private val dao: DeviceDao,
    private val latitude: Double? = null,
    private val longitude: Double? = null,
) {
    var newDeviceCount = 0; private set
    var sensorCount = 0; private set
    var totalCount = 0; private set

    suspend fun processBle(advert: ParsedAdvert): ProcessedDevice {
        val decoded = DecoderChain.decode(advert)
        val ouiVendor = OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = AppearanceResolver.resolve(advert.appearance)
        val serviceHints = ServiceUuidResolver.resolve(advert.serviceUuids)
        val guessedType = DeviceClassifier.guessTypeFromName(advert.name)

        val existing = dao.getDevice(advert.mac)
        val category = resolveCategory(existing?.category, advert, decoded)
        val today = LocalDate.now().toString()

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
            note = existing?.note,
            notified = existing?.notified ?: false,
        )
        dao.upsertDevice(entity)
        persistSighting(advert.mac, advert.rssi, decoded)

        totalCount++
        if (decoded?.hasSensorData == true) sensorCount++

        // Check promotion from ONCE → DEVICE/MYSTERY
        var wasPromoted = false
        var finalCategory = category
        if (category == DeviceCategory.ONCE) {
            val days = dao.countDistinctDays(advert.mac)
            if (days >= 3) {
                val hasId = DeviceClassifier.hasIdentity(
                    entity.name, company, appearance, serviceHints, company, null
                )
                finalCategory = DeviceClassifier.promotedCategory(hasId)
                dao.updateCategory(advert.mac, finalCategory)
                wasPromoted = true
                newDeviceCount++
            }
        }

        // Notification for new sensors
        if (existing == null && category == DeviceCategory.SENSOR) {
            newDeviceCount++
        }

        return ProcessedDevice(
            mac = advert.mac,
            name = entity.name,
            rssi = advert.rssi,
            category = finalCategory,
            brand = entity.brand,
            model = entity.model,
            type = entity.deviceType,
            values = decoded?.values ?: emptyMap(),
            company = entity.company,
            appearance = entity.appearance,
            serviceHints = serviceHints,
            transport = Transport.BLE,
            guessedType = guessedType,
            wasPromoted = wasPromoted,
            entity = entity.copy(category = finalCategory),
        )
    }

    suspend fun processClassic(device: ClassicDevice): ProcessedDevice {
        val existing = dao.getDevice(device.mac)
        val category = existing?.category ?: DeviceCategory.ONCE
        val today = LocalDate.now().toString()
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
            note = existing?.note,
            notified = existing?.notified ?: false,
        )
        dao.upsertDevice(entity)
        persistSighting(device.mac, device.rssi, null)

        totalCount++

        var wasPromoted = false
        var finalCategory = category
        if (category == DeviceCategory.ONCE) {
            val days = dao.countDistinctDays(device.mac)
            if (days >= 3) {
                val hasId = DeviceClassifier.hasIdentity(
                    device.name, company, className, emptyList(), null, className
                )
                finalCategory = DeviceClassifier.promotedCategory(hasId)
                dao.updateCategory(device.mac, finalCategory)
                wasPromoted = true
                newDeviceCount++
            }
        }

        return ProcessedDevice(
            mac = device.mac,
            name = entity.name,
            rssi = device.rssi,
            category = finalCategory,
            brand = null,
            model = className,
            type = null,
            values = emptyMap(),
            company = company,
            appearance = className,
            serviceHints = emptyList(),
            transport = Transport.CLASSIC,
            guessedType = DeviceClassifier.guessTypeFromName(device.name),
            wasPromoted = wasPromoted,
            entity = entity.copy(category = finalCategory),
        )
    }

    private fun resolveCategory(
        existingCategory: DeviceCategory?,
        advert: ParsedAdvert,
        decoded: DecodedDevice?,
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
            try {
                Json.encodeToString(vals.mapValues { it.value.toString() })
            } catch (_: Exception) { null }
        }
        dao.insertSighting(SightingEntity(
            mac = mac,
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            rssi = rssi,
            decodedValues = valuesJson,
        ))
    }
}
