package com.wiliot.wiliotupstream.domain.scanners

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.util.TrafficRule
import com.wiliot.wiliotcore.model.BeaconWiliot
import com.wiliot.wiliotcore.utils.*
import com.wiliot.wiliotupstream.domain.service.ForegroundService
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class BLEScanner {

    private val logTag = logTag()

    private var bluetoothScanner: BluetoothLeScanner? = null

    private var bleAdvertisingIntent: Intent? = null
    private var context: WeakReference<Context>? = null
    private val filters = ArrayList<ScanFilter>()
    private val settings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setLegacy(false)
        .build()

    @OptIn(ObsoleteCoroutinesApi::class)
    @ExperimentalCoroutinesApi
    private val callback = BleScanCallback
    private var jobScanCycle: Job? = null
    private var running: Boolean = true
    private val scannerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun maybeInitLeScanner(context: Context) {
        if (null == bluetoothScanner)
            bluetoothScanner = context.bluetoothManager.adapter.bluetoothLeScanner
    }

    @ExperimentalCoroutinesApi
    @Synchronized
    fun start(context: Context) {
        this.context = context.weak()
        maybeInitLeScanner(context)

        bleAdvertisingIntent = Intent(context, ForegroundService::class.java).also { intent ->
            intent.putExtra(
                ForegroundService.INTENT_CHECK_STRING_KEY,
                "Caller:BLEScanner;+time:${System.currentTimeMillis()}"
            )
        }
        launchService()
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    private val debounceScan =
        throttle<Unit>(delayMillis = 6000L, scope = scannerScope) {
            this.context?.get()?.bluetoothManager?.adapter?.takeIf { !it.isEnabled }
                ?.run { enable() }
            filters.takeIf { it.isEmpty() }?.apply {
                if (TrafficRule.shouldListenToDataTraffic) {
                    // If enableDataTraffic is true
                    // add filters for Direct Pixel-packets and sensors
                    val wiliotManufacturerFilter = ScanFilter.Builder()
                        .setManufacturerData(Integer.parseInt("0500", 16), ByteArray(0))
                        .build()
                    val wiliotServiceFilter = ScanFilter.Builder()
                        .setServiceData(BeaconWiliot.serviceUuid, ByteArray(0))
                        .build()
                    val wiliotServiceD2Filter = ScanFilter.Builder()
                        .setServiceData(BeaconWiliot.serviceUuidD2p2, ByteArray(0))
                        .build()
                    add(wiliotManufacturerFilter)
                    add(wiliotServiceFilter)
                    add(wiliotServiceD2Filter)
                    val sensorDataFilter = ScanFilter.Builder()
                        .setServiceData(BeaconWiliot.sensorServiceUuid, ByteArray(0))
                        .build()
                    add(sensorDataFilter)
                    Reporter.log(
                        "[FILTERS] Direct Pixel-packets filters added to ScanFilters",
                        logTag
                    )
                } else {
                    Reporter.log(
                        "[FILTERS] Direct Pixel-packets and sensors filters not added to ScanFilters " +
                                "because DataOutputTrafficFilter is BRIDGES_ONLY or uploadDataTraffic is false",
                        logTag
                    )
                }
                if (TrafficRule.shouldEitherListenToDataOrEdgeTraffic) {
                    // If enableEdgeTraffic is true or enableDataTraffic is true
                    // add filter for FCC6 (this UUID used for both: retransmitted data and edge traffic)
                    val wiliotServiceFilter2 = ScanFilter.Builder()
                        .setServiceData(BeaconWiliot.serviceUuid2, ByteArray(0))
                        .build()
                    add(wiliotServiceFilter2)
                    Reporter.log(
                        "[FILTERS] Edge filters (data traffic + edge traffic) added to ScanFilters",
                        logTag
                    )
                } else {
                    Reporter.log(
                        "[FILTERS] Edge filters not added to ScanFilters " +
                                "because enableEdgeTraffic is false and enableDataTraffic is false",
                        logTag
                    )
                }
                if (TrafficRule.shouldListenToEdgeTraffic) {
                    val wiliotServiceBridgeFilter = ScanFilter.Builder()
                        .setServiceUuid(BeaconWiliot.bridgeServiceUuid)
                        .build()
                    add(wiliotServiceBridgeFilter)
                    Reporter.log(
                        "[FILTERS] Edge filters (edge traffic) added to ScanFilters",
                        logTag
                    )
                } else {
                    Reporter.log(
                        "[FILTERS] Edge filters not added to ScanFilters " +
                                "because enableEdgeTraffic is false",
                        logTag
                    )
                }
            }
            Reporter.log("[FILTERS] Filter count: ${filters.size}", logTag)
            this.context?.get()?.run {
                maybeInitLeScanner(this)
            }
            try {
                Reporter.log("Start scan (debounce scan)", logTag)
                context?.get()?.let {
                    if (!it.bluetoothManager.adapter.isEnabled) it.bluetoothManager.adapter.enable()
                    if(filters.isEmpty().not())
                        bluetoothScanner?.startScan(filters, settings, callback)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Reporter.exception(exception = e, where = logTag)
                startScan()
            }
        }

    @ExperimentalCoroutinesApi
    fun startScan() {
        Reporter.log("startScan", logTag)
        callback.startCounters()
        scannerScope.launch {
            debounceScan.invoke(Unit)
            delay(10000L)
            debounceScan.invoke(Unit)
        }
    }

    private fun launchService() {
        Reporter.log("launchService", logTag)
        context?.get()?.startForegroundService(bleAdvertisingIntent)
    }

    @ExperimentalCoroutinesApi
    @Synchronized
    fun stopAll() {
        Reporter.log("stopAll", logTag)
        stopScan()
        stopService()
        context = null
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    internal fun stopScan() {
        try {
            callback.stopAndResetCounters()
            bluetoothScanner?.stopScan(callback)
            bluetoothScanner?.flushPendingScanResults(callback)
            filters.clear()
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.localizedMessage.orEmpty())
        }
    }

    @ExperimentalCoroutinesApi
    @Synchronized
    fun restartScanner() {
        scannerScope.launch {
            jobScanCycle?.cancelAndJoin()
            running = false
            stopScan()
            stopService()
            context?.get()?.let { start(it) }
        }
    }

    private fun stopService() {
        Reporter.log("stopService", logTag)
        try {
            context?.get()?.stopService(bleAdvertisingIntent)?.also {
                Reporter.log("stopService -> stopped: $it", logTag)
            }
        } catch (ex: Exception) {
            Reporter.log("stop -> failed to stop; probably stopped earlier", logTag)
        }
    }

    companion object {
        private const val TAG = "BLEScanner"
    }
}
