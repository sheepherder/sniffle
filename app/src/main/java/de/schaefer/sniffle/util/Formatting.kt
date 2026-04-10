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

fun formatScanSummary(uniqueDevices: Int, sensors: Int, newPromoted: Int): String =
    buildList {
        add("$uniqueDevices Geräte gefunden")
        if (sensors > 0) add("$sensors davon Sensoren")
        if (newPromoted > 0) add("$newPromoted neu eingestuft")
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
