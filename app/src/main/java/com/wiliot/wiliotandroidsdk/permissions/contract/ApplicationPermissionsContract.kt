package com.wiliot.wiliotandroidsdk.permissions.contract

interface ApplicationPermissionsContract {

    enum class PermissionsBundle(val permissionsList: List<Permission>) {

        GATEWAY_MODE(
            listOf(
                Permission.BLUETOOTH_NEARBY,
                Permission.BLUETOOTH_ENABLE,
                Permission.LOCATION,
                Permission.BACKGROUND_LOCATION,
                Permission.LOCATION_ENABLE,
                Permission.INTERNET_ADAPTER_ENABLE,
                Permission.OPTIMISATION_DISABLE,
                Permission.NOTIFICATIONS_33,
            )
        )

    }

    enum class Permission {
        BLUETOOTH_NEARBY,
        BLUETOOTH_ENABLE,
        LOCATION,
        BACKGROUND_LOCATION,
        LOCATION_ENABLE,
        INTERNET_ADAPTER_ENABLE,
        OPTIMISATION_DISABLE,
        NOTIFICATIONS_33
    }

    enum class Result {
        GRANTED, REJECTED
    }

    // checks
    fun isPermissionGranted(permission: Permission): Boolean
    fun allPermissionsGranted(bundle: PermissionsBundle): Boolean

    // requests
    fun requestPermission(permission: Permission, resultCallback: PermissionResultCallback)
    fun requestApplicationSettings()
    fun requestGpsSettings()

}

typealias PermissionResultCallback = (
    ApplicationPermissionsContract.Permission,
    ApplicationPermissionsContract.Result
) -> Unit