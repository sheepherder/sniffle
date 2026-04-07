package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert

/**
 * Microsoft Connected Devices Platform (CDP) decoder.
 * Manufacturer data under Company ID 0x0006 (Microsoft).
 * Spec: MS-CDP open specification.
 */
object MsCdpDecoder : Decoder {

    private const val MS_COMPANY_ID = 0x0006

    private val DEVICE_TYPES = mapOf(
        1 to "Xbox One",
        6 to "Apple iPhone",
        7 to "Apple iPad",
        8 to "Android",
        9 to "Windows 10 Desktop",
        11 to "Windows 10 Phone",
        12 to "Linux",
        13 to "Windows IoT",
        14 to "Surface Hub",
        15 to "Windows Laptop",
    )

    private val SCENARIO_TYPES = mapOf(
        1 to "bluetooth",
        5 to "nearby_share",
        6 to "cross_device",
    )

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.manufacturerData[MS_COMPANY_ID] ?: return null
        if (payload.size < 4) return null

        val byte0 = payload[0].toInt() and 0xFF
        val byte1 = payload[1].toInt() and 0xFF

        val scenarioType = (byte0 shr 4) and 0x0F
        val deviceType = (byte1 shr 3) and 0x1F

        val deviceName = DEVICE_TYPES[deviceType] ?: "Microsoft Device ($deviceType)"
        val scenarioName = SCENARIO_TYPES[scenarioType] ?: "unknown"

        val type = when (deviceType) {
            1 -> "CONSOLE"
            6, 7, 8, 11 -> "PHONE"
            9, 12, 15 -> "COMPUTER"
            14 -> "DISPLAY"
            else -> "MISC"
        }

        return DecodedDevice(
            brand = "Microsoft",
            model = deviceName,
            modelId = "MS_CDP",
            type = type,
            values = mapOf(
                "device_type" to deviceName,
                "scenario" to scenarioName,
            ),
            hasSensorData = true,
        )
    }
}
