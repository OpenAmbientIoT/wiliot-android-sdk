package com.wiliot.wiliotdfu

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.os.ParcelUuid
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.*

/**
 * BT Device scanner with filtering by id.
 *
 * @param [desiredId] should contain ID of Bridge that should be found;
 * @param [onFound] callback to get [BluetoothDevice] if found;
 * @param [onFailure] callback to handle negative case when device was not found. Note, that scanning session
 * lasts during 20 sec. If device was not found during this period, [onFailure] will be invoked.
 */
class BridgeDfuScanner(
    val desiredId: String,
    val onFound: (BluetoothDevice) -> Unit,
    val onFailure: () -> Unit,
) {

    private var bluetoothScanner: BluetoothLeScanner? = null
    private val asyncScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var runningJob: Job? = null

    private var bleCallback: ScanCallback? = null

    companion object {
        private const val SCAN_DURATION_MILLIS = 20_000L
        private val bridgeServiceUuid: ParcelUuid =
            ParcelUuid.fromString("0000180a-0000-1000-8000-00805f9b34fb")

        private val settings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        private val filters = mutableListOf<ScanFilter>(
            ScanFilter.Builder()
                .setServiceUuid(bridgeServiceUuid)
                .build(),
        )
    }

    private var found: Boolean = false
    private var scanning: Boolean = false

    /**
     * Perform scan cycle trying to found device with [desiredId].
     *
     * Note, that Bluetooth permissions should be granted before accessing this function.
     */
    @SuppressLint("MissingPermission")
    fun doScanCycle() {
        if (scanning) return
        if (found) found = false // in case of retry
        scanning = true
        if (bleCallback == null) {
            bleCallback = BridgesScanCallback { btDevice ->
                found = true
                onFound.invoke(btDevice)
                dispose()
            }
        }
        if (bluetoothScanner == null) {
            bluetoothScanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
        }

        /*
         * if job is running then do nothing. there is restriction to start new ble scan
         * more than once in 6 seconds
         */
        if (true == runningJob?.isActive)
            return

        runningJob = asyncScope.launch {
            ensureActive()
            try {
                bluetoothScanner?.startScan(filters, settings, bleCallback)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
            ensureActive()
            try {
                delay(SCAN_DURATION_MILLIS)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            ensureActive()
            bluetoothScanner?.stopScan(bleCallback)
            if (!found) onFailure.invoke()
            ensureActive()
        }.apply {
            invokeOnCompletion {
                bluetoothScanner?.stopScan(bleCallback)
                if ((it is CancellationException && it.message == "DISPOSE").not()) {
                    if (!found) onFailure.invoke()
                }
                scanning = false
            }
        }
    }

    /**
     * Force stop scanner process.
     * Do not forget to [dispose] object of [BridgeDfuScanner] when it no longer needed.
     */
    @SuppressLint("MissingPermission")
    fun dispose() {
        bluetoothScanner?.stopScan(bleCallback)
        runningJob?.cancel(CancellationException("DISPOSE"))
        found = false
        scanning = false
    }

    inner class BridgesScanCallback(
        private var foundCallback: (BluetoothDevice) -> Unit,
    ) : ScanCallback() {

        private val logTagLocal = logTag()

        private fun getIdPartFromName(name: String): String {
            try {
                if (name.contains("WLT_")) return name.split("_")[1]
                if (name.contains("Wiliot_")) return name.split("_")[1]
            } catch (ex: Exception) {
                return ""
            }
            return ""
        }

        @SuppressLint("MissingPermission")
        private fun isNotRelevant(device: BluetoothDevice) =
            device.name.isNullOrEmpty() ||
                    (!device.name.startsWith("Wiliot") && !device.name.startsWith("WLT")) ||
                    (!device.name.contains(desiredId, ignoreCase = true) && !desiredId.contains(
                        getIdPartFromName(device.name),
                        ignoreCase = true))

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            asyncScope.launch {
                Reporter.log("result: $result.toString()", logTagLocal)
                result?.device?.let { device ->
                    if (isNotRelevant(device)) {
                        return@launch
                    } else {
                        foundCallback.invoke(device)
                        return@launch
                    }
                }
            }
        }

    }

}