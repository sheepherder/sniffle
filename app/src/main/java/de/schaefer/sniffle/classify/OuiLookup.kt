package de.schaefer.sniffle.classify

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MAC OUI → manufacturer lookup from assets/oui.csv.
 * Format: "AABBCC,Vendor Name" (uppercase hex, no separators).
 */
object OuiLookup {

    private var db: Map<String, String>? = null

    fun init(context: Context) {
        if (db != null) return
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("oui.csv").use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        val comma = line.indexOf(',')
                        if (comma > 0) {
                            val oui = line.substring(0, comma).uppercase()
                            val vendor = line.substring(comma + 1)
                            map[oui] = vendor
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // oui.csv not found — OUI lookup disabled
        }
        db = map
    }

    /**
     * Lookup manufacturer from MAC address.
     * Returns null for locally-administered (random) MACs.
     */
    fun lookup(mac: String): String? {
        val parts = mac.uppercase().split(":")
        if (parts.size < 3) return null
        // Bit 1 of first byte = locally administered (random)
        val firstByte = parts[0].toIntOrNull(16) ?: return null
        if (firstByte and 0x02 != 0) return null
        val oui = parts[0] + parts[1] + parts[2]
        return db?.get(oui)
    }

    /** Returns true if MAC is locally administered (random). */
    fun isRandomMac(mac: String): Boolean {
        val first = mac.split(":").firstOrNull() ?: return true
        val byte = first.toIntOrNull(16) ?: return true
        return byte and 0x02 != 0
    }
}
