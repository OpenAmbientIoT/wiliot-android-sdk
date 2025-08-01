package com.wiliot.wiliotupstream.domain.scanners

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import android.util.Log
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.helper.WiliotAppConfigurationSource
import com.wiliot.wiliotcore.utils.toHexString
import com.wiliot.wiliotupstream.domain.repository.BeaconDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal object BleScanCallback : ScanCallback() {
    const val logTag = "BleScanCallback"

    private val counterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var packetsCounter10minutes: Int = 0
    private var packetsCounterMinute: Int = 0

    private var counterMinJob: Job? = null
    private var counter10MinJob: Job? = null

    private val logsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rawPackets: MutableStateFlow<List<String>> = MutableStateFlow(listOf())

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        when (errorCode) {
            SCAN_FAILED_ALREADY_STARTED -> {}
            else -> {
                Log.e(logTag, "Error: $errorCode")
                Wiliot.delegate.onFail()
            }
        }
    }

    private fun generateLogItem(result: ScanResultInternal) = kotlin.runCatching {
        /*To make sure we are not breaking the app on the moment, when Native part throws*/
        "AdvA: %s RSSI:%d data:%s".format(result.device.address,
            result.rssi,
            result.scanRecord?.serviceData?.map { entry: Map.Entry<ParcelUuid, ByteArray> -> " ${entry.key} : ${entry.value.toHexString()}" })
    }.getOrNull()?.let {
        rawPackets.value += it
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toInternal(): ScanResultInternal {
        return ScanResultInternal(
            rssi = this.rssi,
            isConnectable = this.isConnectable,
            device = ScanResultInternal.Device(
                name = this.device.name,
                address = this.device.address
            )
        ).apply {
            if (this@toInternal.scanRecord != null)
                this.scanRecord = ScanResultInternal.ScanRecord(
                    deviceName = this@toInternal.scanRecord?.deviceName
                ).apply {
                    if (this@toInternal.scanRecord != null) {
                        val bytesString = this@toInternal.scanRecord?.bytes?.toHexString()
                        this.raw = bytesString
                    }
                    if (this@toInternal.scanRecord?.serviceData != null)
                        this.serviceData = this@toInternal.scanRecord?.serviceData?.toMap()
                    if ((this@toInternal.scanRecord?.manufacturerSpecificData?.size() ?: 0) > 0)
                        this.manufacturerSpecificData = this@toInternal.scanRecord?.manufacturerSpecificData
                }
        }
    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        if (null == result) return

        /* Extract required values from the ScanRecord object and release the reference,
            as it's Native object */
        val internalCopy = result.toInternal()
        /* Generate log items only if BleLogs runtime flag is enabled.
         * Don't store BleLogs flag value during the init as it could be changed in the runtime
         */
        if (WiliotAppConfigurationSource.configSource.isBleLogsEnabled())
            generateLogItem(internalCopy)
        BeaconDataRepository.judgeResult(internalCopy)

        if (BuildConfig.DEBUG) {
            Log.d(
                logTag,
                "onScanResult: ${internalCopy.device.address} rssi: ${internalCopy.rssi} data: ${internalCopy.scanRecord?.raw}"
            )
        }

        if (Wiliot.configuration.btPacketsCounterEnabled) {
            packetsCounterMinute += 1
        }
    }

    fun startCounters() {
        Reporter.log("startCounters", logTag)
        if (Wiliot.configuration.btPacketsCounterEnabled.not()) return
        launchMinuteCounter()
        launch10minutesCounter()
    }

    fun stopAndResetCounters() {
        Reporter.log("stopAndResetCounters", logTag)
        kotlin.runCatching { counterMinJob?.cancel() }
        kotlin.runCatching { counter10MinJob?.cancel() }
        packetsCounter10minutes = 0
        packetsCounterMinute = 0
        counterMinJob = null
        counter10MinJob = null
    }

    private fun launchMinuteCounter() {
        if (counterMinJob != null) {
            kotlin.runCatching { counterMinJob?.cancel() }
            counterMinJob = null
        }
        counterMinJob = counterScope.launch {
            delay((60 * 1000) - 1)
            Log.i("RAW_WLT_PKT", "$packetsCounterMinute packets collected during last minute")
            WiliotHealthMonitor.updateLastMinuteCounter(packetsCounterMinute)
            packetsCounter10minutes += packetsCounterMinute
            packetsCounterMinute = 0
            launchMinuteCounter()
        }
    }

    private fun launch10minutesCounter() {
        if (counter10MinJob != null) {
            kotlin.runCatching { counter10MinJob?.cancel() }
            counter10MinJob = null
        }
        counter10MinJob = counterScope.launch {
            delay((10 * 60 * 1000) + 1)
            Log.i(
                "RAW_WLT_PKT",
                "$packetsCounter10minutes packets collected during last 10 minutes"
            )
            WiliotHealthMonitor.updateLast10MinutesCounter(packetsCounter10minutes)
            packetsCounter10minutes = 0
            launch10minutesCounter()
        }
    }

    private fun launchLogs() {
        logsScope.launch {
            rawPackets.collectLatest {
                if (it.size < 10) return@collectLatest
                BeaconDataRepository.sendLogPayload(it)
                rawPackets.value = listOf()
            }
        }
    }

    init {
        launchLogs()
    }

}
