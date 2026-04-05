package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.ble.toHex

/**
 * Eddystone decoder: UID, URL, TLM frames.
 * Spec: https://github.com/google/eddystone
 *
 * Service UUID 0xFEAA.
 */
object EddystoneDecoder : Decoder {

    private const val EDDYSTONE_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.serviceData[EDDYSTONE_UUID] ?: return null
        if (payload.isEmpty()) return null

        val frameType = payload[0].toInt() and 0xFF
        return when (frameType) {
            0x00 -> decodeUid(payload)
            0x10 -> decodeUrl(payload)
            0x20 -> decodeTlm(payload)
            else -> null
        }
    }

    private fun decodeUid(data: ByteArray): DecodedDevice? {
        if (data.size < 18) return null
        val txPower = data[1].toInt() // calibrated at 0m
        val namespace = data.sliceArray(2..11).toHex()
        val instance = data.sliceArray(12..17).toHex()

        return DecodedDevice(
            brand = "Eddystone",
            model = "Eddystone-UID",
            modelId = "EDDYSTONE_UID",
            type = "BCON",
            values = mapOf(
                "namespace" to namespace,
                "instance" to instance,
                "tx_power" to txPower,
            ),
            hasSensorData = false,
        )
    }

    private fun decodeUrl(data: ByteArray): DecodedDevice? {
        if (data.size < 3) return null
        val txPower = data[1].toInt()
        val scheme = URL_SCHEMES.getOrElse(data[2].toInt() and 0xFF) { "?" }
        val encoded = StringBuilder(scheme)
        for (i in 3 until data.size) {
            val b = data[i].toInt() and 0xFF
            encoded.append(URL_CODES.getOrElse(b) { b.toChar().toString() })
        }

        return DecodedDevice(
            brand = "Eddystone",
            model = "Eddystone-URL",
            modelId = "EDDYSTONE_URL",
            type = "BCON",
            values = mapOf(
                "url" to encoded.toString(),
                "tx_power" to txPower,
            ),
            hasSensorData = false,
        )
    }

    private fun decodeTlm(data: ByteArray): DecodedDevice? {
        if (data.size < 14) return null
        val version = data[1].toInt() and 0xFF
        if (version != 0) return null

        val batteryMv = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        // Temperature: 8.8 fixed point
        val tempInt = data[4].toInt() // signed
        val tempFrac = data[5].toInt() and 0xFF
        val temperature = tempInt + tempFrac / 256.0
        val advCount = ((data[6].toInt() and 0xFF) shl 24) or
                ((data[7].toInt() and 0xFF) shl 16) or
                ((data[8].toInt() and 0xFF) shl 8) or
                (data[9].toInt() and 0xFF)
        val secCount = ((data[10].toInt() and 0xFF) shl 24) or
                ((data[11].toInt() and 0xFF) shl 16) or
                ((data[12].toInt() and 0xFF) shl 8) or
                (data[13].toInt() and 0xFF)

        val values = mutableMapOf<String, Any>()
        if (batteryMv > 0) values["battery_mv"] = batteryMv
        if (temperature > -128) values["temperature"] = temperature
        values["adv_count"] = advCount
        values["uptime_s"] = secCount / 10 // in 100ms units

        return DecodedDevice(
            brand = "Eddystone",
            model = "Eddystone-TLM",
            modelId = "EDDYSTONE_TLM",
            type = "THB",
            values = values,
            hasSensorData = true,
        )
    }

    private val URL_SCHEMES = mapOf(
        0 to "http://www.", 1 to "https://www.",
        2 to "http://", 3 to "https://",
    )

    private val URL_CODES = mapOf(
        0 to ".com/", 1 to ".org/", 2 to ".edu/", 3 to ".net/",
        4 to ".info/", 5 to ".biz/", 6 to ".gov/",
        7 to ".com", 8 to ".org", 9 to ".edu", 10 to ".net",
        11 to ".info", 12 to ".biz", 13 to ".gov",
    )
}
