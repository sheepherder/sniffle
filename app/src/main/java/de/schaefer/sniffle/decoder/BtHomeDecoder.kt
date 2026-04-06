package de.schaefer.sniffle.decoder

import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.util.readValueLE

/**
 * BTHome v1/v2 decoder.
 * Spec: https://bthome.io/format/
 *
 * Recognizes service UUID 0xFCD2 (v2) or 0x181C (v1).
 */
object BtHomeDecoder : Decoder {

    private const val BTHOME_V2_UUID = "0000fcd2-0000-1000-8000-00805f9b34fb"
    private const val BTHOME_V1_UUID = "0000181c-0000-1000-8000-00805f9b34fb"

    override fun decode(advert: ParsedAdvert): DecodedDevice? {
        val payload = advert.serviceData[BTHOME_V2_UUID]
            ?: advert.serviceData[BTHOME_V1_UUID]
            ?: return null

        if (payload.isEmpty()) return null

        val version = if (advert.serviceData.containsKey(BTHOME_V2_UUID)) 2 else 1
        val values = mutableMapOf<String, Any>()
        var offset = 0

        if (version == 2) {
            // Byte 0: device info (bit 0: encryption, bits 5-7: version)
            val info = payload[0].toInt() and 0xFF
            val encrypted = info and 0x01 != 0
            if (encrypted) return null // encrypted payloads not supported yet
            offset = 1
        }

        while (offset < payload.size) {
            val objId = payload[offset].toInt() and 0xFF
            offset++
            val def = OBJECTS[objId] ?: break

            if (offset + def.len > payload.size) break

            val raw = payload.readValueLE(offset, def.len, def.signed)
            offset += def.len

            val value: Any = if (def.factor != 1.0) {
                raw * def.factor
            } else {
                raw.toLong()
            }

            values[def.name] = value
        }

        if (values.isEmpty()) return null

        val hasSensor = values.keys.any { it in SENSOR_KEYS }

        return DecodedDevice(
            brand = "BTHome",
            model = advert.name ?: "BTHome v$version",
            modelId = "BTHOME_V$version",
            type = if (hasSensor) "THB" else "BCON",
            values = values,
            hasSensorData = hasSensor,
        )
    }

    private val SENSOR_KEYS = setOf(
        "temperature", "humidity", "pressure", "battery", "voltage",
        "co2", "pm25", "pm10", "illuminance", "moisture", "power",
        "energy", "weight", "distance", "count",
    )


    private data class ObjDef(val name: String, val len: Int, val factor: Double = 1.0, val signed: Boolean = false)

    private val OBJECTS = mapOf(
        0x00 to ObjDef("packet_id", 1),
        0x01 to ObjDef("battery", 1),                       // %
        0x02 to ObjDef("temperature", 2, 0.01, true),       // °C
        0x03 to ObjDef("humidity", 2, 0.01),                // %
        0x04 to ObjDef("pressure", 3, 0.01),                // hPa
        0x05 to ObjDef("illuminance", 3, 0.01),             // lux
        0x06 to ObjDef("weight_kg", 2, 0.01),               // kg
        0x07 to ObjDef("weight_lb", 2, 0.01),               // lb
        0x08 to ObjDef("dewpoint", 2, 0.01, true),          // °C
        0x09 to ObjDef("count", 1),
        0x0A to ObjDef("energy", 3, 0.001),                 // kWh
        0x0B to ObjDef("power", 3, 0.01),                   // W
        0x0C to ObjDef("voltage", 2, 0.001),                // V
        0x0D to ObjDef("pm25", 2),                           // µg/m³
        0x0E to ObjDef("pm10", 2),                           // µg/m³
        0x12 to ObjDef("co2", 2),                            // ppm
        0x13 to ObjDef("tvoc", 2),                           // µg/m³
        0x14 to ObjDef("moisture", 2, 0.01),                // %
        0x2E to ObjDef("humidity", 1),                       // %
        0x3A to ObjDef("button", 1),
        0x3C to ObjDef("dimmer", 2),
        0x3D to ObjDef("count_2", 2),
        0x3E to ObjDef("count_4", 4),
        0x40 to ObjDef("distance_mm", 2),                   // mm
        0x41 to ObjDef("distance_m", 2, 0.1),               // m
        0x42 to ObjDef("duration", 3, 0.001),               // s
        0x43 to ObjDef("current", 2, 0.001),                // A
        0x44 to ObjDef("speed", 2, 0.01),                   // m/s
        0x45 to ObjDef("temperature_01", 2, 0.1, true),     // °C
        0x46 to ObjDef("uv_index", 1, 0.1),
        0x47 to ObjDef("volume_l", 2, 0.1),                 // L
        0x48 to ObjDef("volume_ml", 2),                     // mL
        0x49 to ObjDef("flow_rate", 2, 0.001),              // m³/h
        0x4A to ObjDef("voltage_01", 2, 0.1),               // V
        0x4B to ObjDef("gas", 3, 0.001),                    // m³
        0x4C to ObjDef("gas_4", 4, 0.001),                  // m³
        0x4D to ObjDef("energy_4", 4, 0.001),               // kWh
        0x4E to ObjDef("volume_0001", 4, 0.001),            // L
        0x4F to ObjDef("water", 4, 0.001),                  // L
        0x50 to ObjDef("timestamp", 4),
        0x51 to ObjDef("acceleration", 2, 0.001),           // m/s²
        0x52 to ObjDef("gyroscope", 2, 0.001),              // °/s
        // Binary sensors (1 byte)
        0x0F to ObjDef("generic_bool", 1),
        0x10 to ObjDef("power_on", 1),
        0x11 to ObjDef("opening", 1),
        0x15 to ObjDef("battery_low", 1),
        0x16 to ObjDef("battery_charging", 1),
        0x17 to ObjDef("co", 1),
        0x18 to ObjDef("cold", 1),
        0x19 to ObjDef("connectivity", 1),
        0x1A to ObjDef("door", 1),
        0x1B to ObjDef("garage_door", 1),
        0x1C to ObjDef("gas_detected", 1),
        0x1D to ObjDef("heat", 1),
        0x1E to ObjDef("light", 1),
        0x1F to ObjDef("lock", 1),
        0x20 to ObjDef("moisture_detected", 1),
        0x21 to ObjDef("motion", 1),
        0x22 to ObjDef("moving", 1),
        0x23 to ObjDef("occupancy", 1),
        0x24 to ObjDef("plug", 1),
        0x25 to ObjDef("presence", 1),
        0x26 to ObjDef("problem", 1),
        0x27 to ObjDef("running", 1),
        0x28 to ObjDef("safety", 1),
        0x29 to ObjDef("smoke", 1),
        0x2A to ObjDef("sound", 1),
        0x2B to ObjDef("tamper", 1),
        0x2C to ObjDef("vibration", 1),
        0x2D to ObjDef("window", 1),
    )
}
