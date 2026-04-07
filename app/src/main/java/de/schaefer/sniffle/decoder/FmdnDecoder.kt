package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.batteryBitsToLabel

/**
 * Google Find My Device Network (FMDN) decoder.
 * Service data under UUID 0xFEAA (same as Eddystone).
 *
 * Frame types:
 * - 0x40: normal FMDN (22 or 34 bytes payload)
 * - 0x41: unwanted tracking protection mode
 *
 * Collision with Eddystone EID (also 0x40): distinguished by payload length.
 * Eddystone EID = 10 bytes, FMDN = 22 (P-160) or 34 (P-256) bytes.
 *
 * Ref: https://developers.google.com/nearby/fast-pair/specifications/extensions/fmdn
 */
object FmdnDecoder : Decoder {

    private const val EDDYSTONE_UUID = "0000feaa-0000-1000-8000-00805f9b34fb"

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val data = advert.serviceData[EDDYSTONE_UUID] ?: return null
        if (data.isEmpty()) return null

        val frameType = data[0].toInt() and 0xFF
        if (frameType != 0x40 && frameType != 0x41) return null

        // Distinguish from Eddystone EID: FMDN payload is 22 or 34 bytes, EID is 10
        if (frameType == 0x40 && data.size <= 10) return null

        val isUtp = frameType == 0x41

        // Hashed flags: XOR of last EID byte and flags byte
        // P-256: EID bytes 1-32, flags at 33. P-160: EID bytes 1-20, flags at 21.
        val flagsIndex = if (data.size >= 34) 33 else if (data.size >= 22) 21 else -1
        val battery: String
        val utpFlag: Boolean
        if (flagsIndex > 0) {
            val flags = (data[flagsIndex].toInt() and 0xFF) xor (data[flagsIndex - 1].toInt() and 0xFF)
            battery = batteryBitsToLabel((flags shr 5) and 0x03)
            utpFlag = isUtp || ((flags shr 7) and 0x01 == 1)
        } else {
            battery = "unknown"
            utpFlag = isUtp
        }

        val curve = if (data.size >= 34) "p256" else "p160"

        return DecodedDevice(
            brand = "Google",
            model = if (utpFlag) "FMDN Tracker (UTP)" else "FMDN Tracker",
            modelId = "GOOGLE_FMDN",
            type = "TRACKER",
            values = mapOf(
                "battery" to battery,
                "unwanted_tracking_protection" to utpFlag,
                "frame_type" to if (isUtp) "utp" else "normal",
                "curve" to curve,
            ),
            hasSensorData = false,
        )
    }
}
