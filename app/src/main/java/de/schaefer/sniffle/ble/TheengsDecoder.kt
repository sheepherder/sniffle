package de.schaefer.sniffle.ble

object TheengsDecoder {
    init {
        System.loadLibrary("sniffle")
    }

    external fun nativeDecodeBLE(jsonInput: String): String?

    fun decodeBLE(json: String): String? =
        if (json.isNotEmpty()) nativeDecodeBLE(json) else null
}
