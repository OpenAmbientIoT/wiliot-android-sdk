package com.wiliot.wiliotdfu

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.android.error.GattError

/**
 * Helper for Bridge DFU (OTA) upgrade. Allows to launch and manage OTA upgrade process.
 */
class BridgeDfuHelper {

    private val logTag = logTag()

    /**
     * Enum that represents DFU Stages (Progress)
     */
    enum class DfuStage(val value: Int) {
        /**
         * DFU library establishing connection to the BT Device
         */
        CONNECTING(1),

        /**
         * DFU library established connection with BT Device
         */
        CONNECTED(2),

        /**
         * DFU library trying to start upgrade process
         */
        STARTING(3),

        /**
         * DFU library started upgrade process successfully
         */
        STARTED(4),

        /**
         * DFU library asking BT Device to enter DFU mode
         */
        ENABLING_DFU_MODE(5),

        /**
         * DFU library performs flashing
         */
        FLASHING(6),

        /**
         * DFU library checking integrity of just uploaded software
         */
        VALIDATING(7),

        /**
         * DFU library trying to disconnect from device
         */
        DISCONNECTING(8),

        /**
         * DFU library disconnected from device successfully.
         * It does not mean that process of upgrade is close to the end ([COMPLETED]).
         * Sometimes Bluetooth Device requires upgrades not only for operational part but also for Bootloader.
         * In this case, DFU library will start another one iteration starting from [CONNECTING]
         */
        DISCONNECTED(9),

        /**
         * DFU library completed upgrade operation
         */
        COMPLETED(10);

        /**
         * Returns true if [DfuStage] has order less then or equals to specified [stage]
         */
        fun lessThenOrEquals(stage: DfuStage): Boolean {
            return this.value <= stage.value
        }
    }

    /**
     * Start upgrade process.
     *
     * @param [device] is a previously scanned BT Device via [BridgeDfuScanner];
     * @param [firmwareFileUri] uri of locally stored firmware file;
     * @param [onStageChanged] callback to receive updates on [DfuStage] changes;
     * @param [onFlashingProgress] callback to receive updates explicitly on flashing progress. First param
     * (part) indicates which part is currently flashing (Bootloader/Operational/etc). Second param
     * (parts) indicates how many parts firmware contains. Third param (progress) indicates progress
     * of current part;
     * @param [onError] callback to handle errors that could be occurred during upgrade operation.
     */
    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        device: BluetoothDevice,
        firmwareFileUri: Uri,
        onStageChanged: (stage: DfuStage) -> Unit,
        onFlashingProgress: (part: Int, parts: Int, progress: Int) -> Unit,
        onError: (throwable: Throwable?) -> Unit
    ) {
        Reporter.log("start", logTag)
        DfuServiceInitiator.createDfuNotificationChannel(context)

        DfuServiceListenerHelper.registerProgressListener(
            context,
            object : DfuProgressListener {
                override fun onDeviceConnecting(deviceAddress: String) {
                    Reporter.log("onDeviceConnecting($deviceAddress)", logTag)
                    onStageChanged(DfuStage.CONNECTING)
                }

                override fun onDeviceConnected(deviceAddress: String) {
                    Reporter.log("onDeviceConnected($deviceAddress)", logTag)
                    onStageChanged(DfuStage.CONNECTED)
                }

                override fun onDfuProcessStarting(deviceAddress: String) {
                    Reporter.log("onDfuProcessStarting($deviceAddress)", logTag)
                    onStageChanged(DfuStage.STARTING)
                }

                override fun onDfuProcessStarted(deviceAddress: String) {
                    Reporter.log("onDfuProcessStarted($deviceAddress)", logTag)
                    onStageChanged(DfuStage.STARTED)
                }

                override fun onEnablingDfuMode(deviceAddress: String) {
                    Reporter.log("onEnablingDfuMode($deviceAddress)", logTag)
                    onStageChanged(DfuStage.ENABLING_DFU_MODE)
                }

                override fun onProgressChanged(
                    deviceAddress: String,
                    percent: Int,
                    speed: Float,
                    avgSpeed: Float,
                    currentPart: Int,
                    partsTotal: Int
                ) {
                    Reporter.log(
                        "onProgressChanged($deviceAddress) [$currentPart/$partsTotal] $percent%",
                        logTag
                    )
                    onStageChanged(DfuStage.FLASHING)
                    onFlashingProgress(currentPart, partsTotal, percent)
                }

                override fun onFirmwareValidating(deviceAddress: String) {
                    Reporter.log("onFirmwareValidating($deviceAddress)", logTag)
                    onStageChanged(DfuStage.VALIDATING)
                }

                override fun onDeviceDisconnecting(deviceAddress: String?) {
                    Reporter.log("onDeviceDisconnecting($deviceAddress)", logTag)
                    onStageChanged(DfuStage.DISCONNECTING)
                }

                override fun onDeviceDisconnected(deviceAddress: String) {
                    Reporter.log("onDeviceDisconnected($deviceAddress)", logTag)
                    onStageChanged(DfuStage.DISCONNECTED)
                }

                override fun onDfuCompleted(deviceAddress: String) {
                    Reporter.log("onDfuCompleted($deviceAddress)", logTag)
                    onStageChanged(DfuStage.COMPLETED)
                }

                override fun onDfuAborted(deviceAddress: String) {
                    Reporter.log("onDfuAborted($deviceAddress)", logTag)
                    onError.invoke(null)
                }

                override fun onError(
                    deviceAddress: String,
                    error: Int,
                    errorType: Int,
                    message: String?
                ) {
                    Reporter.log(
                        "onError($deviceAddress) error: $error (${GattError.parse(error)}), errorType: $errorType, message: $message",
                        logTag
                    )
                    onError.invoke(
                        RuntimeException(
                            "error: $error (${GattError.parse(error)}), errorType: $errorType, message: $message"
                        )
                    )
                }
            },
            device.address
        )

        val starter = DfuServiceInitiator(device.address).apply {
            setDeviceName(device.name)
            setKeepBond(true)
            setForceDfu(false)
            setForceScanningForNewAddressInLegacyDfu(false)
            setPrepareDataObjectDelay(400)
            setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
            setPacketsReceiptNotificationsValue(200)
            setPacketsReceiptNotificationsEnabled(false)
            setMtu(247)
        }

        try {
            starter.setZip(firmwareFileUri)
            starter.start(context, BridgeDfuService::class.java)
        } catch (e: Exception) {
            Log.e(logTag, "Error occurred", e)
        }
    }

    /**
     * Force stop upgrade operation
     */
    fun cancel(context: Context) {
        BridgeDfuService.abort(context)
    }

}