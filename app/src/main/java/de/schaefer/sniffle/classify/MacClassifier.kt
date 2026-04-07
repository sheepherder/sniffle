package de.schaefer.sniffle.classify

/**
 * Classifies BLE MAC addresses by address type (Bluetooth Core Spec Vol 6, Part B, §1.3).
 *
 * - Public: real IEEE OUI, permanently assigned
 * - Random Static: locally administered, stable for device lifetime
 * - Resolvable Private (RPA): rotates every ~15 min, not trackable
 * - Non-Resolvable: one-shot, not trackable
 */
object MacClassifier {

    /**
     * Returns true for Public and Random Static addresses — MACs that are stable
     * and therefore meaningful to persist and classify.
     */
    fun isStable(mac: String): Boolean {
        val firstByte = mac.substring(0, 2).toInt(16)
        // Locally administered bit not set → Public address
        if (firstByte and 0x02 == 0) return true
        // Random address: top 2 bits == 11 → Random Static (stable)
        return firstByte and 0xC0 == 0xC0
    }
}
