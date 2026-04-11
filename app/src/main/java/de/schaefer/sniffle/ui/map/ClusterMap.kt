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
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

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
    val count: Int = 1,
    val deviceIds: List<Pair<String, String>> = emptyList(), // mac to displayName
    val isLatest: Boolean = false,
)

@Composable
fun ClusterMap(
    markers: List<ClusterMapMarker>,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    showLocationFab: Boolean = false,
    onLocationRequest: (() -> Unit)? = null,
    initialZoom: Double = 15.0,
    onDeviceTap: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var initialCenterDone by remember { mutableStateOf(false) }
    val density = context.resources.displayMetrics.density
    val clusterBitmap = remember { createClusterBitmap(context) }
    val dotCache = remember { mutableMapOf<Int, android.graphics.drawable.Drawable>() }

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
            updateMapOverlays(map, markers, myLocation, density, clusterBitmap, dotCache, onDeviceTap)
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
    density: Float,
    clusterBitmap: Bitmap,
    dotCache: MutableMap<Int, android.graphics.drawable.Drawable>,
    onDeviceTap: ((String) -> Unit)?,
) {
    map.overlays.clear()

    // Tap on empty map area closes any open InfoWindow
    map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint?) : Boolean {
            InfoWindow.closeAllInfoWindowsOn(map)
            return false
        }
        override fun longPressHelper(p: GeoPoint?) = false
    }))

    if (markers.isNotEmpty()) {
        val clusterer = RadiusMarkerClusterer(map.context)
        clusterer.setMaxClusteringZoomLevel(20)
        clusterer.setIcon(clusterBitmap)

        val markerDataMap = mutableMapOf<Marker, ClusterMapMarker>()
        val sharedInfoWindow = if (onDeviceTap != null)
            DeviceListInfoWindow(map, markerDataMap, onDeviceTap) else null

        for (m in markers.sortedBy { it.isLatest }) {
            val marker = Marker(map)
            marker.position = GeoPoint(m.lat, m.lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.icon = if (m.count > 1) {
                BitmapDrawable(map.resources, createCountDot(m.color, m.count, density))
            } else {
                dotCache.getOrPut(m.color) { createDotDrawable(m.color, 14, density) }
            }
            marker.title = m.title
            marker.snippet = m.snippet

            if (sharedInfoWindow != null) {
                markerDataMap[marker] = m
                marker.infoWindow = sharedInfoWindow
                marker.setOnMarkerClickListener { clicked, mapView ->
                    InfoWindow.closeAllInfoWindowsOn(mapView)
                    clicked.showInfoWindow()
                    true
                }
            } else {
                marker.setInfoWindow(null)
            }

            clusterer.add(marker)
        }

        map.overlays.add(clusterer)
    }

    myLocation?.let { loc ->
        val myMarker = Marker(map)
        myMarker.position = loc
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        myMarker.title = "Mein Standort"
        myMarker.icon = createMyLocationIcon(density)
        myMarker.setInfoWindow(null)
        myMarker.setOnMarkerClickListener { _, _ -> true }
        map.overlays.add(myMarker)
    }

    map.invalidate()
}

internal fun createDotDrawable(color: Int, sizeDp: Int, density: Float): android.graphics.drawable.Drawable {
    val sizePx = (sizeDp * density).toInt()
    val borderPx = (2 * density).toInt()
    val totalPx = sizePx + borderPx * 2
    val dot = GradientDrawable()
    dot.shape = GradientDrawable.OVAL
    dot.setColor(color)
    dot.setSize(sizePx, sizePx)
    val border = GradientDrawable()
    border.shape = GradientDrawable.OVAL
    border.setColor(0xFFFFFFFF.toInt())
    border.setSize(totalPx, totalPx)
    return LayerDrawable(arrayOf(border, dot)).apply {
        setLayerInset(1, borderPx, borderPx, borderPx, borderPx)
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

private fun createMyLocationIcon(density: Float): BitmapDrawable {
    val outerR = (20 * density).toInt()
    val innerR = (8 * density).toInt()
    val size = outerR * 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    // Blue halo
    canvas.drawCircle(cx, cy, outerR.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x552196F3; style = Paint.Style.FILL
    })
    // Blue ring
    canvas.drawCircle(cx, cy, (14 * density), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x882196F3.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * density
    })
    // White border
    canvas.drawCircle(cx, cy, innerR + 2 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
    })
    // Blue center
    canvas.drawCircle(cx, cy, innerR.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt(); style = Paint.Style.FILL
    })
    return BitmapDrawable(null as android.content.res.Resources?, bitmap)
}

private fun createCountDot(color: Int, count: Int, density: Float): Bitmap {
    val sizePx = (22 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx / 2f - density

    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, r - density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
    })

    val text = if (count > 999) "…" else count.toString()
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        textSize = (if (count > 99) 8 else 10) * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textH = (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(text, cx, cy - textH, textPaint)
    return bitmap
}

/**
 * Custom InfoWindow with clickable device list.
 * Single device: tap bubble → navigate.
 * Multiple devices: each row is tappable individually.
 */
private class DeviceListInfoWindow(
    private val map: MapView,
    private val dataMap: Map<Marker, ClusterMapMarker>,
    private val onDeviceTap: (String) -> Unit,
) : InfoWindow(buildRootView(map), map) {

    private val content: LinearLayout = (mView as ScrollView).getChildAt(0) as LinearLayout

    @Suppress("ClickableViewAccessibility")
    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val data = dataMap[marker] ?: return
        content.removeAllViews()

        val dp = map.resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val padSmall = (4 * dp).toInt()

        // Title
        content.addView(TextView(map.context).apply {
            text = data.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(pad, pad, pad, padSmall)
        })

        // Device rows
        for ((mac, name) in data.deviceIds) {
            content.addView(TextView(map.context).apply {
                text = "$name\n$mac"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(pad, padSmall, pad, padSmall)
                val attr = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, attr, true)
                setBackgroundResource(attr.resourceId)
                setOnClickListener {
                    onDeviceTap(mac)
                    close()
                }
            })
        }

        // Let ScrollView handle touch events normally (scrolling),
        // but request parent (MapView) not to intercept drags inside the popup
        mView.setOnTouchListener { v, e ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false // let ScrollView process the event
        }

        // After layout: push down if clipped at the top
        mView.post {
            val top = (mView.parent as? MapView)?.let {
                val loc = IntArray(2)
                mView.getLocationInWindow(loc)
                val mapLoc = IntArray(2)
                it.getLocationInWindow(mapLoc)
                loc[1] - mapLoc[1]
            } ?: 0
            if (top < 0) {
                mView.translationY = -top.toFloat()
            } else {
                mView.translationY = 0f
            }
        }
    }

    override fun onClose() {}

    companion object {
        private fun buildRootView(map: MapView): ScrollView {
            val dp = map.resources.displayMetrics.density
            return object : ScrollView(map.context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val maxH = map.height - (24 * dp).toInt()
                    val constrained = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                    super.onMeasure(widthMeasureSpec, constrained)
                }
            }.apply {
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                elevation = 4 * dp
                layoutParams = FrameLayout.LayoutParams(
                    (220 * dp).toInt(),
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                addView(LinearLayout(map.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                })
            }
        }
    }
}
