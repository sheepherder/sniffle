package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert

/**
 * Ruuvi Tag RAWv2 (Data Format 5) decoder.
 * Spec: https://github.com/ruuvi/ruuvi-sensor-protocols
 *
 * Manufacturer ID 0x0499 (Ruuvi Innovations).
 */
object RuuviDecoder : Decoder {

    private const val RUUVI_COMPANY_ID = 0x0499

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.manufacturerData[RUUVI_COMPANY_ID] ?: return null
        if (payload.size < 14) return null

        val format = payload[0].toInt() and 0xFF
        if (format != 5) return null // Only RAWv2

        val values = mutableMapOf<String, Any>()

        // Temperature: 16-bit signed, 0.005 °C resolution
        val tempRaw = (payload[1].toInt() shl 8) or (payload[2].toInt() and 0xFF)
        if (tempRaw != -32768) {
            values["temperature"] = tempRaw * 0.005
        }

        // Humidity: 16-bit unsigned, 0.0025% resolution
        val humRaw = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        if (humRaw != 0xFFFF) {
            values["humidity"] = humRaw * 0.0025
        }

        // Pressure: 16-bit unsigned, Pa, offset +50000
        val presRaw = ((payload[5].toInt() and 0xFF) shl 8) or (payload[6].toInt() and 0xFF)
        if (presRaw != 0xFFFF) {
            values["pressure"] = (presRaw + 50000) / 100.0 // hPa
        }

        // Acceleration X/Y/Z: 16-bit signed, mG
        val accX = (payload[7].toInt() shl 8) or (payload[8].toInt() and 0xFF)
        val accY = (payload[9].toInt() shl 8) or (payload[10].toInt() and 0xFF)
        val accZ = (payload[11].toInt() shl 8) or (payload[12].toInt() and 0xFF)
        if (accX != -32768) values["acc_x"] = accX / 1000.0
        if (accY != -32768) values["acc_y"] = accY / 1000.0
        if (accZ != -32768) values["acc_z"] = accZ / 1000.0

        // Battery voltage (11 bits) + TX power (5 bits)
        val powerInfo = ((payload[13].toInt() and 0xFF) shl 8) or (payload[14].toInt() and 0xFF)
        val batteryMv = (powerInfo ushr 5) + 1600
        val txPower = (powerInfo and 0x1F) * 2 - 40
        if (batteryMv != 1600 + 0x7FF) {
            values["battery"] = batteryMv / 1000.0 // V
        }
        values["tx_power"] = txPower

        // Movement counter
        if (payload.size > 15) {
            values["movement"] = payload[15].toInt() and 0xFF
        }

        // Measurement sequence
        if (payload.size > 17) {
            val seq = ((payload[16].toInt() and 0xFF) shl 8) or (payload[17].toInt() and 0xFF)
            values["sequence"] = seq
        }

        if (values.isEmpty()) return null

        return DecodedDevice(
            brand = "Ruuvi",
            model = "RuuviTag RAWv2",
            modelId = "RUUVI_RAWV2",
            type = "THB",
            values = values,
            hasSensorData = true,
        )
    }
}
