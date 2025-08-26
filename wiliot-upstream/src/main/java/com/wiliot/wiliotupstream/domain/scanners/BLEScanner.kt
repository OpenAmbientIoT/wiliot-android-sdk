package com.wiliot.wiliotupstream.domain.scanners

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wiliot.wiliotcore.config.util.TrafficRule
import com.wiliot.wiliotcore.model.BeaconWiliot
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.bluetoothManager
import com.wiliot.wiliotcore.utils.every
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotupstream.domain.service.ForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class BLEScanner {

    private val logTag = logTag()

    companion object {
        private const val RESTART_PERIOD = 2 * 60 * 1000L // 2 minutes
    }

    private var bluetoothScanner: BluetoothLeScanner? = null

    private var bleAdvertisingIntent: Intent? = null
    private var context: WeakReference<Context>? = null
    private val filters = ArrayList<ScanFilter>()
    private val settings: ScanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setLegacy(false)
        .build()

    private var scannerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicRestartJob: Job? = null

    private var started: Boolean = false
        set(value) {
            field = value
            if (value) {
                startPeriodicRestartJob()
            } else {
                cancelPeriodicRestartJob()
            }
        }

    @OptIn(ObsoleteCoroutinesApi::class)
    @ExperimentalCoroutinesApi
    private val callback = BleScanCallback

    //==============================================================================================
    // *** Starter API ***
    //==============================================================================================

    // region [Starter API]

    /**
     * Starts BLE scanning and foreground service; used as an API in WiliotStarter
     */
    @ExperimentalCoroutinesApi
    @Synchronized
    internal fun start(context: Context) {
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

    @ExperimentalCoroutinesApi
    @Synchronized
    internal fun stopAll() {
        Reporter.log("stopAll", logTag)
        stopScan()
        stopService()
        context = null
    }

    // endregion

    //==============================================================================================
    // *** Service API ***
    //==============================================================================================

    // region [Service API]

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    internal fun startScan() {
        Reporter.log("startScan", logTag)
        callback.startCounters()
        prepareScannerAndStart()
        started = true
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    internal fun stopScan() {
        Reporter.log("stopScan", logTag)
        started = false
        try {
            callback.stopAndResetCounters()
            bluetoothScanner?.stopScan(callback)
            bluetoothScanner?.flushPendingScanResults(callback)
            filters.clear()
        } catch (e: IllegalStateException) {
            Log.e(logTag, e.localizedMessage.orEmpty())
            Reporter.exception("Error during stopScan", e, logTag)
        }
    }

    // endregion

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    @OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private fun softPeriodicRestart() {
        Reporter.log("softPeriodicRestart", logTag)
        scannerScope.launch {
            // stop scanner
            try {
                bluetoothScanner?.stopScan(callback)
                bluetoothScanner?.flushPendingScanResults(callback)
                filters.clear()
            } catch (e: IllegalStateException) {
                Reporter.exception("Error during softPeriodicRestart", e, logTag)
            }
            // delay
            delay(1000) // 1 second
            // start scanner
            prepareScannerAndStart()
        }
    }

    private fun startPeriodicRestartJob() {
        Reporter.log("startPeriodicRestartJob", logTag)
        periodicRestartJob?.cancel()
        periodicRestartJob = scannerScope.every(millis = RESTART_PERIOD, initialDelay = RESTART_PERIOD) {
            Reporter.log("Periodic restart of scanning", logTag)
            softPeriodicRestart()
        }
    }

    private fun cancelPeriodicRestartJob() {
        Reporter.log("cancelPeriodicRestartJob", logTag)
        periodicRestartJob?.cancel()
        periodicRestartJob = null
    }

    private fun maybeInitLeScanner(context: Context) {
        if (null == bluetoothScanner)
            bluetoothScanner = context.bluetoothManager.adapter.bluetoothLeScanner
    }

    private fun initFilters() {
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
    }

    @SuppressLint("MissingPermission")
    private fun prepareScannerAndStart() {
        this.context?.get()?.bluetoothManager?.adapter?.takeIf { !it.isEnabled }
            ?.run { enable() }
        initFilters()
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

    private fun launchService() {
        Reporter.log("launchService", logTag)
        context?.get()?.startForegroundService(bleAdvertisingIntent)
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

    // endregion

}
