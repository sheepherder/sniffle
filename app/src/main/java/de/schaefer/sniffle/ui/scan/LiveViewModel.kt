package de.schaefer.sniffle.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schaefer.sniffle.App
import de.schaefer.sniffle.ble.AdvertParser
import de.schaefer.sniffle.ble.BleScanner
import de.schaefer.sniffle.ble.ClassicDevice
import de.schaefer.sniffle.ble.ClassicScanner
import de.schaefer.sniffle.ble.ParsedAdvert
import de.schaefer.sniffle.classify.AppearanceResolver
import de.schaefer.sniffle.classify.DeviceClassifier
import de.schaefer.sniffle.classify.OuiLookup
import de.schaefer.sniffle.classify.ServiceUuidResolver
import de.schaefer.sniffle.data.DeviceCategory
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.SightingEntity
import de.schaefer.sniffle.data.Transport
import de.schaefer.sniffle.decoder.DecodedDevice
import de.schaefer.sniffle.decoder.DecoderChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class LiveDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val category: DeviceCategory,
    val brand: String?,
    val model: String?,
    val type: String?,
    val values: Map<String, Any> = emptyMap(),
    val company: String?,
    val appearance: String?,
    val serviceHints: List<String> = emptyList(),
    val transport: Transport = Transport.BLE,
    val guessedType: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
)

data class LiveState(
    val sensors: List<LiveDevice> = emptyList(),
    val devices: List<LiveDevice> = emptyList(),
    val mystery: List<LiveDevice> = emptyList(),
    val once: List<LiveDevice> = emptyList(),
    val onceExpanded: Boolean = false,
    val totalCount: Int = 0,
)

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as App).database.deviceDao()
    private val bleScanner = BleScanner(application)
    private val classicScanner = ClassicScanner(application)

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    // Live devices map: MAC → LiveDevice
    private val liveDevices = mutableMapOf<String, LiveDevice>()

    init {
        OuiLookup.init(application)
    }

    fun startScanning() {
        if (bleScanner.isAvailable) {
            viewModelScope.launch {
                bleScanner.scan().collect { advert ->
                    handleBleAdvert(advert)
                }
            }
        }
        if (classicScanner.isAvailable) {
            viewModelScope.launch {
                classicScanner.scan().collect { device ->
                    handleClassicDevice(device)
                }
            }
        }
    }

    fun toggleOnceExpanded() {
        _state.value = _state.value.copy(onceExpanded = !_state.value.onceExpanded)
    }

    private suspend fun handleBleAdvert(advert: ParsedAdvert) {
        val decoded = DecoderChain.decode(advert)
        val ouiVendor = OuiLookup.lookup(advert.mac)
        val company = DeviceClassifier.companyFromId(advert.manufacturerData) ?: ouiVendor
        val appearance = AppearanceResolver.resolve(advert.appearance)
        val serviceHints = ServiceUuidResolver.resolve(advert.serviceUuids)
        val guessedType = DeviceClassifier.guessTypeFromName(advert.name)

        // Determine or load category
        val existing = db.getDevice(advert.mac)
        val category = existing?.category
            ?: DeviceClassifier.classifyBle(advert, decoded)

        val today = LocalDate.now().toString()
        val firstSeen = existing?.firstSeenDate ?: today

        val live = LiveDevice(
            mac = advert.mac,
            name = advert.name ?: existing?.name,
            rssi = advert.rssi,
            category = category,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            type = decoded?.type ?: existing?.deviceType,
            values = decoded?.values ?: emptyMap(),
            company = company ?: existing?.company,
            appearance = appearance ?: existing?.appearance,
            serviceHints = serviceHints,
            transport = Transport.BLE,
            guessedType = guessedType,
        )
        liveDevices[advert.mac] = live
        updateState()

        // Persist
        persistDevice(advert.mac, live, decoded, firstSeen, today)
        persistSighting(advert.mac, advert.rssi, decoded)

        // Check promotion
        if (category == DeviceCategory.ONCE) {
            checkPromotion(advert.mac, live)
        }
    }

    private suspend fun handleClassicDevice(device: ClassicDevice) {
        val existing = db.getDevice(device.mac)
        val category = existing?.category ?: DeviceCategory.ONCE
        val today = LocalDate.now().toString()
        val firstSeen = existing?.firstSeenDate ?: today

        val live = LiveDevice(
            mac = device.mac,
            name = device.name ?: existing?.name,
            rssi = device.rssi,
            category = category,
            brand = null,
            model = device.deviceClassName,
            type = null,
            company = OuiLookup.lookup(device.mac),
            appearance = device.deviceClassName,
            transport = Transport.CLASSIC,
            guessedType = DeviceClassifier.guessTypeFromName(device.name),
        )
        liveDevices[device.mac] = live
        updateState()

        db.upsertDevice(DeviceEntity(
            mac = device.mac,
            name = device.name ?: existing?.name,
            brand = existing?.brand,
            model = device.deviceClassName ?: existing?.model,
            deviceType = existing?.deviceType,
            transport = Transport.CLASSIC,
            category = category,
            appearance = device.deviceClassName,
            company = OuiLookup.lookup(device.mac),
            firstSeenDate = firstSeen,
            latestSeenDate = today,
            note = existing?.note,
            notified = existing?.notified ?: false,
        ))
        persistSighting(device.mac, device.rssi, null)

        if (category == DeviceCategory.ONCE) {
            checkPromotion(device.mac, live)
        }
    }

    private suspend fun persistDevice(
        mac: String, live: LiveDevice, decoded: DecodedDevice?,
        firstSeen: String, today: String,
    ) {
        val existing = db.getDevice(mac)
        db.upsertDevice(DeviceEntity(
            mac = mac,
            name = live.name ?: existing?.name,
            brand = decoded?.brand ?: existing?.brand,
            model = decoded?.model ?: existing?.model,
            modelId = decoded?.modelId ?: existing?.modelId,
            deviceType = decoded?.type ?: existing?.deviceType,
            transport = live.transport,
            category = live.category,
            appearance = live.appearance ?: existing?.appearance,
            company = live.company ?: existing?.company,
            firstSeenDate = firstSeen,
            latestSeenDate = today,
            note = existing?.note,
            notified = existing?.notified ?: false,
        ))
    }

    private suspend fun persistSighting(mac: String, rssi: Int, decoded: DecodedDevice?) {
        val valuesJson = decoded?.values?.takeIf { it.isNotEmpty() }?.let { vals ->
            try {
                Json.encodeToString(vals.mapValues { it.value.toString() })
            } catch (_: Exception) { null }
        }
        db.insertSighting(SightingEntity(
            mac = mac,
            timestamp = System.currentTimeMillis(),
            rssi = rssi,
            decodedValues = valuesJson,
        ))
    }

    private suspend fun checkPromotion(mac: String, live: LiveDevice) {
        val days = db.countDistinctDays(mac)
        if (days >= 3) {
            val identity = DeviceClassifier.hasIdentity(
                name = live.name,
                ouiVendor = live.company,
                appearance = live.appearance,
                serviceHints = live.serviceHints,
                company = live.company,
                deviceClassName = if (live.transport == Transport.CLASSIC) live.model else null,
            )
            val newCategory = DeviceClassifier.promotedCategory(identity)
            db.updateCategory(mac, newCategory)
            liveDevices[mac] = live.copy(category = newCategory)
            updateState()
        }
    }

    private fun updateState() {
        // Remove devices not seen in 60s
        val cutoff = System.currentTimeMillis() - 60_000
        liveDevices.entries.removeIf { it.value.lastSeen < cutoff }

        val all = liveDevices.values.toList()
        _state.value = _state.value.copy(
            sensors = all.filter { it.category == DeviceCategory.SENSOR }.sortedByDescending { it.rssi },
            devices = all.filter { it.category == DeviceCategory.DEVICE }.sortedByDescending { it.rssi },
            mystery = all.filter { it.category == DeviceCategory.MYSTERY }.sortedByDescending { it.rssi },
            once = all.filter { it.category == DeviceCategory.ONCE }.sortedByDescending { it.rssi },
            totalCount = all.size,
        )
    }
}
