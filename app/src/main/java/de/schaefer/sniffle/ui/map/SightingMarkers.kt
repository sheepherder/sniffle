package de.schaefer.sniffle.ui.map

import androidx.compose.ui.graphics.toArgb
import de.schaefer.sniffle.data.Section
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.ui.theme.color
import de.schaefer.sniffle.util.formatTimestampLong

/** Groups a single device's sightings by exact GPS location into map markers. */
internal fun groupSightingMarkers(sightings: List<SightingEntity>, section: Section?): List<ClusterMapMarker> {
    val baseColor = (section?.color ?: Section.DEVICE.color).toArgb()
    val grouped = mutableMapOf<Pair<Double, Double>, MutableList<SightingEntity>>()
    for (s in sightings) {
        val lat = s.latitude ?: continue
        val lon = s.longitude ?: continue
        grouped.getOrPut(lat to lon) { mutableListOf() }.add(s)
    }
    if (grouped.isEmpty()) return emptyList()
    val latestSighting = sightings
        .filter { it.latitude != null && it.longitude != null }
        .maxByOrNull { it.timestamp }
    val latestLoc = latestSighting?.let { it.latitude!! to it.longitude!! }
    return grouped.map { (loc, entries) ->
        val newest = entries.maxBy { it.timestamp }
        val isLatest = loc == latestLoc
        ClusterMapMarker(
            lat = loc.first,
            lon = loc.second,
            title = formatTimestampLong(newest.timestamp),
            color = if (isLatest) baseColor else desaturate(baseColor),
            count = entries.size,
            isLatest = isLatest,
        )
    }.sortedBy { it.isLatest }
}
