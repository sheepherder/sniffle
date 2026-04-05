package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.ble.toHex

/**
 * iBeacon decoder.
 * Manufacturer ID 0x004C (Apple) + prefix 02 15.
 */
object IBeaconDecoder : Decoder {

    private const val APPLE_COMPANY_ID = 0x004C

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.manufacturerData[APPLE_COMPANY_ID] ?: return null
        if (payload.size < 23) return null
        if (payload[0].toInt() and 0xFF != 0x02) return null
        if (payload[1].toInt() and 0xFF != 0x15) return null

        val uuid = buildString {
            append(payload.sliceArray(2..5).toHex())
            append("-")
            append(payload.sliceArray(6..7).toHex())
            append("-")
            append(payload.sliceArray(8..9).toHex())
            append("-")
            append(payload.sliceArray(10..11).toHex())
            append("-")
            append(payload.sliceArray(12..17).toHex())
        }

        val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)
        val txPower = payload[22].toInt() // signed byte

        return DecodedDevice(
            brand = "iBeacon",
            model = "iBeacon",
            modelId = "IBEACON",
            type = "BCON",
            values = mapOf(
                "uuid" to uuid,
                "major" to major,
                "minor" to minor,
                "tx_power" to txPower,
            ),
            hasSensorData = false,
        )
    }
}
