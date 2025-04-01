package com.wiliot.wiliotdownstream.domain.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.wiliot.wiliotadvertising.BleCommandsAdvertising
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract
import com.wiliot.wiliotcore.contracts.EdgeJob
import com.wiliot.wiliotcore.contracts.EdgeJobExecutorContract
import com.wiliot.wiliotcore.model.Ack
import com.wiliot.wiliotcore.model.DownlinkAction
import com.wiliot.wiliotcore.model.DownlinkActionMessage
import com.wiliot.wiliotcore.model.DownlinkConfigurationMessage
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.every
import com.wiliot.wiliotcore.utils.helper.handleSdkConfigurationChangeRequest
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.service.WltServiceNotification
import com.wiliot.wiliotdownstream.domain.repository.DownstreamRepository
import com.wiliot.wiliotdownstream.feature.downstream
import com.wiliot.wiliotdownstream.feature.queueManager
import com.wiliot.wiliotdownstream.feature.withEdgeProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class DownstreamService : Service() {

    private val logTag = logTag()

    /**
     * A global variable to let system check if the Service is running without needing
     * to start or bind to it.
     * This is the best practice method as defined here:
     * https://groups.google.com/forum/#!topic/android-developers/jEvXMWgbgzE
     */
    private var running = false

    private var serviceCheckStr: String? = null

    private val commandsManager: CommandsQueueManagerContract
        get() = Wiliot.downstream().queueManager

    private var subscriptionJob: Job? = null
    private var processingJob: Job? = null
    private var serviceHeartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastConnectionHash: String? = null
    private var relaunchManagerJob: Job? = null

    private var mainJobLaunched: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Reporter.log("onStartCommand", logTag)

        lastConnectionHash = null

        intent?.extras?.let {
            serviceCheckStr = it.getString(INTENT_CHECK_STRING_KEY, null)
        }
        if (intent == null) {
            Reporter.log("onStartCommand -> stopSelf; no Intent!", logTag)
            stopSelf()
        } else {
            running = true
        }
        goForeground()

        relaunchManagerJob = serviceScope.launch {
            commandsManager.mqttConnectionHash.collectLatest { newHash ->
                lastConnectionHash = if (lastConnectionHash != null) {
                    if (lastConnectionHash != newHash && newHash != null) {
                        Reporter.log(
                            "New MQTT connection detected; subscription will be relaunched",
                            logTag
                        )
                        stopMainJob()
                        delay(500)
                        launchMainJob("relaunchManagerJob")
                    }
                    newHash
                } else {
                    newHash
                }
            }
        }

        if (mainJobLaunched.not()) launchMainJob("onStartCommand")

        commandsManager.ensureUploadQueueActive()
        commandsManager.goTransportServiceForeground(this)

        return super.onStartCommand(intent, flags, startId)
    }

    private fun launchMainJob(callerLog: String?) {
        Reporter.log("launchMainJob($callerLog)", logTag)

        mainJobLaunched = true

        subscriptionJob?.runCatching { cancel() }
        subscriptionJob = serviceScope.launch {
            commandsManager.subscribeOnDownlink(Wiliot.configuration.environment).let {
                it.collectLatest { m ->
                    DownstreamRepository.onNewMessage(m)
                }
            }
        }
        processingJob?.runCatching { cancel() }
        processingJob = serviceScope.launch {
            DownstreamRepository.currentMessage.collectLatest {
                it?.let { msg ->
                    when (msg) {
                        is DownlinkActionMessage -> processDownlinkActionMessage(msg)
                        is DownlinkConfigurationMessage -> processDownlinkConfigurationMessage(msg)
                        else -> return@collectLatest
                    }
                }
            }
        }

        serviceHeartbeatJob?.runCatching { cancel() }
        serviceHeartbeatJob = serviceScope.every(TimeUnit.MINUTES.toMillis(1)) {
            commandsManager.sendDownlinkHeartbeat()
        }
    }

    private fun processDownlinkActionMessage(msg: DownlinkActionMessage) {
        when (msg.action) {
            DownlinkAction.ADVERTISE -> {
                BleCommandsAdvertising.startAdvertising(
                    context = this@DownstreamService,
                    payload = msg.txPacketAsBytesArray(),
                    duration = msg.txMaxDurationMs
                        ?: DownstreamRepository.DEFAULT_COMMAND_DELAY
                )
            }

            DownlinkAction.BRIDGE_OTA -> {
                withEdgeProcessor {
                    kotlin.runCatching {
                        startJob(
                            edgeJob = EdgeJob.BridgeOtaUpgrade(
                                bridgeId = msg.bridgeId ?: "unknown",
                                rebootPacket = msg.txPacketAsBytesArray(),
                                rebootPacketAdvDuration = msg.txMaxDurationMs ?: 1000,
                                firmwareUrl = msg.bridgeFirmwareDirectoryURL ?: "unknown"
                            ),
                            callback = object : EdgeJobExecutorContract.ExecutorCallback<EdgeJob.BridgeOtaUpgrade> {
                                override fun jobSucceeded(
                                    message: String?,
                                    job: EdgeJob.BridgeOtaUpgrade
                                ) {
                                    Reporter.log(
                                        "BRIDGE OTA -> jobSucceeded (${job.bridgeId})",
                                        logTag
                                    )
                                    DownstreamRepository.notifyMessageProcessed(msg)
                                    Wiliot.downstream().feedbackChannel?.sendAck(Ack.OtaJobAck.success())
                                }

                                override fun jobFailed(
                                    message: String?,
                                    error: Throwable?,
                                    job: EdgeJob.BridgeOtaUpgrade
                                ) {
                                    Reporter.exception(
                                        "BRIDGE OTA -> jobFailed (${job.bridgeId})",
                                        error,
                                        logTag
                                    )
                                    DownstreamRepository.notifyMessageProcessed(msg)
                                    Wiliot.downstream().feedbackChannel?.sendAck(Ack.OtaJobAck.failure())
                                }
                            }
                        )
                    }.onFailure { err ->
                        Reporter.exception(
                            message = "Attempt to start OTA job failed",
                            exception = err,
                            where = logTag
                        )
                    }
                }
            }

            DownlinkAction.PREPARE_BRIDGE_IMAGE,
            DownlinkAction.REBOOT_BRIDGE,
            DownlinkAction.UPGRADE_BRIDGE,
            DownlinkAction.CLEAR_IMAGES,
            -> {
                // Not implemented yet
            }

            else -> {
                // Nothing
            }
        }
    }

    private fun processDownlinkConfigurationMessage(msg: DownlinkConfigurationMessage) {
        Wiliot.handleSdkConfigurationChangeRequest(msg)
    }

    private fun stopMainJob() {
        Reporter.log("stopMainJob", logTag)

        mainJobLaunched = false

        DownstreamRepository.release()
        commandsManager.clearDownlinkCommand()
        subscriptionJob?.cancel()
        subscriptionJob = null
        processingJob?.cancel()
        processingJob = null
        serviceHeartbeatJob?.cancel()
        serviceHeartbeatJob = null
    }

    private fun goForeground() =
        WltServiceNotification.getWltDsServiceNotification(this)?.let { notification ->
            startForeground(WltServiceNotification.WLT_DS_SERVICE_NOTIFICATION_ID, notification)
        }.also {
            Wiliot.notifyServicesStarting()
        }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        /**
         * Note that onDestroy is not guaranteed to be called quickly or at all. Services exist at
         * the whim of the system, and onDestroy can be delayed or skipped entirely if memory need
         * is critical.
         */
        Reporter.log("onDestroy (scs: $serviceCheckStr)", logTag)

        runBlocking {
            DownstreamRepository.release()
            stopMainJob()
            serviceScope.coroutineContext.cancelChildren()
            running = false
            serviceScope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            Wiliot.downstream().stop()
            super.onDestroy()
        }
    }

    companion object {
        const val INTENT_CHECK_STRING_KEY = "ds_service_intent_check_str"
    }

}
