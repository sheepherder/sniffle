package de.schaefer.sniffle.classify

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MAC OUI → manufacturer lookup from assets/oui.csv.
 */
object OuiLookup {

    private var db: Map<String, String> = emptyMap()

    fun init(context: Context) {
        if (db.isNotEmpty()) return
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("oui.csv").use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
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

    fun lookup(mac: String): String? {
        val oui = mac.take(8).replace(":", "").uppercase()
        return db[oui]
    }
}
