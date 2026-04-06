package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.readUint16BE
import de.schaefer.sniffle.util.readUint32BE
import de.schaefer.sniffle.util.toHex

/**
 * Eddystone decoder: UID, URL, TLM frames.
 * Service UUID 0xFEAA.
 */
object EddystoneDecoder : Decoder {

    private const val EDDYSTONE_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val data = advert.serviceData[EDDYSTONE_UUID] ?: return null
        if (data.isEmpty()) return null
        return when (data[0].toInt() and 0xFF) {
            0x00 -> decodeUid(data)
            0x10 -> decodeUrl(data)
            0x20 -> decodeTlm(data)
            else -> null
        }
    }

    private fun decodeUid(d: ByteArray): DecodedDevice? {
        if (d.size < 18) return null
        return DecodedDevice(
            "Eddystone", "Eddystone-UID", "EDDYSTONE_UID", "BCON",
            mapOf(
                "namespace" to d.sliceArray(2..11).toHex(),
                "instance" to d.sliceArray(12..17).toHex(),
                "tx_power" to d[1].toInt(),
            ), false,
        )
    }

    private fun decodeUrl(d: ByteArray): DecodedDevice? {
        if (d.size < 3) return null
        val scheme = URL_SCHEMES[d[2].toInt() and 0xFF] ?: "?"
        val url = buildString {
            append(scheme)
            for (i in 3 until d.size) {
                val b = d[i].toInt() and 0xFF
                append(URL_CODES[b] ?: b.toChar().toString())
            }
        }
        return DecodedDevice(
            "Eddystone", "Eddystone-URL", "EDDYSTONE_URL", "BCON",
            mapOf("url" to url, "tx_power" to d[1].toInt()), false,
        )
    }

    private fun decodeTlm(d: ByteArray): DecodedDevice? {
        if (d.size < 14 || (d[1].toInt() and 0xFF) != 0) return null
        val batteryMv = d.readUint16BE(2)
        val temp = d[4].toInt() + (d[5].toInt() and 0xFF) / 256.0
        val advCount = d.readUint32BE(6)
        val secCount = d.readUint32BE(10)

        val values = mutableMapOf<String, Any>()
        if (batteryMv > 0) values["battery_mv"] = batteryMv
        if (temp > -128) values["temperature"] = temp
        values["adv_count"] = advCount
        values["uptime_s"] = secCount / 10
        return DecodedDevice("Eddystone", "Eddystone-TLM", "EDDYSTONE_TLM", "THB", values, true)
    }

    private val URL_SCHEMES = mapOf(
        0 to "http://www.", 1 to "https://www.", 2 to "http://", 3 to "https://",
    )
    private val URL_CODES = mapOf(
        0 to ".com/", 1 to ".org/", 2 to ".edu/", 3 to ".net/",
        4 to ".info/", 5 to ".biz/", 6 to ".gov/",
        7 to ".com", 8 to ".org", 9 to ".edu", 10 to ".net",
        11 to ".info", 12 to ".biz", 13 to ".gov",
    )
}
