package de.schaefer.sniffle.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Read unsigned 16-bit big-endian at [offset]. */
fun ByteArray.readUint16BE(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

/** Read signed 16-bit big-endian at [offset]. */
fun ByteArray.readInt16BE(offset: Int): Int {
    val raw = readUint16BE(offset)
    return if (raw > 0x7FFF) raw - 0x10000 else raw
}

/** Read unsigned 16-bit little-endian at [offset]. */
fun ByteArray.readUint16LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

/** Read signed 16-bit little-endian at [offset]. */
fun ByteArray.readInt16LE(offset: Int): Int {
    val raw = readUint16LE(offset)
    return if (raw > 0x7FFF) raw - 0x10000 else raw
}

/** Read unsigned 32-bit big-endian at [offset]. */
fun ByteArray.readUint32BE(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
    ((this[offset + 1].toLong() and 0xFF) shl 16) or
    ((this[offset + 2].toLong() and 0xFF) shl 8) or
    (this[offset + 3].toLong() and 0xFF)

/** Read unsigned 24-bit little-endian at [offset]. */
fun ByteArray.readUint24LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16)

/** Read signed 24-bit little-endian at [offset]. */
fun ByteArray.readInt24LE(offset: Int): Int {
    val raw = readUint24LE(offset)
    return if (raw > 0x7FFFFF) raw - 0x1000000 else raw
}

/** Maps a 2-bit BLE battery level field (0-3) to a label. */
fun batteryBitsToLabel(bits: Int): String = when (bits) {
    0 -> "full"
    1 -> "medium"
    2 -> "low"
    3 -> "critical"
    else -> "unknown"
}

/** Read little-endian value of [len] bytes (1-4), signed or unsigned. */
fun ByteArray.readValueLE(offset: Int, len: Int, signed: Boolean): Double {
    return when (len) {
        1 -> if (signed) this[offset].toDouble() else (this[offset].toInt() and 0xFF).toDouble()
        2 -> if (signed) readInt16LE(offset).toDouble() else readUint16LE(offset).toDouble()
        3 -> if (signed) readInt24LE(offset).toDouble() else readUint24LE(offset).toDouble()
        4 -> {
            val buf = ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
            if (signed) buf.getInt().toDouble() else (buf.getInt().toLong() and 0xFFFFFFFFL).toDouble()
        }
        else -> 0.0
    }
}
