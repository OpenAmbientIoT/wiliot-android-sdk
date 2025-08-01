package com.wiliot.wiliotandroidsdk

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wiliot.wiliotandroidsdk.permissions.PermissionsHelper
import com.wiliot.wiliotandroidsdk.permissions.RequestPermissionDialog
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract
import com.wiliot.wiliotandroidsdk.utils.ViewModelState
import com.wiliot.wiliotandroidsdk.utils.log
import com.wiliot.wiliotandroidsdk.utils.logTag
import com.wiliot.wiliotandroidsdk.utils.upd
import com.wiliot.wiliotcore.ServiceState
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.VirtualBridgeConfig
import com.wiliot.wiliotcore.health.WiliotHealth
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.utils.helper.start
import com.wiliot.wiliotcore.utils.helper.stop
import com.wiliot.wiliotupstream.feature.upstream
import com.wiliot.wiliotvirtualbridge.config.VConfig
import com.wiliot.wiliotvirtualbridge.feature.VirtualBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mAppPermissions: ApplicationPermissionsContract
): ViewModel() {

    private val logTag = logTag()
    private var healthJob: Job? = null

    data class State(
        val customPermissionDialog: RequestPermissionDialog? = null,
        val wiliotHealth: WiliotHealth = WiliotHealth(),
        val vBrgCfg: VirtualBridgeConfig = VConfig.config
    ): ViewModelState

    private val mState = mutableStateOf(State())
    val state: MutableState<State> = mState

    fun notifyActivityResumed() {
        checkPermissionsIfNeed()
        refreshHealthJobState()
    }

    fun checkBeforeStart(startAction: () -> Unit) {
        if (Wiliot.upstream().upstreamState.value != ServiceState.STOPPED) return
        checkGatewayPermissions(startAction)
    }

    fun onCustomPermissionDialogAccepted(
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        permission: ApplicationPermissionsContract.Permission
    ) {
        log("onCustomPermissionDialogAccepted($permission)", logTag)
        mState.upd { copy(customPermissionDialog = null) }
        when (permission) {
            ApplicationPermissionsContract.Permission.LOCATION_ENABLE -> mAppPermissions.requestGpsSettings()
            ApplicationPermissionsContract.Permission.BLUETOOTH_ENABLE -> mAppPermissions.requestPermission(
                permission
            ) { _, _ ->
                // Nothing
            }

            ApplicationPermissionsContract.Permission.INTERNET_ADAPTER_ENABLE -> mAppPermissions.requestPermission(
                permission
            ) { _, _ ->
                // Nothing
            }

            ApplicationPermissionsContract.Permission.BACKGROUND_LOCATION -> mAppPermissions.requestPermission(
                permission
            ) { _, result ->
                if (result == ApplicationPermissionsContract.Result.REJECTED) mAppPermissions.requestApplicationSettings()
            }

            else -> mAppPermissions.requestApplicationSettings()
        }
    }

    fun cancelGatewayPermissionCheck() {
        log("cancelGatewayPermissionCheck", logTag)
        mState.upd { copy(customPermissionDialog = null) }
        checkGatewayPermissions()
    }

    private var allowedToCheckPermissionsAfterResume: Boolean = false

    private fun checkPermissionsIfNeed() {
        log("checkPermissionsIfNeed -> $allowedToCheckPermissionsAfterResume", logTag)
        if (allowedToCheckPermissionsAfterResume.not()) return
        allowedToCheckPermissionsAfterResume = false
        checkGatewayPermissions()
    }

    private fun checkGatewayPermissions(onSuccess: (() -> Unit)? = null) {
        log("checkGatewayPermissions", logTag)
        PermissionsHelper.checkPermissions(
            appPermissions = mAppPermissions,
            bundle = ApplicationPermissionsContract.PermissionsBundle.GATEWAY_MODE,
            onPermissionChainCheckFailed = {
                log("onPermissionChainCheckFailed", logTag)
                Wiliot.stop()
            },
            onPermissionChainCheckSucceed = {
                log("onPermissionChainCheckSucceed", logTag)
                onSuccess?.invoke() ?: run {
                    Wiliot.let {
                        try {
                            it.start()
                        } catch (ex: IllegalStateException) {
                            log(
                                "Can not start gateway mode by user request (UI)",
                                where = logTag
                            )
                        }
                    }
                }
            },
            onCustomRequestPermission = { permission, bundle ->
                showCustomRequestPermissionDialog(
                    permission = permission,
                    bundle = bundle
                )
            }
        )
    }

    private fun showCustomRequestPermissionDialog(
        permission: ApplicationPermissionsContract.Permission,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
    ) {
        mState.upd {
            copy(
                customPermissionDialog = PermissionsHelper.generatePermissionDialogData(
                    permission = permission,
                    bundle = bundle,
                    domainCallback = {
                        allowedToCheckPermissionsAfterResume = true
                    }
                )
            )
        }
    }

    private fun refreshHealthJobState() {
        if (healthJob == null) {
            healthJob = viewModelScope.launch {
                WiliotHealthMonitor.state.collectLatest {
                    mState.upd {
                        copy(
                            wiliotHealth = it,
                            vBrgCfg = VConfig.config
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        healthJob?.cancel()
        healthJob = null
    }

}