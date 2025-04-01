package com.wiliot.wiliotcore.contracts

/**
 * Contract for durable edge-related jobs processing/management (e.g. OTA Bridge FW upgrade)
 */
interface EdgeJobProcessorContract {

    /**
     * Creates task of type [EdgeJob] to execute and starts it.
     *
     * @param [edgeJob] exact [EdgeJob] to execute;
     * @param [callback] to get result (success/failure).
     */
    fun <T : EdgeJob> startJob(edgeJob: T, callback: EdgeJobExecutorContract.ExecutorCallback<T>)

    /**
     * Force stop current task (if exist)
     */
    fun cancelCurrentJobIfExist()
}

sealed class EdgeJob {

    /**
     * Bridge OTA Firmware upgrade job.
     *
     * @param [bridgeId] id of Bridge that should be upgraded;
     * @param [rebootPacket] BT packet received from Cloud to reboot Bridge;
     * @param [rebootPacketAdvDuration] duration of [rebootPacket] advertisement in ms (how long this
     * packet should be advertised);
     * @param [firmwareUrl] url of firmware (to download).
     *
     * Description: Downlink module listen to Cloud commands, and if it receives command to upgrade
     * exact Bridge, it forms [BridgeOtaUpgrade] object with data received from Cloud message.
     * Basically, it contains [bridgeId], [firmwareUrl] to be able to download firmware and
     * [rebootPacket] to reboot Bridge device to make it as connectable BluetoothDevice.
     * Actual implementation steps of execution for this task are: download firmware file, broadcast
     * [rebootPacket] during [rebootPacketAdvDuration] time, discover Bridge as a connectable
     * BluetoothDevice, connect to it and flash it.
     */
    class BridgeOtaUpgrade(
        val bridgeId: String,
        val rebootPacket: ByteArray,
        val rebootPacketAdvDuration: Long,
        val firmwareUrl: String,
    ) : EdgeJob()
}

/**
 * Contract for [EdgeJob] executor.
 * While [EdgeJobProcessorContract] describes management for [EdgeJob] tasks, [EdgeJobExecutorContract]
 * describes execution of task itself.
 */
interface EdgeJobExecutorContract<T : EdgeJob> {

    /**
     * Callback to get result of execution for [EdgeJob] task
     */
    interface ExecutorCallback<T> {

        /**
         * Called in case [EdgeJob] successfully finished.
         *
         * @param [message] could contain short summary about executed task;
         * @param [job] exact [EdgeJob] that was finished.
         */
        fun jobSucceeded(message: String?, job: T)

        /**
         * Called in case [EdgeJob] failed to finish
         *
         * @param [message] could contain details about failure;
         * @param [error] could contain exact [Throwable] that caused to failure;
         * @param [job] exact [EdgeJob] that was failed.
         */
        fun jobFailed(message: String?, error: Throwable?, job: T)
    }

    /**
     * Set up callback to get result of execution for [EdgeJob] task
     */
    fun setupCallback(callback: ExecutorCallback<T>)

    /**
     * Start [EdgeJob] execution
     */
    fun startJob()

    /**
     * Force stop [EdgeJob] execution
     */
    fun cancelJob()
}