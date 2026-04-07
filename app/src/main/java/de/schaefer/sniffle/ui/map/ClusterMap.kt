package de.schaefer.sniffle.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
    initialCenter: GeoPoint? = null,
    initialZoom: Double = 15.0,
    onInfoWindowTap: ((ClusterMapMarker) -> Unit)? = null,
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var initialCenterDone by remember { mutableStateOf(false) }
    val clusterBitmap = remember { createClusterBitmap(context) }
    val dotCache = remember { mutableMapOf<Int, android.graphics.drawable.Drawable>() }

    // Keep a lookup from Marker to our data so the InfoWindow can call back
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
            update = { map ->
                map.overlays.clear()
                markerDataMap.clear()

                // Collect all points for bounding box (markers + my location)
                val allPoints = mutableListOf<GeoPoint>()

                // My location dot
                myLocation?.let { loc ->
                    val myMarker = Marker(map)
                    myMarker.position = loc
                    myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    myMarker.title = "Mein Standort"
                    myMarker.icon = createDotDrawable(0xFF2196F3.toInt(), 24)
                    myMarker.setOnMarkerClickListener { _, _ -> false }
                    map.overlays.add(myMarker)
                    allPoints.add(loc)
                }

                if (markers.isNotEmpty()) {
                    val clusterer = RadiusMarkerClusterer(context)
                    clusterer.setIcon(clusterBitmap)

                    for (m in markers) {
                        val marker = Marker(map)
                        val pos = GeoPoint(m.lat, m.lon)
                        marker.position = pos
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.icon = dotCache.getOrPut(m.color) { createDotDrawable(m.color, 20) }
                        marker.title = m.title
                        marker.snippet = m.snippet

                        if (onInfoWindowTap != null) {
                            markerDataMap[marker] = m
                            marker.infoWindow = TappableInfoWindow(map, marker, markerDataMap, onInfoWindowTap)
                        }

                        clusterer.add(marker)
                        allPoints.add(pos)
                    }

                    map.overlays.add(clusterer)
                }

                // Fit to all points on first load
                if (!initialCenterDone && allPoints.isNotEmpty()) {
                    if (allPoints.size == 1) {
                        map.controller.setCenter(allPoints[0])
                        map.controller.setZoom(initialZoom)
                    } else {
                        val lats = allPoints.map { it.latitude }
                        val lons = allPoints.map { it.longitude }
                        map.zoomToBoundingBox(
                            BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()),
                            true, 50
                        )
                    }
                    initialCenterDone = true
                }

                map.invalidate()
            }
        )

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
