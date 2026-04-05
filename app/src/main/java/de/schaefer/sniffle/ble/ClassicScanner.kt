package de.schaefer.sniffle.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.schaefer.sniffle.data.Transport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Classic Bluetooth discovery result.
 */
data class ClassicDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val deviceClass: Int?,
    val deviceClassName: String?,
)

class ClassicScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val isAvailable: Boolean
        get() = adapter?.isEnabled == true

    /**
     * Continuous Classic BT discovery as a Flow.
     * Runs discovery (~12s), pauses (~18s), repeats.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<ClassicDevice> = callbackFlow {
        val bt = adapter ?: run {
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    trySend(ClassicDevice(
                        mac = device.address,
                        name = device.name,
                        rssi = rssi,
                        deviceClass = device.bluetoothClass?.deviceClass,
                        deviceClassName = classToName(device.bluetoothClass),
                    ))
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)

        // Restart discovery periodically
        launch {
            while (isActive) {
                bt.startDiscovery()
                delay(12_000) // Discovery runs ~12s
                bt.cancelDiscovery()
                delay(18_000) // Pause before next round
            }
        }

        awaitClose {
            bt.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }

    companion object {
        fun classToName(btClass: BluetoothClass?): String? {
            if (btClass == null) return null
            return when (btClass.majorDeviceClass) {
                BluetoothClass.Device.Major.COMPUTER -> "Computer"
                BluetoothClass.Device.Major.PHONE -> "Telefon"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> when (btClass.deviceClass) {
                    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Kopfhörer"
                    BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Lautsprecher"
                    BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> "Mikrofon"
                    BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Auto-Audio"
                    BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "HiFi"
                    else -> "Audio/Video"
                }
                BluetoothClass.Device.Major.PERIPHERAL -> "Peripherie"
                BluetoothClass.Device.Major.IMAGING -> "Drucker/Scanner"
                BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                BluetoothClass.Device.Major.TOY -> "Spielzeug"
                BluetoothClass.Device.Major.HEALTH -> "Gesundheit"
                BluetoothClass.Device.Major.NETWORKING -> "Netzwerk"
                BluetoothClass.Device.Major.MISC -> "Sonstiges"
                BluetoothClass.Device.Major.UNCATEGORIZED -> null
                else -> null
            }
        }
    }
}
