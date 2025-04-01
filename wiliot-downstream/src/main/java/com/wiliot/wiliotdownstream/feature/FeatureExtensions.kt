package com.wiliot.wiliotdownstream.feature

import android.content.Context
import android.content.Intent
import com.wiliot.wiliotcore.ServiceState
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract
import com.wiliot.wiliotcore.contracts.EdgeJobProcessorContract
import com.wiliot.wiliotcore.contracts.UpstreamFeedbackChannel
import com.wiliot.wiliotcore.contracts.VirtualBridgeContract
import com.wiliot.wiliotcore.contracts.WiliotDownstreamModule
import com.wiliot.wiliotcore.contracts.wiring.CommandsQueueManagerProvider
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotdownstream.domain.repository.DownstreamRepository
import com.wiliot.wiliotdownstream.domain.service.DownstreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Main control unit of Downstream module
 */
class Downstream private constructor() : WiliotDownstreamModule {

    private val logTag = logTag()

    internal var queueManagerProvider: WeakReference<CommandsQueueManagerProvider>? = null

    internal var edgeProcessor: EdgeJobProcessorContract? = null
    internal var feedbackChannel: UpstreamFeedbackChannel? = null

    internal var vBridge: VirtualBridgeContract? = null

    companion object {

        private var dInstance: Downstream? = null

        /**
         * Singleton implementation of [Downstream] control unit
         */
        internal fun getInstance(): Downstream {
            if (dInstance == null) {
                dInstance = Downstream()
                Wiliot.registerModule(dInstance!!)
            }
            return dInstance!!
        }
    }

    private var downstreamIntent: Intent? = null

    private val mState = MutableStateFlow(DownstreamState.STOPPED)

    /**
     * Contains live flow representing current [Downstream] state
     */
    override val downstreamState: StateFlow<DownstreamState> = mState

    /**
     * Start [DownstreamService]. It allows to receive and process commands from the Cloud
     */
    override fun start() {
        Reporter.log("start; config = ${Wiliot.configuration}", logTag)
        if (Wiliot.configuration.isValid().not()) throw IllegalStateException(
            "Can not start Downstream. Configuration is invalid: ${Wiliot.configuration}"
        )

        Wiliot.withApplicationContext {
            downstreamIntent = Intent(this, DownstreamService::class.java).also { intent ->
                intent.putExtra(
                    DownstreamService.INTENT_CHECK_STRING_KEY,
                    "Caller:DownstreamExt;+time:${System.currentTimeMillis()}"
                )
            }
            launchService(this)
        }

        mState.value = DownstreamState.RUNNING_BACKGROUND
    }

    private fun launchService(context: Context?) {
        Reporter.log("launchService", logTag)
        context?.startForegroundService(downstreamIntent)
    }

    /**
     * Stop [DownstreamService]. It stops service and cancels currently running task in [EdgeJobProcessorContract]
     */
    override fun stop() {
        Reporter.log("stop", logTag)
        Wiliot.withApplicationContext {
            try {
                this.stopService(downstreamIntent).also {
                    Reporter.log("stop -> stopped: $it", logTag)
                }
            } catch (ex: Exception) {
                Reporter.log("stop -> failed to stop; probably stopped earlier", logTag)
            }
        }
        DownstreamRepository.release()
        edgeProcessor?.cancelCurrentJobIfExist()
        mState.value = DownstreamState.STOPPED
    }

    /**
     * Set implementation of [EdgeJobProcessorContract]
     */
    override fun setEdgeProcessor(processor: EdgeJobProcessorContract?) {
        this.edgeProcessor = processor
    }

    /**
     * Set implementation of [UpstreamFeedbackChannel]
     */
    override fun setUpstreamFeedbackChannel(channel: UpstreamFeedbackChannel?) {
        this.feedbackChannel = channel
    }

    override fun setupQueueProvider(provider: CommandsQueueManagerProvider) {
        queueManagerProvider = provider.weak()
    }

    override fun setVirtualBridge(vBridge: VirtualBridgeContract) {
        this.vBridge = vBridge
    }

}

/**
 * Returns instance of [Downstream] singleton
 */
@Suppress("unused", "UnusedReceiverParameter")
fun Wiliot.downstream(): Downstream = Downstream.getInstance()

internal fun withEdgeProcessor(task: EdgeJobProcessorContract.() -> Unit) {
    Wiliot.downstream().edgeProcessor.takeUnless { it == null }?.let(task)
}

internal val Downstream.queueManager: CommandsQueueManagerContract
    get() {
        if (queueManagerProvider == null) throw RuntimeException(
            "QueueManagerProvider is not initialized. Use Downstream.queueManagerBy() extension to initialize it"
        )
        return try {
            queueManagerProvider!!.get()!!.provideCommandsQueueManager()
        } catch (ex: Exception) {
            throw RuntimeException(
                "QueueManagerProvider failed to provide CommandsQueueManagerContract impl",
                ex
            )
        }
    }

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initDownstream() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initDownstream`. First you should initialize Wiliot with ApplicationContext."
    )
    Downstream.getInstance()
}

typealias DownstreamState = ServiceState