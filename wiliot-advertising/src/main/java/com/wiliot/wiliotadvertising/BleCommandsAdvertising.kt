package com.wiliot.wiliotadvertising

import android.annotation.SuppressLint
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.wiliot.wiliotcore.utils.WiliotReporter
import com.wiliot.wiliotcore.utils.bluetoothManager
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.toHexString

/**
 * Tool that allows to advertise BLE packets
 */
object BleCommandsAdvertising {

    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private val logTag = logTag()
    private val setCallback = object : AdvertisingSetCallback() {

        private fun Int.isFailureStatus(): Boolean {
            return this == ADVERTISE_FAILED_ALREADY_STARTED
                    || this == ADVERTISE_FAILED_DATA_TOO_LARGE
                    || this == ADVERTISE_FAILED_FEATURE_UNSUPPORTED
                    || this == ADVERTISE_FAILED_INTERNAL_ERROR
                    || this == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
        }

        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet,
            txPower: Int,
            status: Int,
        ) {
            WiliotReporter.log(
                logTag,
                "onAdvertisingSetStarted(): txPower: $txPower, status: $status"
            )
            if (status.isFailureStatus()) stop(this)
        }

        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            WiliotReporter.log(
                logTag,
                "onAdvertisingDataSet() :status:$status"
            )
            if (status.isFailureStatus()) stop(this)
        }

        override fun onScanResponseDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            WiliotReporter.log(
                logTag,
                "onScanResponseDataSet(): status:$status"
            )
            if (status.isFailureStatus()) stop(this)
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            WiliotReporter.log(
                logTag,
                "onAdvertisingSetStopped()"
            )
        }
    }

    /**
     * Get references to system Bluetooth objects if we don't have them already.
     */
    private fun initialize(context: Context) {
        if (mBluetoothLeAdvertiser == null) {
            val mBluetoothManager = context.bluetoothManager
            val mBluetoothAdapter = mBluetoothManager.adapter
            if (mBluetoothAdapter != null) {
                mBluetoothLeAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser
            } else {
                Log.e(logTag, "BT is null")
            }
        }
    }

    /**
     * This function performs initialization of [BluetoothLeAdvertiser] and starts advertising.
     * Basically this function used in Downstream module and in common case should not be used manually.
     * Downstream module receives command from Cloud to advertise BLE packet and then uses this
     * function to perform advertising.
     *
     * @param [payload] is a [ByteArray] content of BLE packet to be advertised;
     * @param [duration] defines how long this packet should be advertised.
     *
     * Note, that Bluetooth permission should be granted before accessing this function.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(
        context: Context,
        payload: ByteArray,
        duration: Long,
    ) {
        initialize(context)

        val multipleAdvertisementSupported =
            context.bluetoothManager.adapter?.isMultipleAdvertisementSupported ?: false
        WiliotReporter.log("Support BLE Advertising: $multipleAdvertisementSupported", logTag)

        val settings = buildAdvertiseSettings()
        val data = buildAdvertiseData(payload)

        mBluetoothLeAdvertiser?.runCatching {
            this.startAdvertisingSet(
                settings,
                data,
                null,
                null,
                null,
                duration.toInt(),
                0,
                setCallback
            )
        }?.onSuccess {
            WiliotReporter.log("Packet successfully advertised", logTag)
        }
    }

    private fun buildAdvertiseSettings(): AdvertisingSetParameters =
        AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)
            .build()

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private fun buildAdvertiseData(payload: ByteArray): AdvertiseData {

        /**
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         * This includes everything put into AdvertiseData including UUIDs, device info, &
         * arbitrary service or manufacturer data.
         * Attempting to send packets over this limit will result in a failure with error code
         * BleAdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         * onStartFailure() method of an BleAdvertiseCallback implementation.
         */

        val advertisingData = AdvertiseData.Builder()

        advertisingData.addServiceData(
            ParcelUuid.fromString(
                StringBuilder().apply {
                    append("0000")
                    append(payload.takeLast(29).take(2).toByteArray().toHexString().chunked(2).reversed().joinToString("") { it })
                    append("-0000-1000-8000-00805F9B34FB")
                }.toString()
            ),
            payload.takeLast(27).toByteArray()
        )
        advertisingData.setIncludeDeviceName(false)
        return advertisingData.build()
    }

    @SuppressLint("MissingPermission")
    private fun stop(callback: AdvertisingSetCallback) {
        WiliotReporter.log("Service: Stopping Advertising", logTag)
        try {
            mBluetoothLeAdvertiser?.stopAdvertisingSet(callback)
        } catch (ex: Exception) {
            WiliotReporter.exception("Can not stop advertising", ex, logTag)
        }
    }

    /**
     * Force stop advertising process.
     */
    fun stop() {
        stop(setCallback)
    }

}