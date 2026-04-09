package de.schaefer.sniffle.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.schaefer.sniffle.data.SightingEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MAX_ZOOM = Zoom.max(Zoom.fixed(50f), Zoom.Content)

private val FIT_DATA_RANGE = object : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        if (minY == maxY) minY - 1 else minY
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
        if (minY == maxY) maxY + 1 else maxY
}

internal data class ParsedSensorData(
    val entries: List<Pair<Long, Map<String, String>>>,
    val keys: List<String>,
    val baseTime: Long,
    val xStep: Double,
    val timeFormatPattern: String,
)

internal fun parseSensorData(sightings: List<SightingEntity>): ParsedSensorData? {
    val entries = sightings.asReversed()
        .map { it.timestamp to parseValues(it.decodedValues) }
        .filter { it.second.isNotEmpty() }
    if (entries.isEmpty()) return null

    val seen = linkedSetOf<String>()
    for ((_, vals) in entries) {
        for ((k, v) in vals) {
            if (v.toDoubleOrNull() != null) seen.add(k)
        }
    }
    val keys = seen.toList()
    if (keys.isEmpty()) return null

    val baseTime = entries.first().first
    val xStep = entries.zipWithNext { a, b -> (b.first - a.first).toDouble() }
        .filter { it > 0.0 }
        .minOrNull() ?: 1.0
    val spanMs = entries.last().first - baseTime
    val pattern = if (spanMs > 86_400_000L) "dd.MM HH:mm" else "HH:mm"

    return ParsedSensorData(entries, keys, baseTime, xStep, pattern)
}

@Composable
internal fun SensorCharts(
    sightings: List<SightingEntity>,
    onOpenChart: ((key: String) -> Unit)? = null,
) {
    val data = remember(sightings) { parseSensorData(sightings) } ?: return

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "Werte-Verlauf",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        val scrollState = rememberVicoScrollState(scrollEnabled = true)
        val zoomState = rememberVicoZoomState(
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content,
            maxZoom = MAX_ZOOM,
        )

        for (key in data.keys) {
            key(key) {
                SensorLineChart(
                    data = data,
                    key = key,
                    chartHeight = 150.dp,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    onFullscreen = if (onOpenChart != null) {{ onOpenChart(key) }} else null,
                )
            }
        }
    }
}

@Composable
internal fun SensorChartFullscreen(
    sightings: List<SightingEntity>,
    key: String,
) {
    val data = remember(sightings) { parseSensorData(sightings) } ?: return

    val scrollState = rememberVicoScrollState(scrollEnabled = true)
    val zoomState = rememberVicoZoomState(
        initialZoom = Zoom.Content,
        minZoom = Zoom.Content,
        maxZoom = MAX_ZOOM,
    )

    SensorLineChart(
        data = data,
        key = key,
        chartHeight = null,
        scrollState = scrollState,
        zoomState = zoomState,
        onFullscreen = null,
    )
}

@Composable
private fun SensorLineChart(
    data: ParsedSensorData,
    key: String,
    chartHeight: Dp?,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    onFullscreen: (() -> Unit)?,
) {
    val points = remember(data.entries, key) {
        data.entries.mapNotNull { (ts, vals) ->
            vals[key]?.toDoubleOrNull()?.let { (ts - data.baseTime).toDouble() to it }
        }
    }
    if (points.size < 2) return

    Row(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        if (onFullscreen != null) {
            IconButton(onClick = onFullscreen, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Vollbild",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val modelProducer = remember(key) { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = points.map { it.first },
                    y = points.map { it.second },
                )
            }
        }
    }

    val timeFormat = remember(data.timeFormatPattern) {
        SimpleDateFormat(data.timeFormatPattern, Locale.getDefault())
    }
    val xFormatter = remember(data.baseTime, data.timeFormatPattern) {
        CartesianValueFormatter { _, x, _ ->
            timeFormat.format(Date(x.toLong() + data.baseTime))
        }
    }

    val chartModifier = if (chartHeight != null) {
        Modifier.fillMaxWidth().height(chartHeight)
    } else {
        Modifier.fillMaxSize()
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = FIT_DATA_RANGE),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter),
            getXStep = { data.xStep },
        ),
        modelProducer = modelProducer,
        scrollState = scrollState,
        zoomState = zoomState,
        modifier = chartModifier.padding(bottom = 4.dp),
    )
}

internal fun parseValues(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    return try {
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        obj.mapValues { it.value.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyMap()
    }
}
