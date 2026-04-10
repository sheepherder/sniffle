package de.schaefer.sniffle.classify

/** Well-known BLE service UUIDs → human-readable hints. */
object ServiceUuidResolver {

    private val SHORT_UUIDS = mapOf(
        "1800" to "Generic Access", "1801" to "Generic Attribute",
        "180a" to "Geräteinfo", "180d" to "Herzfrequenz",
        "180f" to "Batterie", "1810" to "Blutdruck",
        "1812" to "HID", "1814" to "Laufsensor",
        "1816" to "Rad-Geschwindigkeit", "1818" to "Rad-Leistung",
        "1819" to "Navigation", "181a" to "Umweltsensor",
        "181d" to "Waage", "1822" to "Pulsoximeter",
        "fd6f" to "COVID Exposure", "fce0" to "Aranet",
        "fcd2" to "BTHome", "fe95" to "Xiaomi MiBeacon",
        "fe9f" to "Google", "feb3" to "Shelly",
        "feaa" to "Eddystone", "fd5a" to "Samsung",
        "fcf1" to "Google Fast Pair", "fef3" to "Google/Nest",
        "feed" to "Tile", "fe2c" to "Google Nearby",
    )

    private val FULL_UUIDS = mapOf(
        "6e400001-b5a3-f393-e0a9-e50e24dcca9e" to "Nordic UART",
        "cba20d00-224d-11e6-9fb8-0002a5d5c51b" to "SwitchBot",
    )

    fun resolve(uuids: List<String>): List<String> {
        val hints = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (uuid in uuids) {
            val full = uuid.lowercase()
            // Try full 128-bit match
            FULL_UUIDS[full]?.let { if (seen.add(it)) hints.add(it) }
            // Try 16-bit short UUID from standard base
            val short = if (full.length == 36 && full.endsWith("-0000-1000-8000-00805f9b34fb")) {
                full.substring(4, 8) // 16-bit short UUID from 0000xxxx-...; upper 16 bits are always 0 for assigned UUIDs
            } else full
            SHORT_UUIDS[short]?.let { if (seen.add(it)) hints.add(it) }
        }
        return hints
    }
}
