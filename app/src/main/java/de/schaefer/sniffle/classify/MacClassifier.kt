package de.schaefer.sniffle.classify

import android.bluetooth.BluetoothDevice

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
     *
     * Uses [addressType] from BluetoothDevice.getAddressType() (API 34+) when available.
     * Falls back to heuristic on API 33: locally-administered bit + top-2-bit check.
     */
    fun isStable(mac: String, addressType: Int = -1): Boolean {
        // API 34+: trust the BLE stack's address type
        if (addressType == BluetoothDevice.ADDRESS_TYPE_PUBLIC) return true
        if (addressType == BluetoothDevice.ADDRESS_TYPE_RANDOM) {
            val firstByte = mac.substring(0, 2).toInt(16)
            // Random Static: top 2 bits = 11
            return firstByte and 0xC0 == 0xC0
        }

        // Fallback (API 33 or unknown): heuristic from MAC bytes
        val firstByte = mac.substring(0, 2).toInt(16)
        // Locally administered bit set → Random address
        if (firstByte and 0x02 != 0) {
            // Random Static (top 2 bits = 11) → stable
            return firstByte and 0xC0 == 0xC0
        }
        // Locally administered bit clear → Public address
        return true
    }
}
