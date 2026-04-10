package de.schaefer.sniffle.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

@Composable
fun ShowAllChip(showAll: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = showAll,
        onClick = onClick,
        label = { Text(if (showAll) "Alle" else "Letzte") },
        modifier = modifier,
    )
}

data class ClusterMapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val title: String,
    val snippet: String,
    val color: Int,
)

@Composable
fun ClusterMap(
    markers: List<ClusterMapMarker>,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    showLocationFab: Boolean = false,
    onLocationRequest: (() -> Unit)? = null,
    initialZoom: Double = 15.0,
    onInfoWindowTap: ((ClusterMapMarker) -> Unit)? = null,
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var initialCenterDone by remember { mutableStateOf(false) }
    val clusterBitmap = remember { createClusterBitmap(context) }
    val dotCache = remember { mutableMapOf<Int, android.graphics.drawable.Drawable>() }
    val markerDataMap = remember { mutableMapOf<Marker, ClusterMapMarker>() }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(initialZoom)
                    mapView = this
                }
            },
        )

        // Update markers when data or mapView changes
        LaunchedEffect(markers, myLocation, mapView) {
            val map = mapView ?: return@LaunchedEffect
            updateMapOverlays(
                map, markers, myLocation, context, clusterBitmap, dotCache,
                markerDataMap, onInfoWindowTap,
            )
            if (!initialCenterDone) {
                val allPoints = buildList {
                    myLocation?.let { add(it) }
                    markers.forEach { add(GeoPoint(it.lat, it.lon)) }
                }
                if (allPoints.isNotEmpty()) {
                    // Wait for layout if map has no dimensions yet
                    repeat(10) {
                        if (map.width > 0 && map.height > 0) return@repeat
                        kotlinx.coroutines.delay(50)
                    }
                    if (map.width > 0 && map.height > 0) {
                        val lats = allPoints.map { it.latitude }
                        val lons = allPoints.map { it.longitude }
                        val latSpan = (lats.max() - lats.min()).coerceAtLeast(0.0005)
                        val lonSpan = (lons.max() - lons.min()).coerceAtLeast(0.0005)
                        val centerLat = (lats.max() + lats.min()) / 2
                        val centerLon = (lons.max() + lons.min()) / 2
                        map.zoomToBoundingBox(
                            BoundingBox(
                                centerLat + latSpan / 2, centerLon + lonSpan / 2,
                                centerLat - latSpan / 2, centerLon - lonSpan / 2,
                            ),
                            false, 50
                        )
                    }
                    initialCenterDone = true
                }
            }
        }

        if (showLocationFab) {
            FloatingActionButton(
                onClick = {
                    onLocationRequest?.invoke()
                    myLocation?.let { loc -> mapView?.controller?.animateTo(loc) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mein Standort")
            }
        }
    }
}

private fun updateMapOverlays(
    map: MapView,
    markers: List<ClusterMapMarker>,
    myLocation: GeoPoint?,
    context: android.content.Context,
    clusterBitmap: Bitmap,
    dotCache: MutableMap<Int, android.graphics.drawable.Drawable>,
    markerDataMap: MutableMap<Marker, ClusterMapMarker>,
    onInfoWindowTap: ((ClusterMapMarker) -> Unit)?,
) {
    map.overlays.clear()
    markerDataMap.clear()

    myLocation?.let { loc ->
        val myMarker = Marker(map)
        myMarker.position = loc
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        myMarker.title = "Mein Standort"
        myMarker.icon = dotCache.getOrPut(0xFF2196F3.toInt()) { createDotDrawable(0xFF2196F3.toInt(), 24) }
        myMarker.setOnMarkerClickListener { _, _ -> false }
        map.overlays.add(myMarker)
    }

    if (markers.isNotEmpty()) {
        val clusterer = RadiusMarkerClusterer(context)
        clusterer.setIcon(clusterBitmap)

        for (m in markers) {
            val marker = Marker(map)
            marker.position = GeoPoint(m.lat, m.lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = dotCache.getOrPut(m.color) { createDotDrawable(m.color, 20) }
            marker.title = m.title
            marker.snippet = m.snippet

            if (onInfoWindowTap != null) {
                markerDataMap[marker] = m
                marker.infoWindow = TappableInfoWindow(map, marker, markerDataMap, onInfoWindowTap)
            }

            clusterer.add(marker)
        }

        map.overlays.add(clusterer)
    }

    map.invalidate()
}

internal fun createDotDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val dot = GradientDrawable()
    dot.shape = GradientDrawable.OVAL
    dot.setColor(color)
    dot.setSize(sizeDp, sizeDp)
    val border = GradientDrawable()
    border.shape = GradientDrawable.OVAL
    border.setColor(0xFFFFFFFF.toInt())
    border.setSize(sizeDp + 4, sizeDp + 4)
    return LayerDrawable(arrayOf(border, dot)).apply {
        setLayerInset(1, 2, 2, 2, 2)
    }
}

private fun createClusterBitmap(context: android.content.Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xCC455A64.toInt()
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }

    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - density
    canvas.drawCircle(cx, cy, r, circlePaint)
    canvas.drawCircle(cx, cy, r, borderPaint)

    return bitmap
}

private class TappableInfoWindow(
    mapView: MapView,
    private val owner: Marker,
    private val dataMap: Map<Marker, ClusterMapMarker>,
    private val onTap: (ClusterMapMarker) -> Unit,
) : MarkerInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, mapView) {
    override fun onOpen(item: Any?) {
        super.onOpen(item)
        val data = dataMap[owner] ?: return
        mView.setOnClickListener { onTap(data) }
    }
}
