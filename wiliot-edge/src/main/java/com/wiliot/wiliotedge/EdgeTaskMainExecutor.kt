package com.wiliot.wiliotedge

import com.wiliot.wiliotcore.contracts.EdgeJob
import com.wiliot.wiliotcore.contracts.EdgeJobExecutorContract
import com.wiliot.wiliotcore.contracts.EdgeJobProcessorContract
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotedge.brgota.BridgeOtaUpgradeExecutor

internal object EdgeTaskMainExecutor : EdgeJobProcessorContract {

    private val logTag = logTag()

    private var task: EdgeJobExecutorContract<out EdgeJob>? = null

    @Suppress("UNCHECKED_CAST")
    override fun <T : EdgeJob> startJob(edgeJob: T, callback: EdgeJobExecutorContract.ExecutorCallback<T>) {
        Reporter.log("startJob($edgeJob)", logTag)
        if (task != null) {
            Reporter.log("startJob($edgeJob) -> error: BUSY", logTag)
            throw IllegalStateException("EdgeTaskMainExecutor is busy")
        }
        task = when (edgeJob) {
            is EdgeJob.BridgeOtaUpgrade -> BridgeOtaUpgradeExecutor(edgeJob).apply {
                val externalCallback = callback as EdgeJobExecutorContract.ExecutorCallback<EdgeJob.BridgeOtaUpgrade>
                setupCallback(
                    object : EdgeJobExecutorContract.ExecutorCallback<EdgeJob.BridgeOtaUpgrade> {
                        override fun jobSucceeded(message: String?, job: EdgeJob.BridgeOtaUpgrade) {
                            externalCallback.jobSucceeded(message, job)
                            cancelCurrentJobIfExist()
                        }

                        override fun jobFailed(
                            message: String?,
                            error: Throwable?,
                            job: EdgeJob.BridgeOtaUpgrade
                        ) {
                            externalCallback.jobFailed(message, error, job)
                            cancelCurrentJobIfExist()
                        }
                    }
                )
            }
            else -> throw IllegalArgumentException(
                "EdgeTaskMainExecutor can not resolve target executor for job of type ${edgeJob::class.java.simpleName}"
            )
        }.also { it.startJob() }
    }

    override fun cancelCurrentJobIfExist() {
        task?.cancelJob()
        task = null
    }

}