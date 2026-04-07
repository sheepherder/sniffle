package de.schaefer.sniffle.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.Build
import de.schaefer.sniffle.util.toHex

/**
 * Parsed BLE advertisement data, ready for decoder chain.
 * [addressType]: BLE address type from BluetoothDevice.getAddressType() (API 34+), or -1 if unavailable.
 */
data class ParsedAdvert(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<String, ByteArray>,
    val serviceUuids: List<String>,
    val appearance: Int?,
    val addressType: Int = -1,
)

object AdvertParser {

    fun parse(result: ScanResult): ParsedAdvert {
        val device = result.device
        val record = result.scanRecord
        val mac = device.address
        val name = record?.deviceName
        val rssi = result.rssi
        val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }

        val mfgData = mutableMapOf<Int, ByteArray>()
        record?.manufacturerSpecificData?.let { sparse ->
            @Suppress("UseKtx") for (i in 0 until sparse.size()) {
                mfgData[sparse.keyAt(i)] = sparse.valueAt(i)
            }
        }

        val svcData = mutableMapOf<String, ByteArray>()
        record?.serviceData?.forEach { (uuid, bytes) ->
            svcData[uuid.toString()] = bytes
        }

        val svcUuids = record?.serviceUuids?.map { it.toString() } ?: emptyList()
        val appearance = parseAppearance(record)

        val addrType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            device.addressType
        } else {
            -1
        }

        return ParsedAdvert(mac, name, rssi, txPower, mfgData, svcData, svcUuids, appearance, addrType)
    }

    private fun parseAppearance(record: ScanRecord?): Int? {
        val bytes = record?.bytes ?: return null
        var i = 0
        while (i < bytes.size - 1) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= bytes.size) break
            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x19 && len >= 3) {
                val lo = bytes[i + 2].toInt() and 0xFF
                val hi = bytes[i + 3].toInt() and 0xFF
                return lo or (hi shl 8)
            }
            i += len + 1
        }
        return null
    }
}
