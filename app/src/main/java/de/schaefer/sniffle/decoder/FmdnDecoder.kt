package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert

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

        val curve = if (data.size >= 34) "p256" else "p160"

        return DecodedDevice(
            brand = "Google",
            model = if (isUtp) "FMDN Tracker (UTP)" else "FMDN Tracker",
            modelId = "GOOGLE_FMDN",
            type = "TRACKER",
            values = mapOf(
                "frame_type" to if (isUtp) "utp" else "normal",
                "curve" to curve,
            ),
            hasSensorData = false,
        )
    }
}
