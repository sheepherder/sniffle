package de.schaefer.sniffle.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BleScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    val isAvailable: Boolean
        get() = adapter?.isEnabled == true && scanner != null

    /**
     * Continuous BLE scan as a Flow of ParsedAdvert.
     * Emits every advertisement received. Stops when the Flow is cancelled.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<ParsedAdvert> = callbackFlow {
        val leScanner = scanner ?: run {
            close()
            return@callbackFlow
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(AdvertParser.parse(result))
            }
        }

        leScanner.startScan(null, settings, callback)

        // Android throttles BLE scans to opportunistic after 30 min.
        // Restart every 25 min to stay in LOW_LATENCY mode.
        launch {
            while (isActive) {
                delay(25 * 60 * 1000L)
                try {
                    leScanner.stopScan(callback)
                    leScanner.startScan(null, settings, callback)
                } catch (_: Exception) {
                    break
                }
            }
        }

        awaitClose {
            leScanner.stopScan(callback)
        }
    }
}
