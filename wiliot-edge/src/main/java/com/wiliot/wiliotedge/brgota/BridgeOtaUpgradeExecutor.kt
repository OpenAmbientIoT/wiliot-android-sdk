package com.wiliot.wiliotedge.brgota

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.wiliot.wiliotadvertising.BleCommandsAdvertising
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.EdgeJob
import com.wiliot.wiliotcore.contracts.EdgeJobExecutorContract
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.isValidJwt
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotdfu.BridgeDfuHelper
import com.wiliot.wiliotdfu.BridgeDfuScanner
import com.wiliot.wiliotedge.BuildConfig
import com.wiliot.wiliotedge.EdgeJobExecutor
import com.wiliot.wiliotedge.withApplicationContext
import com.wiliot.wiliotnetworkedge.WiliotNetworkEdge
import com.wiliot.wiliotnetworkedge.utils.coreApiBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class BridgeOtaUpgradeExecutor(
    private val jobConfig: EdgeJob.BridgeOtaUpgrade
) : EdgeJobExecutor<EdgeJob.BridgeOtaUpgrade>(jobConfig) {

    private val logTag = logTag()

    private var mCallback: EdgeJobExecutorContract.ExecutorCallback<EdgeJob.BridgeOtaUpgrade>? =
        null

    private val executorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var executorJob: Job? = null

    private var scanner: BridgeDfuScanner? = null
    private var dfuHelper: BridgeDfuHelper? = null

    override fun setupCallback(callback: EdgeJobExecutorContract.ExecutorCallback<EdgeJob.BridgeOtaUpgrade>) {
        mCallback = callback
    }

    override fun startJob() {
        Reporter.log("startJob", logTag)
        withApplicationContext {
            executorJob = executorScope.launch {

                // 1. Download Firmware
                Reporter.log("1. Download Firmware", logTag)
                val fwUri = try {
                    FwDownloadTool.download(
                        context = this@withApplicationContext,
                        url = jobConfig.firmwareUrl
                    )
                } catch (ex: Exception) {
                    Reporter.exception(
                        message = "Failed to download FW",
                        exception = ex,
                        where = logTag
                    )
                    mCallback?.jobFailed(
                        "Failed to download FW",
                        error = ex,
                        job = jobConfig
                    )
                    terminateAll()
                    return@launch
                }

                // 2. Send reboot packet
                Reporter.log("2. Send reboot pkt", logTag)
                BleCommandsAdvertising.startAdvertising(
                    context = this@withApplicationContext,
                    payload = jobConfig.rebootPacket,
                    duration = jobConfig.rebootPacketAdvDuration
                )
                delay(jobConfig.rebootPacketAdvDuration)

                // 3. Find Bridge and Flash it
                Reporter.log("3. Find Bridge and Flash it", logTag)
                scanner = BridgeDfuScanner(
                    jobConfig.bridgeId,
                    onFound = { btDevice ->
                        dfuHelper = BridgeDfuHelper()
                        dfuHelper?.start(
                            context = this@withApplicationContext,
                            device = btDevice,
                            firmwareFileUri = fwUri,
                            onStageChanged = { dfuStage ->
                                Reporter.log("[DFU] Stage: $dfuStage", logTag)
                                if (dfuStage == BridgeDfuHelper.DfuStage.COMPLETED) {
                                    Reporter.log("[DFU] Completed!", logTag)
                                    mCallback?.jobSucceeded(
                                        "Flashing process finished successfully",
                                        job = jobConfig
                                    )
                                    terminateAll()
                                }
                            },
                            onFlashingProgress = { part, parts, progress ->
                                Reporter.log("[DFU] Flashing ($part/$parts) $progress%", logTag)
                            },
                            onError = { err ->
                                Reporter.exception(
                                    message = "Failed to flash Bridge ${jobConfig.bridgeId} with fw ${jobConfig.firmwareUrl}",
                                    exception = err,
                                    logTag
                                )
                                mCallback?.jobFailed(
                                    "Failed to flash Bridge",
                                    error = err,
                                    job = jobConfig
                                )
                                terminateAll()
                            }
                        )
                    },
                    onFailure = {
                        mCallback?.jobFailed(
                            "Failed to find Bridge nearby",
                            error = IllegalStateException("Failed to find Bridge nearby"),
                            job = jobConfig
                        )
                        terminateAll()
                    }
                )
                scanner?.doScanCycle()

            }
        } ?: kotlin.run {
            mCallback?.jobFailed(
                "Failed to execute job. Context is null",
                error = IllegalStateException("Context is null"),
                job = jobConfig
            )
            terminateAll()
        }
    }

    override fun cancelJob() {
        Reporter.log("cancelJob", logTag)
        terminateAll()
    }

    private fun terminateAll() {
        executorJob?.cancel()
        executorJob = null
        withApplicationContext {
            dfuHelper?.cancel(this)
            dfuHelper = null
        }
        scanner?.dispose()
    }

}

private object FwDownloadTool {

    private val logTag = logTag()

    private const val FW_FILE_NAME = "brg_firmware.zip"

    suspend fun download(context: Context, url: String): Uri {

        val finalUrl = StringBuilder().apply {
            if (url.startsWith(Wiliot.configuration.environment.coreApiBase()).not()) {
                append(Wiliot.configuration.environment.coreApiBase())
                if (url.startsWith("/").not()) append("/")
            }
            if (url.endsWith(".zip")) {
                append(url)
            } else {
                append(url)
                if (url.endsWith("/").not()) append("/")
                append("brg_sd_bl_app.zip")
            }
        }.toString()

        // check token
        var tokenChecks = 0

        fun isTokenValid(): Boolean {
            return WiliotNetworkEdge.configuration.authToken.isValidJwt()
        }

        suspend fun checkToken(): Boolean {
            Reporter.log("download -> checkToken()", logTag)
            while (tokenChecks <= 3) {
                tokenChecks += 1
                if (isTokenValid().not()) {
                    Reporter.log("download -> checkToken -> token is expired, requesting new one (attempt: $tokenChecks)...", logTag)
                    Wiliot.tokenExpirationCallback.onPrimaryTokenExpired()
                    delay(1000)
                } else {
                    break
                }
            }
            return isTokenValid()
        }

        if (!checkToken()) {
            Reporter.log("download -> token check is not passed", logTag)
            throw RuntimeException("Failed to get valid token. Unable to download Bridge FW image")
        }

        Reporter.log("download -> original: $url final: $finalUrl", logTag)

        // Remove prev file
        context.cacheDir?.listFiles { _, name ->
            name.equals(FW_FILE_NAME)
        }?.forEach {
            it.delete()
        }

        // Create new File
        val downloadingFile = File(context.cacheDir, FW_FILE_NAME)

        // Download File
        URL(finalUrl).let {
            val connection = it.openConnection() as HttpsURLConnection
            connection.setRequestProperty("Authorization", "Bearer ${WiliotNetworkEdge.configuration.authToken}")
            if (BuildConfig.DEBUG) {
                Reporter.log("Token for download fw: ${WiliotNetworkEdge.configuration.authToken}", logTag)
            }
            connection.contentType.let {  ct ->
                val stream = ct.contains("application/octet-stream")
                val zip = ct.contains("application/zip")
                stream || zip
            }.run {
                if (!this) throw IllegalArgumentException(
                    "Expected content type is application/zip but provided firmware URL leads to ${connection.contentType}: $finalUrl"
                )
            }

            val inputStream = connection.inputStream

            FileOutputStream(downloadingFile).use { output ->
                inputStream.copyTo(output)
            }

            inputStream.close()
            connection.disconnect()
        }

        return downloadingFile.toUri().also {
            Reporter.log("Download completed; uri: $it", logTag)
        }
    }

}