package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.batteryBitsToLabel
import de.schaefer.sniffle.util.readUint16BE

/**
 * Apple Continuity protocol decoder.
 * Manufacturer data under Company ID 0x004C (Apple).
 *
 * Payload is TLV-encoded: [Type: 1B] [Length: 1B] [Value: NB]
 * Multiple TLV messages can be present in a single advertisement.
 *
 * Subtypes:
 * - 0x07: Proximity Pairing (AirPods, Beats) — battery, model, charging
 * - 0x10: Nearby Info (iPhone/iPad/Mac) — activity, lock, AirDrop
 * - 0x12: Find My (AirTag, Find My accessories) — battery, owner proximity
 * - 0x0A: AirPlay Source
 *
 * Ref: "Handoff All Your Privacy" (PETS 2020)
 */
object ContinuityDecoder : Decoder {

    private const val APPLE_COMPANY_ID = 0x004C

    // Proximity Pairing (0x07) device model lookup — 2-byte big-endian model IDs.
    // Source: theapplewiki.com/wiki/Bluetooth_PIDs
    @Suppress("SpellCheckingInspection")
    private val PROXIMITY_MODELS = mapOf(
        0x0220 to "AirPods",
        0x0F20 to "AirPods 2",
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x1320 to "AirPods 3",
        0x0A20 to "AirPods Max",
        0x0620 to "Powerbeats3",
        0x0B20 to "Powerbeats Pro",
        0x0320 to "Beats X",
        0x0520 to "Beats Solo3",
        0x0920 to "Beats Studio3",
        0x0C20 to "Beats Solo Pro",
        0x1120 to "Beats Fit Pro",
        0x1220 to "Beats Studio Buds+",
        0x1720 to "Beats Solo Buds",
        0x1620 to "Beats Studio Pro",
        0x0D20 to "Beats Studio Buds",
        0x1020 to "Beats Flex",
        0x0420 to "BeatsX",
    )

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.manufacturerData[APPLE_COMPANY_ID] ?: return null
        if (payload.size < 3) return null

        val messages = parseTlv(payload)
        if (messages.isEmpty()) return null

        return when {
            0x07 in messages -> decodeProximityPairing(messages[0x07]!!)
            0x12 in messages -> decodeFindMy(messages[0x12]!!)
            0x10 in messages -> decodeNearbyInfo(messages[0x10]!!)
            0x0A in messages -> decodeAirPlay()
            else -> decodeGenericApple(messages)
        }
    }

    private fun parseTlv(payload: ByteArray): Map<Int, ByteArray> {
        val result = mutableMapOf<Int, ByteArray>()
        var offset = 0
        while (offset + 1 < payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val length = payload[offset + 1].toInt() and 0xFF
            offset += 2
            if (offset + length > payload.size) break
            if (length == 0) continue
            result[type] = payload.sliceArray(offset until offset + length)
            offset += length
        }
        return result
    }

    // ── 0x07 Proximity Pairing ──────────────────────────────────────────

    private fun decodeProximityPairing(data: ByteArray): DecodedDevice {
        val values = mutableMapOf<String, Any>()

        // Byte 0: prefix/status, Byte 1: device model high, Byte 2: device model low
        val modelName: String
        if (data.size >= 3) {
            val modelCode = data.readUint16BE(1)
            modelName = PROXIMITY_MODELS[modelCode] ?: "Apple Audio (0x%04X)".format(modelCode)
            values["model_code"] = "0x%04X".format(modelCode)
        } else {
            modelName = "Apple Audio"
        }

        // Byte 3: UTP status byte
        // Byte 4: battery — upper nibble = left (0-10), lower nibble = right (0-10)
        if (data.size >= 5) {
            val battByte = data[4].toInt() and 0xFF
            val left = (battByte shr 4) and 0x0F
            val right = battByte and 0x0F
            if (left <= 10) values["batteryLeft"] = left * 10
            if (right <= 10) values["batteryRight"] = right * 10
        }

        // Byte 5: upper nibble = case battery (0-10), lower nibble = charging bits
        if (data.size >= 6) {
            val caseByte = data[5].toInt() and 0xFF
            val caseBatt = (caseByte shr 4) and 0x0F
            val chargingBits = caseByte and 0x07
            if (caseBatt <= 10) values["batteryCase"] = caseBatt * 10
            values["chargingLeft"] = chargingBits and 0x04 != 0
            values["chargingRight"] = chargingBits and 0x02 != 0
            values["chargingCase"] = chargingBits and 0x01 != 0
        }

        // Byte 6: lid open (bit 0)
        if (data.size >= 7) {
            values["lidOpen"] = (data[6].toInt() and 0x01) == 1
        }

        return DecodedDevice(
            brand = "Apple",
            model = modelName,
            modelId = "APPLE_PP",
            type = "HEADPHONES",
            values = values,
            hasSensorData = true,
        )
    }

    // ── 0x10 Nearby Info ────────────────────────────────────────────────

    private val ACTIVITY_LABELS = mapOf(
        0x00 to "idle",
        0x01 to "audio",
        0x03 to "screenOff",
        0x05 to "screenOn",
        0x07 to "screenOnLocked",
        0x09 to "screenOffLocked",
        0x0A to "calling",
        0x0B to "ringing",
        0x0D to "homescreenActive",
    )

    private fun decodeNearbyInfo(data: ByteArray): DecodedDevice {
        val values = mutableMapOf<String, Any>()

        if (data.isNotEmpty()) {
            val statusFlags = data[0].toInt() and 0xFF
            val activityNibble = (statusFlags shr 4) and 0x0F
            values["activity"] = ACTIVITY_LABELS[activityNibble] ?: "unknown(0x%X)".format(activityNibble)
        }

        // Byte 1: status flags
        if (data.size >= 2) {
            val flags = data[1].toInt() and 0xFF
            values["airdrop"] = flags and 0x08 != 0
            values["locked"] = flags and 0x04 != 0
        }

        return DecodedDevice(
            brand = "Apple",
            model = "Apple Device",
            modelId = "APPLE_NEARBY",
            type = "PHONE",
            values = values,
            hasSensorData = true,
        )
    }

    // ── 0x12 Find My ────────────────────────────────────────────────────

    private fun decodeFindMy(data: ByteArray): DecodedDevice {
        val values = mutableMapOf<String, Any>()

        if (data.isNotEmpty()) {
            val statusByte = data[0].toInt() and 0xFF
            values["ownerNearby"] = statusByte and 0x01 != 0

            values["battery"] = batteryBitsToLabel((statusByte shr 6) and 0x03)
        }

        return DecodedDevice(
            brand = "Apple",
            model = "Find My Device",
            modelId = "APPLE_FINDMY",
            type = "TRACKER",
            values = values,
            hasSensorData = true,
        )
    }

    // ── 0x0A AirPlay ────────────────────────────────────────────────────

    private fun decodeAirPlay(): DecodedDevice = DecodedDevice(
        brand = "Apple",
        model = "AirPlay Source",
        modelId = "APPLE_AIRPLAY",
        type = "AUDIO",
        values = emptyMap(),
        hasSensorData = true,
    )

    // ── Fallback for unknown Apple Continuity subtypes ───────────────────

    private fun decodeGenericApple(messages: Map<Int, ByteArray>): DecodedDevice = DecodedDevice(
        brand = "Apple",
        model = "Apple Device",
        modelId = "APPLE_CONT",
        type = "MISC",
        values = mapOf("subtypes" to messages.keys.joinToString(",") { "0x%02X".format(it) }),
        hasSensorData = true,
    )
}
