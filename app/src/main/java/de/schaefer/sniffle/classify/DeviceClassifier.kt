package de.schaefer.sniffle.classify

import de.schaefer.sniffle.ble.ClassicDevice
import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.decoder.DecodedDevice

/**
 * Determines category for a device.
 *
 * Category flow:
 * - SENSOR: immediately if decoded with sensor data
 * - DEVICE: promoted from ONCE when seen on 3+ distinct days AND has identity
 * - MYSTERY: promoted from ONCE when seen on 3+ distinct days AND has NO identity
 * - ONCE: everything else (default)
 *
 * Promotion from ONCE is checked externally (needs DB access).
 * This class only handles initial classification and identity detection.
 */
object DeviceClassifier {

    private val NAME_PATTERNS = listOf(
        Regex("\\[TV]|Smart.?TV|TV\\b|BRAVIA|LG.?TV|Roku", RegexOption.IGNORE_CASE) to "TV",
        Regex("EOS\\s?R|Canon|Nikon|ILCE|SONY.*CAM|LUMIX", RegexOption.IGNORE_CASE) to "Kamera",
        Regex("ScanWatch|Watch|Fitbit|Mi.?Band|Amazfit|Garmin", RegexOption.IGNORE_CASE) to "Uhr",
        Regex("Oral.?B|Sonicare|Toothbrush", RegexOption.IGNORE_CASE) to "Zahnbürste",
        Regex("SwitchBot|Shelly|Tuya|SONOFF|Tapo", RegexOption.IGNORE_CASE) to "SmartHome",
        Regex("JBL|Bose|AirPods|Buds|Soundcore|WH-|WF-|Earbuds", RegexOption.IGNORE_CASE) to "Audio",
        Regex("Tile|Nut\\b|SmartTag|AirTag", RegexOption.IGNORE_CASE) to "Tracker",
        Regex("Lock|Nuki|August|Yale", RegexOption.IGNORE_CASE) to "Schloss",
        Regex("Lamp|Bulb|Light|Yeelight|LIFX|Hue", RegexOption.IGNORE_CASE) to "Licht",
        Regex("Printer|EPSON|HP.?Smart", RegexOption.IGNORE_CASE) to "Drucker",
        Regex("PlayStation|Xbox|Switch", RegexOption.IGNORE_CASE) to "Konsole",
    )

    /** Known BLE company IDs. */
    private val COMPANY_IDS = mapOf(
        0x004C to "Apple", 0x0006 to "Microsoft", 0x0075 to "Samsung",
        0x00E0 to "Google", 0x0059 to "Nordic Semi", 0x000D to "Texas Instruments",
        0x0157 to "Huawei", 0x0310 to "Xiaomi", 0x01DA to "Amazfit",
        0x038F to "Garmin", 0x0087 to "Bose", 0x000A to "Qualcomm",
        0x0171 to "Amazon", 0x0301 to "Withings", 0x02E5 to "Espressif",
        0x0499 to "Ruuvi", 0x0046 to "Sony", 0x01A9 to "Canon",
        0x0154 to "Sonos", 0x0002 to "Intel", 0x0386 to "Tile",
    )

    /**
     * Initial category for a newly discovered BLE device.
     * SENSOR immediately if the decoder reports sensor data (temperature, humidity, etc.).
     */
    fun classifyBle(advert: ParsedAdvert, decoded: DecodedDevice?): DeviceCategory {
        if (decoded?.hasSensorData == true) return DeviceCategory.SENSOR
        return DeviceCategory.ONCE
    }

    /**
     * Initial category for a Classic BT device.
     */
    fun classifyClassic(device: ClassicDevice): DeviceCategory {
        return DeviceCategory.ONCE
    }

    /**
     * Check if device has enough identity to become DEVICE (vs MYSTERY) on promotion.
     */
    fun hasIdentity(
        name: String?,
        ouiVendor: String?,
        appearance: String?,
        serviceHints: List<String>,
        company: String?,
        deviceClassName: String?,
    ): Boolean {
        if (!name.isNullOrBlank()) return true
        if (!ouiVendor.isNullOrBlank()) return true
        if (!appearance.isNullOrBlank()) return true
        if (serviceHints.isNotEmpty()) return true
        if (!company.isNullOrBlank()) return true
        if (!deviceClassName.isNullOrBlank()) return true
        return false
    }

    /**
     * Determine promoted category when 3-day threshold is reached.
     */
    fun promotedCategory(hasIdentity: Boolean): DeviceCategory =
        if (hasIdentity) DeviceCategory.DEVICE else DeviceCategory.MYSTERY

    /**
     * Try to guess device type from BLE name.
     */
    fun guessTypeFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        for ((pattern, label) in NAME_PATTERNS) {
            if (pattern.containsMatchIn(name)) return label
        }
        return null
    }

    /**
     * Get company name from BLE manufacturer data company ID.
     */
    fun companyFromId(mfgData: Map<Int, ByteArray>): String? {
        for (cid in mfgData.keys) {
            val name = COMPANY_IDS[cid]
            if (name != null) return name
        }
        return null
    }

}
