package de.schaefer.sniffle.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * Application-scope BLE + Classic BT scanner.
 *
 * Both the live UI (ScanViewModel) and the background ScanWorker collect from
 * [bleResults] and [classicResults]. Android sees exactly ONE physical scan
 * regardless of how many subscribers; upstream scanning stops automatically
 * when the last subscriber cancels (SharingStarted.WhileSubscribed).
 */
class ScanCoordinator(context: Context) {
    private val bleScanner = BleScanner(context)
    private val classicScanner = ClassicScanner(context)

    // Intentionally process-scoped (never cancelled) — matches App singleton lifetime.
    // WhileSubscribed stops the *upstream* flow when no subscribers remain,
    // so BLE stops; only the sharing infrastructure coroutine stays alive.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val bleAvailable: Boolean get() = bleScanner.isAvailable
    val classicAvailable: Boolean get() = classicScanner.isAvailable

    val bleResults: SharedFlow<ParsedAdvert> = bleScanner.scan()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), replay = 0)

    val classicResults: SharedFlow<ClassicDevice> = classicScanner.scan()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), replay = 0)
}
