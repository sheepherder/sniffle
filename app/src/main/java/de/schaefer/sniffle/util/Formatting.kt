package de.schaefer.sniffle.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shortFormat = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMAN)
private val longFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)

fun formatTimestamp(ms: Long): String =
    if (ms == 0L) "?" else shortFormat.format(Date(ms))

fun formatTimestampLong(ms: Long): String =
    if (ms == 0L) "?" else longFormat.format(Date(ms))

fun formatScanSummary(uniqueDevices: Int, sensors: Int, newPromoted: Int): String =
    buildList {
        add("$uniqueDevices Geräte gefunden")
        if (sensors > 0) add("$sensors davon Sensoren")
        if (newPromoted > 0) add("$newPromoted neu eingestuft")
    }.joinToString(", ")
