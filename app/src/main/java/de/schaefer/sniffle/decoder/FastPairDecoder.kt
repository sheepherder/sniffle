package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.classify.FastPairLookup
import de.schaefer.sniffle.util.readUint24BE

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

        val modelId = data.readUint24BE(1)

        val idHex = "0x%06X".format(modelId)
        val decodedModelId = "FASTPAIR_${idHex.removePrefix("0x")}"
        val lookup = FastPairLookup.lookup(modelId)

        return DecodedDevice(
            brand = lookup?.brand ?: "Google",
            model = lookup?.name ?: "Fast Pair Device ($idHex)",
            modelId = decodedModelId,
            type = lookup?.deviceType ?: "MISC",
            values = mapOf("model_id" to idHex),
            hasSensorData = false,
        )
    }
}
