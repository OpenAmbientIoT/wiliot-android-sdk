package com.wiliot.wiliotupstream.domain.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wiliot.wiliotcalibration.BleAdvertising
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract
import com.wiliot.wiliotcore.model.Ack
import com.wiliot.wiliotcore.model.BasePacketData
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.BridgeStatus
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.bluetoothManager
import com.wiliot.wiliotcore.utils.connectivityManager
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.service.WltServiceNotification
import com.wiliot.wiliotcore.utils.service.WltServiceNotification.WLT_US_SERVICE_NOTIFICATION_ID
import com.wiliot.wiliotupstream.BuildConfig
import com.wiliot.wiliotupstream.R
import com.wiliot.wiliotupstream.domain.repository.BeaconDataRepository
import com.wiliot.wiliotupstream.feature.queueManager
import com.wiliot.wiliotupstream.feature.upstream
import com.wiliot.wiliotupstream.feature.withExtraEdgeProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class ForegroundService : Service() {

    private val logTag = logTag()

    private val queueManager: MessageQueueManagerContract
        get() = Wiliot.upstream().queueManager

    internal class NetworkCallbackImp(
        private val holder: NetworkState,
    ) : ConnectivityManager.NetworkCallback() {

        internal data class NetworkState(
            var network: Network?,
            var isConnected: Boolean = false,
            var capabilities: NetworkCapabilities? = null,
        ) {
            @Suppress("unused")
            val signalStrength: Int
                get() =
                    capabilities?.signalStrength ?: -1

            @Suppress("unused")
            val transport: String
                get() = when {
                    true == capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    true == capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cel"
                    else -> "Unknown"
                }
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (network == holder.network) {
                holder.isConnected = true
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            if (network == holder.network) {
                holder.isConnected = false
            }
        }

        override fun onUnavailable() {
            super.onUnavailable()
            holder.isConnected = false
            holder.network = null
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            if (network == holder.network) {
                holder.capabilities = networkCapabilities
            }
        }
    }

    /**
     * A global variable to let system check if the Service is running without needing
     * to start or bind to it.
     * This is the best practice method as defined here:
     * https://groups.google.com/forum/#!topic/android-developers/jEvXMWgbgzE
     */
    private var running = false

    private var serviceCheckStr: String? = null

    private var job: Job? = null
    private val alertChannelId = "wiliot_service_alert"
    private val alertChannelName = "Wiliot Alert"
    private var networkCallback: NetworkCallbackImp? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val oldHandler = Thread.currentThread().uncaughtExceptionHandler
    private val uncaughtExceptionHandler: Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        Reporter.log(
            "Uncaught exception processed (UPSTREAM SERVICE)",
            logTag,
            highlightError = true
        )
        if (BuildConfig.DEBUG) {
            throwable.printStackTrace()
        }
        relaunchOrExit(thread, oldHandler, throwable)
    }

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Reporter.log("onStartCommand", logTag)
        intent?.extras?.let {
            serviceCheckStr = it.getString(INTENT_CHECK_STRING_KEY, null)
        }
        if (intent == null) {
            Reporter.log("onStartCommand -> stopSelf; no Intent!", logTag)
            stopSelf()
        } else {
            running = true
        }
        queueManager.initActorAsync()
        goForeground()
        goMqttForeground()
        startNetworkStateWatch()

        job = serviceScope.launch {
            tryToStartBleAdvertisement()
            try {
                Wiliot.upstream().scanner?.startScan()
            } catch (e: Exception) {
                Log.e(logTag, e.localizedMessage.orEmpty())
            }
            serviceScope.launch {
                BeaconDataRepository.bridgesPayload.collectLatest {
                    notifyBridgeStatusChange(it)
                }
            }
            serviceScope.launch {
                BeaconDataRepository.bridgesHbPayload.collectLatest {
                    Reporter.log("publish hb from Service (scs: $serviceCheckStr)", logTag)
                    publishBridgesHb(it)
                }
            }
            serviceScope.launch {
                BeaconDataRepository.instantPayload.collectLatest {
                    Reporter.log("publish instant pl from Service (scs: $serviceCheckStr)", logTag)
                    doPublishUsingPayload(it.toMutableSet())
                }
            }
            serviceScope.launch {
                BeaconDataRepository.melPayload.collectLatest {
                    Reporter.log("publish MEL payload from Service (scs: $serviceCheckStr)", logTag)
                    publishMel(it)
                }
            }
            serviceScope.launch {
                BeaconDataRepository.logsPayload.collectLatest {
                    Reporter.log("publish log from Service (scs: $serviceCheckStr)", logTag)
                    publishLog(it)
                }
            }
            serviceScope.launch {
                BeaconDataRepository.melAckPayload.collectLatest {
                    Reporter.log("publish MEL ACK from Service (scs: $serviceCheckStr)", logTag)
                    publishMelAck(it)
                }
            }
            serviceScope.launch {
                BeaconDataRepository.bridgeAckPayload.collectLatest {
                    Reporter.log("publish bridge ack from Service (scs: $serviceCheckStr)", logTag)
                    publishBridgeAck(it)
                }
            }

            doPublishCapabilities()
        }

        attachUncaughtExceptionHandler()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun notifyBridgeStatusChange(payload: List<BridgeStatus>) {
        serviceScope.launch {
            payload.filter { it.formationType == BridgeStatus.FormationType.FROM_CONFIG_PKT }
                .takeIf {
                    it.isNotEmpty()
                }?.let { filteredPayload ->
                queueManager.publishBridgeStatus(
                    filteredPayload,
                    Wiliot.configuration.environment
                )
            }

            delay(2000L)
            withExtraEdgeProcessor { askForBridgesIfNeeded(payload) }
        }
    }

    private fun publishLog(payload: List<Any>) {
        serviceScope.launch {
            queueManager.publishPacketLog(
                payload,
                Wiliot.configuration.environment
            )
        }
    }

    private fun publishMelAck(payload: Ack) {
        serviceScope.launch {
            queueManager.publishMelAck(
                payload,
                Wiliot.configuration.environment
            )
        }
    }

    private fun publishBridgeAck(payload: List<Any>) {
        serviceScope.launch {
            queueManager.publishBridgeAckAction(
                payload,
                Wiliot.configuration.environment
            )
        }
    }

    private fun publishBridgesHb(payload: List<BridgeHbPacketAbstract>) {
        serviceScope.launch {
            queueManager.publishBridgeHb(payload, Wiliot.configuration.environment)
        }
    }

    private fun doPublishUsingPayload(payload: MutableSet<BasePacketData>) {
        serviceScope.launch {
            queueManager.publishPayload(
                payload
            )
        }
    }

    private fun publishMel(payload: List<MelModulePacket>) {
        serviceScope.launch {
            queueManager.publishMel(
                payload,
                Wiliot.configuration.environment
            )
        }
    }

    private fun doPublishCapabilities() {
        serviceScope.launch {
            queueManager.publishCapabilities()
        }
    }

    private fun tryToStartBleAdvertisement() {
        if (airplaneModeEnabled) {
            showAirplaneModeNotification()
        } else {
            startBleAdvertisement()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showAirplaneModeNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent().apply {
                setClassName(
                    "com.wiliot.wiliotapp",
                    "com.wiliot.wiliotapp.ui.main.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AIRPLANE_MODE_ERROR_FLAG, true)
            },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createAlertNotificationChannel()?.let { channelId ->
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
            val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_start)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(getString(R.string.foreground_service_airplane_mode_error_title))
                .setContentText(getString(R.string.foreground_service_airplane_mode_error_description))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            with(NotificationManagerCompat.from(this)) {
                notify(Random.nextInt(), notification)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleAdvertisement() {
        if (Wiliot.configuration.isBleAdvertisementEnabled) {
            try {
                BleAdvertising.startAdvertising(this@ForegroundService)
                Reporter.log(
                    "BleAdvertising started (scs: $serviceCheckStr)",
                    this@ForegroundService.logTag
                )
            } catch (e: IllegalStateException) {
                // it means that BT was turned off
                if (bluetoothManager.adapter.enable()) {
                    BleAdvertising.startAdvertising(this@ForegroundService)
                    Reporter.log(
                        "BleAdvertising started (scs: $serviceCheckStr)",
                        this@ForegroundService.logTag
                    )
                } else {
                    Reporter.log(
                        "BleAdvertising failed to start. Can not enable Bluetooth (scs: $serviceCheckStr)",
                        this@ForegroundService.logTag
                    )
                }
            }
        }
    }

    private val airplaneModeEnabled: Boolean
        get() = Settings.System.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onDestroy() {
        /**
         * Note that onDestroy is not guaranteed to be called quickly or at all. Services exist at
         * the whim of the system, and onDestroy can be delayed or skipped entirely if memory need
         * is critical.
         */
        Reporter.log("onDestroy (scs: $serviceCheckStr)", logTag)

        runBlocking {
            serviceScope.coroutineContext.cancelChildren()
            Wiliot.upstream().scanner?.stopScan()
            queueManager.releaseConnections()
            queueManager.stopConnectionMonitoring()
            running = false
            stopNetworkStateWatch()
            serviceScope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            Wiliot.upstream().stop()
            Thread.currentThread().uncaughtExceptionHandler = oldHandler
            super.onDestroy()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Wiliot.upstream().releaseMemory(level)
    }

    //region NetworkWatch
    private fun startNetworkStateWatch() {
        applicationContext.connectivityManager.run {
            networkCallback = NetworkCallbackImp(
                NetworkCallbackImp.NetworkState(activeNetwork)
            ).apply {
                registerDefaultNetworkCallback(this)
            }
        }
    }

    private fun stopNetworkStateWatch() {
        applicationContext.connectivityManager.run {
            networkCallback?.let {
                unregisterNetworkCallback(it)
                networkCallback = null
            }
        }
    }
    //endregion

    /**
     * Required for extending service, but this will be a Started Service only, so no need for
     * binding.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun goForeground() =
        WltServiceNotification.getWltUsServiceNotification(this)?.let { notification ->
            startForeground(WLT_US_SERVICE_NOTIFICATION_ID, notification)
        }.also {
            Wiliot.notifyServicesStarting()
        }

    private fun goMqttForeground() =
        WltServiceNotification.getWltMQTTServiceNotification(this)?.let { notification ->
            queueManager.bindMqttToForeground(notification)
        }.also {
            Wiliot.notifyServicesStarting()
        }

    private fun createAlertNotificationChannel(): String? =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.run {
            val channel = NotificationChannel(
                alertChannelId,
                alertChannelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            createNotificationChannel(channel)
            channel.id
        }

    private fun attachUncaughtExceptionHandler() {
        if (customExceptionHandlerAttached.not()) {
            Thread.currentThread().uncaughtExceptionHandler = uncaughtExceptionHandler
            customExceptionHandlerAttached = true
        }
    }

    private fun relaunchOrExit(thread: Thread, handler: Thread.UncaughtExceptionHandler?, throwable: Throwable) {
        if (Wiliot.configuration.phoenix) {
            Reporter.log("relaunchOrExit (UPSTREAM SERVICE) -> M2M relaunch", logTag)
            Reporter.exception("UPSTREAM FATAL", throwable, logTag)
            // Make sure it is equal to actual app package name
            packageManager.getLaunchIntentForPackage(Wiliot.applicationPackage)?.component?.let { comp ->
                Intent.makeRestartActivityTask(comp)
            }?.let { intent ->
                // Make sure it is equal to MainActivity.IMMEDIATE_GW_LAUNCH value
                intent.putExtra(
                    "immediate_gw_launch", true
                )
                intent.putExtra(
                    "magic_number_a", Wiliot.positioningLastDetectedFloor
                )
                startActivity(intent)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            Reporter.log("relaunchOrExit (UPSTREAM SERVICE) -> exit", logTag)
            if (handler != null) {
                handler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    companion object {
        private var customExceptionHandlerAttached = false

        const val AIRPLANE_MODE_ERROR_FLAG = "wiliot_fs_airplane_mode_error"
        const val INTENT_CHECK_STRING_KEY = "service_intent_check_str"
    }
}
