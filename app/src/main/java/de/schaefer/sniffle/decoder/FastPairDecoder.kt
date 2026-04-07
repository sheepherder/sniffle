package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.classify.FastPairLookup

/**
 * Google Fast Pair decoder.
 * Service data under UUID 0xFE2C.
 *
 * Unpaired: [Flags: 1B] [Model ID: 3B Big Endian]
 * Paired:   [Flags: 1B] [Account Key Filter: variable] [Salt: 2B]
 *
 * Ref: https://developers.google.com/nearby/fast-pair/specifications/service/provider
 */
object FastPairDecoder : Decoder {

    private const val FAST_PAIR_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val data = advert.serviceData[FAST_PAIR_UUID] ?: return null
        if (data.size < 4) return null

        // Read 3-byte model ID (big-endian) from bytes 1-3
        val modelId = ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)

        val idHex = "0x%06X".format(modelId)
        val decodedModelId = "FASTPAIR_${idHex.removePrefix("0x")}"
        val lookup = FastPairLookup.lookup(modelId)

        return DecodedDevice(
            brand = lookup?.brand ?: "Google",
            model = lookup?.name ?: "Fast Pair Device ($idHex)",
            modelId = decodedModelId,
            type = lookup?.deviceType ?: "MISC",
            values = mapOf("model_id" to idHex),
            hasSensorData = true,
        )
    }
}
