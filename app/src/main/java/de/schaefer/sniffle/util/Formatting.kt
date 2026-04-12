package de.schaefer.sniffle.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shortFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMAN)
}
private val longFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
}

fun formatTimestamp(ms: Long): String =
    if (ms == 0L) "?" else shortFormat.get()!!.format(Date(ms))

fun formatTimestampLong(ms: Long): String =
    if (ms == 0L) "?" else longFormat.get()!!.format(Date(ms))

fun formatLiveStatus(signals: Int, sighted: Int, newDevices: Int, newSensors: Int): String =
    buildList {
        add("$signals Signale")
        add("$sighted Geräte")
        if (newDevices > 0) add("$newDevices erstmalig")
        if (newSensors > 0) add(if (newSensors == 1) "1 neuer Sensor" else "$newSensors neue Sensoren")
    }.joinToString(" • ")

fun formatScanSummary(
    signals: Int,
    sighted: Int,
    newDevices: Int,
    newSensors: Int,
    promotions: Int,
): String =
    buildList {
        add("$signals Signale, $sighted Geräte")
        if (newDevices > 0) add("$newDevices erstmalig gesehen")
        if (newSensors > 0) add(if (newSensors == 1) "1 neuer Sensor" else "$newSensors neue Sensoren")
        if (promotions > 0) add(if (promotions == 1) "1 jetzt regelmäßig" else "$promotions jetzt regelmäßig")
    }.joinToString(", ")

fun parseValues(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    return try {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        obj.mapValues { it.value.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyMap()
    }
}
