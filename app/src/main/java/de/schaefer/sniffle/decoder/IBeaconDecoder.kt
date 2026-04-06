package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.readUint16BE
import de.schaefer.sniffle.util.toHex

/**
 * iBeacon decoder. Manufacturer ID 0x004C (Apple) + prefix 02 15.
 */
object IBeaconDecoder : Decoder {

    private const val APPLE_COMPANY_ID = 0x004C

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val p = advert.manufacturerData[APPLE_COMPANY_ID] ?: return null
        if (p.size < 23 || (p[0].toInt() and 0xFF) != 0x02 || (p[1].toInt() and 0xFF) != 0x15) return null

        val uuid = buildString {
            append(p.sliceArray(2..5).toHex()); append("-")
            append(p.sliceArray(6..7).toHex()); append("-")
            append(p.sliceArray(8..9).toHex()); append("-")
            append(p.sliceArray(10..11).toHex()); append("-")
            append(p.sliceArray(12..17).toHex())
        }

        return DecodedDevice(
            "iBeacon", "iBeacon", "IBEACON", "BCON",
            mapOf(
                "uuid" to uuid,
                "major" to p.readUint16BE(18),
                "minor" to p.readUint16BE(20),
                "tx_power" to p[22].toInt(),
            ), false,
        )
    }
}
