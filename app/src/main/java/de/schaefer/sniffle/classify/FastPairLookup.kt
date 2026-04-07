package de.schaefer.sniffle.classify

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
/**
 * Looks up Google Fast Pair Model IDs from assets/fastpair_models.json.
 * Pattern follows OuiLookup: singleton, lazy init, in-memory map.
 */
object FastPairLookup {

    data class FastPairModel(
        val name: String,
        val brand: String,
        val deviceType: String,
    )

    @Volatile
    private var db: Map<Int, FastPairModel> = emptyMap()

    fun init(context: Context) {
        if (db.isNotEmpty()) return
        synchronized(this) {
            if (db.isNotEmpty()) return
            val map = mutableMapOf<Int, FastPairModel>()
            try {
                context.assets.open("fastpair_models.json").use { stream ->
                    val text = stream.bufferedReader().readText()
                    val json = Json { ignoreUnknownKeys = true }
                    val array = json.parseToJsonElement(text).jsonArray
                    for (element in array) {
                        val obj = element.jsonObject
                        val modelId = obj["modelId"]?.jsonPrimitive?.content
                            ?.removePrefix("0x")?.removePrefix("0X")
                            ?.toIntOrNull(16) ?: continue
                        val name = obj["name"]?.jsonPrimitive?.content ?: continue
                        val brand = obj["brand"]?.jsonPrimitive?.content ?: "Unknown"
                        val deviceType = obj["deviceType"]?.jsonPrimitive?.content ?: "MISC"
                        map[modelId] = FastPairModel(name, brand, deviceType)
                    }
                }
            } catch (_: Exception) { }
            db = map
        }
    }

    fun lookup(modelId: Int): FastPairModel? = db[modelId]
}
