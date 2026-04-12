package de.schaefer.sniffle.ui.map

import android.annotation.SuppressLint
import android.app.Application
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.util.GeoPoint

/** Tracks last known + current location for map screens. */
class MapLocationTracker(private val application: Application) {
    private var cts = CancellationTokenSource()
    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation

    @SuppressLint("MissingPermission")
    fun refresh() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(application)
            client.lastLocation.addOnSuccessListener { loc ->
                loc?.let { _currentLocation.value = GeoPoint(it.latitude, it.longitude) }
            }
            cts.cancel()
            cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    loc?.let { _currentLocation.value = GeoPoint(it.latitude, it.longitude) }
                }
        } catch (_: Exception) {}
    }

    fun cancel() {
        cts.cancel()
    }
}
