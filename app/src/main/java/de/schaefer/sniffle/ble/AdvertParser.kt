package de.schaefer.sniffle.ble

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Parsed BLE advertisement data, ready for decoder chain.
 */
data class ParsedAdvert(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val manufacturerData: Map<Int, ByteArray>,   // company ID → payload
    val serviceData: Map<String, ByteArray>,      // UUID string → payload
    val serviceUuids: List<String>,
    val appearance: Int?,
    val theengsJson: String,                      // JSON for TheengsDecoder
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
            for (i in 0 until sparse.size()) {
                mfgData[sparse.keyAt(i)] = sparse.valueAt(i)
            }
        }

        val svcData = mutableMapOf<String, ByteArray>()
        record?.serviceData?.forEach { (uuid, bytes) ->
            svcData[uuid.toString()] = bytes
        }

        val svcUuids = record?.serviceUuids?.map { it.toString() } ?: emptyList()
        val appearance = parseAppearance(record)

        val theengsJson = buildTheengsJson(name, mfgData, svcData)

        return ParsedAdvert(
            mac = mac,
            name = name,
            rssi = rssi,
            txPower = txPower,
            manufacturerData = mfgData,
            serviceData = svcData,
            serviceUuids = svcUuids,
            appearance = appearance,
            theengsJson = theengsJson,
        )
    }

    /**
     * Build JSON string for TheengsDecoder.
     * Format: {"name":"...", "manufacturerdata":"hex...", "servicedata":"hex...", "servicedatauuid":"..."}
     */
    private fun buildTheengsJson(
        name: String?,
        mfgData: Map<Int, ByteArray>,
        svcData: Map<String, ByteArray>,
    ): String {
        return buildJsonObject {
            if (!name.isNullOrEmpty()) put("name", name)

            if (mfgData.isNotEmpty()) {
                val hex = mfgData.entries.joinToString("") { (cid, payload) ->
                    // Company ID as 2-byte little-endian hex + payload hex
                    val lo = cid and 0xFF
                    val hi = (cid shr 8) and 0xFF
                    "%02x%02x".format(lo, hi) + payload.toHex()
                }
                put("manufacturerdata", hex)
            }

            if (svcData.isNotEmpty()) {
                val (uuid, payload) = svcData.entries.first()
                put("servicedata", payload.toHex())
                put("servicedatauuid", uuid)
            }
        }.toString()
    }

    /**
     * Parse BLE Appearance from raw advertisement bytes (AD Type 0x19).
     */
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

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
