package de.schaefer.sniffle.classify

/** BLE GAP Appearance codes → German labels. */
object AppearanceResolver {

    private val NAMES = mapOf(
        0 to "Unbekannt", 64 to "Telefon", 128 to "Computer",
        192 to "Uhr", 193 to "Sportuhr", 256 to "Uhr", 320 to "Display",
        384 to "Fernbedienung", 448 to "Brille", 512 to "Tag",
        576 to "Schlüsselanhänger", 640 to "Media Player",
        704 to "Barcode-Scanner", 768 to "Thermometer",
        769 to "Ohr-Thermometer", 832 to "Herzfrequenz-Sensor",
        833 to "Brustgurt", 896 to "Blutdruckmessgerät",
        960 to "HID-Gerät", 961 to "Tastatur", 962 to "Maus",
        963 to "Joystick", 964 to "Gamepad", 965 to "Grafiktablett",
        966 to "Kartenleser", 967 to "Digitaler Stift",
        968 to "Barcode-Scanner", 1024 to "Blutzucker-Messgerät",
        1088 to "Laufsensor", 1152 to "Radsensor",
        1216 to "Pulsoximeter", 1280 to "Waage",
        1344 to "Mobilitätshilfe", 1408 to "Glukose-Monitor",
        1472 to "Insulinpumpe", 3136 to "Außen-LED",
        3200 to "Innen-LED", 5184 to "Outdoor Sport",
    )

    fun resolve(code: Int?): String? {
        if (code == null) return null
        return NAMES[code] ?: NAMES[(code shr 6) shl 6]
    }
}
