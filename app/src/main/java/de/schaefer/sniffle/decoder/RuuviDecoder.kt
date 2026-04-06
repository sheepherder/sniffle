package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.readInt16BE
import de.schaefer.sniffle.util.readUint16BE

/**
 * Ruuvi Tag RAWv2 (Data Format 5) decoder.
 * Manufacturer ID 0x0499 (Ruuvi Innovations).
 */
object RuuviDecoder : Decoder {

    private const val RUUVI_COMPANY_ID = 0x0499
    private const val FORMAT_RAWV2 = 5
    private const val INVALID_16 = -32768

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val p = advert.manufacturerData[RUUVI_COMPANY_ID] ?: return null
        if (p.size < 14 || (p[0].toInt() and 0xFF) != FORMAT_RAWV2) return null

        val values = mutableMapOf<String, Any>()

        val temp = p.readInt16BE(1)
        if (temp != INVALID_16) values["temperature"] = temp * 0.005

        val hum = p.readUint16BE(3)
        if (hum != 0xFFFF) values["humidity"] = hum * 0.0025

        val pres = p.readUint16BE(5)
        if (pres != 0xFFFF) values["pressure"] = (pres + 50000) / 100.0

        val accX = p.readInt16BE(7)
        val accY = p.readInt16BE(9)
        val accZ = p.readInt16BE(11)
        if (accX != INVALID_16) values["acc_x"] = accX / 1000.0
        if (accY != INVALID_16) values["acc_y"] = accY / 1000.0
        if (accZ != INVALID_16) values["acc_z"] = accZ / 1000.0

        val powerInfo = p.readUint16BE(13)
        val batteryMv = (powerInfo ushr 5) + 1600
        if (batteryMv != 1600 + 0x7FF) values["battery"] = batteryMv / 1000.0
        values["tx_power"] = (powerInfo and 0x1F) * 2 - 40

        if (p.size > 15) values["movement"] = p[15].toInt() and 0xFF
        if (p.size > 17) values["sequence"] = p.readUint16BE(16)

        if (values.isEmpty()) return null
        return DecodedDevice("Ruuvi", "RuuviTag RAWv2", "RUUVI_RAWV2", "THB", values, true)
    }
}
