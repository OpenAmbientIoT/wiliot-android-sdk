package com.wiliot.wiliotandroidsdk.permissions

import com.wiliot.wiliotandroidsdk.R
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract
import com.wiliot.wiliotandroidsdk.utils.log
import com.wiliot.wiliotandroidsdk.utils.logTag

object PermissionsHelper {

    private val logTag = logTag()

    private val permissionsRequestedAgain =
        mutableSetOf<ApplicationPermissionsContract.Permission>()

    fun isAllPermissionsGranted(
        appPermissions: ApplicationPermissionsContract,
        bundle: ApplicationPermissionsContract.PermissionsBundle
    ): Boolean {
        return bundle.permissionsList.map {
            appPermissions.isPermissionGranted(it)
        }.reduce { acc, b ->
            acc && b
        }
    }

    fun checkPermissions(
        appPermissions: ApplicationPermissionsContract,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        onPermissionChainCheckFailed: () -> Unit,
        onPermissionChainCheckSucceed: () -> Unit,
        onCustomRequestPermission: (
            permission: ApplicationPermissionsContract.Permission,
            bundle: ApplicationPermissionsContract.PermissionsBundle
        ) -> Unit
    ) {
        log("checkPermissions", logTag)
        bundle.permissionsList.forEach { bundledPermission ->
            if (appPermissions.isPermissionGranted(bundledPermission).not()) {
                if (bundledPermission !in permissionsRequestedAgain) {
                    when (bundledPermission) {
                        ApplicationPermissionsContract.Permission.BACKGROUND_LOCATION,
                        ApplicationPermissionsContract.Permission.LOCATION_ENABLE,
                        ApplicationPermissionsContract.Permission.INTERNET_ADAPTER_ENABLE -> {
                            permissionsRequestedAgain.add(bundledPermission)
                            onCustomRequestPermission.invoke(bundledPermission, bundle)
                        }
                        else -> {
                            appPermissions.requestPermission(bundledPermission) { permission, result ->
                                onPermissionGrantResultReceived(
                                    appPermissions,
                                    permission,
                                    bundle,
                                    result,
                                    onPermissionChainCheckFailed,
                                    onPermissionChainCheckSucceed,
                                    onCustomRequestPermission
                                )
                            }
                        }
                    }
                } else {
                    onPermissionChainCheckFailed.invoke()
                }
                return
            }
        }
        log("checkPermissions -> ALL GRANTED", logTag)
        onPermissionChainCheckSucceed.invoke()
        permissionsRequestedAgain.clear()
    }

    fun reset() {
        log("reset", logTag)
        permissionsRequestedAgain.clear()
    }

    fun generatePermissionDialogData(
        permission: ApplicationPermissionsContract.Permission,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        domainCallback: () -> Unit
    ): RequestPermissionDialog {
        return when (permission) {
            ApplicationPermissionsContract.Permission.BLUETOOTH_NEARBY -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_bluetooth_nearby_title,
                descriptionText = R.string.permission_bluetooth_nearby_description,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.BLUETOOTH_ENABLE -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_bluetooth_enable_title,
                descriptionText = R.string.permission_bluetooth_nearby_description,
                positiveButtonText = R.string.general_ok,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.LOCATION -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_location_title,
                descriptionText = R.string.permission_location_description,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.BACKGROUND_LOCATION -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_bg_location_title,
                descriptionText = R.string.permission_location_background_description,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.LOCATION_ENABLE -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_location_enable_title,
                descriptionText = R.string.permission_location_description,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.INTERNET_ADAPTER_ENABLE -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_internet_enable_title,
                descriptionText = R.string.permission_internet_enable_description,
                positiveButtonText = R.string.general_ok,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.OPTIMISATION_DISABLE -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_optimisation_disable_title,
                descriptionText = R.string.permission_optimisation_disable_description,
                positiveButtonText = R.string.general_ok,
                domainCallback = domainCallback
            )
            ApplicationPermissionsContract.Permission.NOTIFICATIONS_33 -> RequestPermissionDialog(
                permissionsBundle = bundle,
                permission = permission,
                titleText = R.string.permission_notifications_title,
                descriptionText = R.string.permission_notifications_description,
                positiveButtonText = R.string.general_ok,
                domainCallback = domainCallback
            )
        }
    }

    private fun onPermissionGrantResultReceived(
        appPermissions: ApplicationPermissionsContract,
        permission: ApplicationPermissionsContract.Permission,
        bundle: ApplicationPermissionsContract.PermissionsBundle,
        result: ApplicationPermissionsContract.Result,
        onPermissionChainCheckFailed: () -> Unit,
        onPermissionChainCheckSucceed: () -> Unit,
        onCustomRequestPermission: (
            permission: ApplicationPermissionsContract.Permission,
            bundle: ApplicationPermissionsContract.PermissionsBundle
        ) -> Unit
    ) {
        log(
            "onPermissionGrantResultReceived($bundle, $permission, $result)",
            logTag
        )
        if (result == ApplicationPermissionsContract.Result.GRANTED) {
            checkPermissions(
                appPermissions,
                bundle,
                onPermissionChainCheckFailed,
                onPermissionChainCheckSucceed,
                onCustomRequestPermission
            )
        } else {
            val notRequestedAgainBefore = permission !in permissionsRequestedAgain

            if (notRequestedAgainBefore) {
                permissionsRequestedAgain.add(permission)
                onCustomRequestPermission.invoke(permission, bundle)
            } else {
                onPermissionChainCheckFailed.invoke()
            }
        }
    }

}