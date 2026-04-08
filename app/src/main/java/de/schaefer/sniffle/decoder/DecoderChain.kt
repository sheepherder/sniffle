package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.ble.TheengsDecoder
import de.schaefer.sniffle.util.toHex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class DecodedDevice(
    val brand: String,
    val model: String,
    val modelId: String,
    val type: String,
    val values: Map<String, Any>,
    val hasSensorData: Boolean,
)

val SENSOR_TYPES = setOf("THB", "TH", "BBQ", "PLANT", "SCALE", "AIR", "ENRG", "BODY", "ACEL")

interface Decoder {
    fun decode(advert: ParsedAdvert): DecodedDevice?
}

object DecoderChain {

    private val decoders: List<Decoder> = listOf(
        TheengsDecoderAdapter,
        BtHomeDecoder,
        RuuviDecoder,
        EddystoneDecoder,
        IBeaconDecoder,
        ContinuityDecoder,
        FastPairDecoder,
        FmdnDecoder,
        MsCdpDecoder,
    )

    fun decode(advert: ParsedAdvert): DecodedDevice? {
        for (decoder in decoders) {
            val result = decoder.decode(advert)
            if (result != null) return result
        }
        return null
    }
}

/**
 * Wraps TheengsDecoder C++/JNI. Builds the Theengs-specific JSON from ParsedAdvert.
 */
object TheengsDecoderAdapter : Decoder {
    private val json = Json { ignoreUnknownKeys = true }

    private val SKIP_KEYS = setOf(
        "name", "id", "mac", "manufacturerdata", "servicedata",
        "servicedatauuid", "uuid", "brand", "model", "model_id",
        "type", "cidc", "acts"
    )

    // Company IDs handled by dedicated decoders (ContinuityDecoder, MsCdpDecoder)
    private val SKIP_COMPANY_IDS = setOf(0x004C, 0x0006)

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        if (advert.manufacturerData.keys.any { it in SKIP_COMPANY_IDS }) return null
        val input = buildTheengsJson(advert)
        if (input.length < 3) return null
        val resultJson = TheengsDecoder.decodeBLE(input) ?: return null
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
                val prim = (element as? kotlinx.serialization.json.JsonPrimitive) ?: continue
                values[key] = when {
                    prim.isString -> prim.content
                    prim.content.contains('.') -> prim.content.toDoubleOrNull() ?: prim.content
                    prim.content == "true" -> true
                    prim.content == "false" -> false
                    else -> prim.content.toLongOrNull() ?: prim.content
                }
            }

            if ("tempc" in values) values.remove("tempf")

            DecodedDevice(brand, model, modelId, type, values, type in SENSOR_TYPES)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildTheengsJson(advert: ParsedAdvert): String = buildJsonObject {
        if (!advert.name.isNullOrEmpty()) put("name", advert.name)
        if (advert.manufacturerData.isNotEmpty()) {
            val (cid, payload) = advert.manufacturerData.entries.first()
            val hex = "%02x%02x".format(cid and 0xFF, (cid shr 8) and 0xFF) + payload.toHex()
            put("manufacturerdata", hex)
        }
        if (advert.serviceData.isNotEmpty()) {
            val (uuid, payload) = advert.serviceData.entries.first()
            put("servicedata", payload.toHex())
            // Theengs expects short "0x181a" form, Android gives full 128-bit UUID
            val shortUuid = if (uuid.length == 36 && uuid.endsWith("-0000-1000-8000-00805f9b34fb"))
                "0x${uuid.substring(4, 8)}" else uuid
            put("servicedatauuid", shortUuid)
        }
    }.toString()
}
