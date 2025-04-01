package com.wiliot.wiliotedge

import android.content.Context
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.EdgeJobProcessorContract
import com.wiliot.wiliotcore.contracts.WiliotEdgeModule
import com.wiliot.wiliotcore.getWithApplicationContext
import com.wiliot.wiliotcore.registerModule
import java.lang.RuntimeException

object WiliotEdge : WiliotEdgeModule {

    interface ContextRetriever {
        fun getContext(): Context?
    }

    internal var contextRetriever: ContextRetriever? = null

    override val processor: EdgeJobProcessorContract? get() = EdgeTaskMainExecutor.takeIf { contextRetriever != null }

    internal fun initialize(contextRetriever: ContextRetriever) {
        this.contextRetriever = contextRetriever
        Wiliot.registerModule(this)
    }

}

internal fun withApplicationContext(block: Context.() -> Unit) = WiliotEdge.contextRetriever?.getContext()?.let(block)

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initEdge() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initEdge`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotEdge.initialize(
        object : WiliotEdge.ContextRetriever {
            override fun getContext(): Context? {
                return getWithApplicationContext { this }!!
            }
        }
    )
}
