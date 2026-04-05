package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.ble.TheengsDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Result of decoding a BLE advertisement.
 */
data class DecodedDevice(
    val brand: String,
    val model: String,
    val modelId: String,
    val type: String,                         // THB, RMAC, BBQ, PLANT, etc.
    val values: Map<String, Any>,             // sensor values: "tempc" -> 23.4
    val hasSensorData: Boolean,               // true if has temp/hum/co2/etc.
)

private val SENSOR_TYPES = setOf("THB", "TH", "BBQ", "PLANT", "SCALE", "AIR", "ENRG", "BODY", "ACEL")

/**
 * Tries all decoders in order. First match wins.
 */
object DecoderChain {

    private val decoders: List<Decoder> = listOf(
        TheengsDecoderAdapter,
        BtHomeDecoder,
        RuuviDecoder,
        EddystoneDecoder,
        IBeaconDecoder,
    )

    fun decode(advert: ParsedAdvert): DecodedDevice? {
        for (decoder in decoders) {
            val result = decoder.decode(advert)
            if (result != null) return result
        }
        return null
    }
}

interface Decoder {
    fun decode(advert: ParsedAdvert): DecodedDevice?
}

/**
 * Adapter that wraps TheengsDecoder C++/JNI calls.
 */
object TheengsDecoderAdapter : Decoder {
    private val json = Json { ignoreUnknownKeys = true }

    private val SKIP_KEYS = setOf(
        "name", "id", "mac", "manufacturerdata", "servicedata",
        "servicedatauuid", "uuid", "brand", "model", "model_id",
        "type", "cidc", "acts"
    )

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        if (advert.theengsJson.length < 3) return null
        val resultJson = TheengsDecoder.decodeBLE(advert.theengsJson) ?: return null
        if (resultJson.isEmpty()) return null

        return try {
            val obj = json.parseToJsonElement(resultJson).jsonObject
            val brand = obj["brand"]?.jsonPrimitive?.content ?: return null
            val model = obj["model"]?.jsonPrimitive?.content ?: ""
            val modelId = obj["model_id"]?.jsonPrimitive?.content ?: ""
            val type = obj["type"]?.jsonPrimitive?.content ?: ""

            val values = mutableMapOf<String, Any>()
            for ((key, element) in obj) {
                if (key in SKIP_KEYS) continue
                val prim = element.jsonPrimitive
                values[key] = when {
                    prim.isString -> prim.content
                    prim.content.contains('.') -> prim.content.toDoubleOrNull() ?: prim.content
                    prim.content == "true" -> true
                    prim.content == "false" -> false
                    else -> prim.content.toLongOrNull() ?: prim.content
                }
            }

            // Remove tempf if tempc exists
            if ("tempc" in values) values.remove("tempf")

            DecodedDevice(
                brand = brand,
                model = model,
                modelId = modelId,
                type = type,
                values = values,
                hasSensorData = type in SENSOR_TYPES,
            )
        } catch (_: Exception) {
            null
        }
    }
}
