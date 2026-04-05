package de.schaefer.sniffle.ble

object TheengsDecoder {
    init {
        System.loadLibrary("sniffle")
    }

    external fun nativeDecodeBLE(jsonInput: String): String?
    external fun nativeGetProperties(modelId: String): String?
    external fun nativeGetAttribute(modelId: String, attribute: String): String?

    fun decodeBLE(json: String): String? =
        if (json.isNotEmpty()) nativeDecodeBLE(json) else null

    fun getProperties(modelId: String): String? =
        if (modelId.isNotEmpty()) nativeGetProperties(modelId) else null

    fun getAttribute(modelId: String, attribute: String): String? =
        if (modelId.isNotEmpty() && attribute.isNotEmpty()) nativeGetAttribute(modelId, attribute) else null
}
