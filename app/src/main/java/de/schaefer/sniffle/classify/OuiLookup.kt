package de.schaefer.sniffle.classify

import android.content.Context

/**
 * MAC OUI → manufacturer lookup from assets/oui.csv.
 */
object OuiLookup {

    @Volatile
    private var db: Map<String, String> = emptyMap()

    fun init(context: Context) {
        if (db.isNotEmpty()) return
        synchronized(this) {
            if (db.isNotEmpty()) return
            val map = mutableMapOf<String, String>()
            try {
                context.assets.open("oui.csv").use { stream ->
                    stream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val comma = line.indexOf(',')
                            if (comma > 0) {
                                map[line.substring(0, comma).uppercase()] = line.substring(comma + 1)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            db = map
        }
    }

    fun lookup(mac: String): String? {
        val oui = mac.take(8).replace(":", "").uppercase()
        return db[oui]
    }
}
