package com.wiliot.wiliotandroidsdk.permissions

import androidx.annotation.StringRes
import com.wiliot.wiliotandroidsdk.R
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract

class RequestPermissionDialog(
    val permissionsBundle: ApplicationPermissionsContract.PermissionsBundle,
    val permission: ApplicationPermissionsContract.Permission,
    @StringRes val titleText: Int,
    @StringRes val descriptionText: Int,
    @StringRes val positiveButtonText: Int = R.string.permission_go_to_settings,
    @StringRes val negativeButtonText: Int = R.string.general_cancel,
    val domainCallback: (() -> Unit)? = null
)