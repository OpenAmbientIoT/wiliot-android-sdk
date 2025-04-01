package com.wiliot.wiliotandroidsdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.wiliot.wiliotandroidsdk.di.ApplicationPermissionsDelegate
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract
import com.wiliot.wiliotandroidsdk.ui.theme.WiliotAndroidSDKTheme
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.health.WiliotHealth
import com.wiliot.wiliotcore.utils.helper.start
import com.wiliot.wiliotcore.utils.helper.stop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var appPermissionsDelegate: ApplicationPermissionsContract

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (appPermissionsDelegate as? ApplicationPermissionsDelegate)?.setupHostActivity(this)
        enableEdgeToEdge()
        setContent {
            WiliotAndroidSDKTheme {
                MainScreenUI(
                    viewModel = mViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mViewModel.notifyActivityResumed()
    }

}

@Composable
fun MainScreenUI(
    viewModel: MainViewModel
) {

    val vmState = viewModel.state.value

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GatewayId: ${Wiliot.getFullGWId()}")
            Spacer(modifier = Modifier.height(16.dp))
            HealthCard(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                vmState.wiliotHealth
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = {
                    viewModel.checkBeforeStart {
                        Wiliot.start()
                    }
                }) {
                    Text("Start")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    Wiliot.stop()
                }) {
                    Text("Stop")
                }
            }
        }
    }

    val showCustomPermissionRequestDialog = remember { mutableStateOf(false) }
    showCustomPermissionRequestDialog.value = vmState.customPermissionDialog != null

    if (showCustomPermissionRequestDialog.value) {
        vmState.customPermissionDialog.takeIf { it != null }?.let { dlgData ->
            PermissionRequestDialog(
                showDialog = showCustomPermissionRequestDialog,
                titleText = stringResource(id = dlgData.titleText),
                descriptionText = stringResource(id = dlgData.descriptionText),
                positiveButtonText = stringResource(id = dlgData.positiveButtonText),
                negativeButtonText = stringResource(id = dlgData.negativeButtonText),
                resultCallback = { accepted ->
                    if (accepted) {
                        dlgData.domainCallback?.invoke()
                        viewModel.onCustomPermissionDialogAccepted(
                            dlgData.permissionsBundle,
                            dlgData.permission
                        )
                    } else {
                        when (dlgData.permissionsBundle) {
                            ApplicationPermissionsContract.PermissionsBundle.GATEWAY_MODE -> {
                                viewModel.cancelGatewayPermissionCheck()
                            }

                            else -> {
                                // Nothing
                            }
                        }
                    }
                }
            )
        }
    }

}

@Composable
fun PermissionRequestDialog(
    showDialog: MutableState<Boolean>,
    titleText: String,
    descriptionText: String,
    positiveButtonText: String,
    negativeButtonText: String,
    resultCallback: (accepted: Boolean) -> Unit
) {

    if (showDialog.value) {
        AlertDialog(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            onDismissRequest = {
                showDialog.value = false
            },
            text = {
                Column(modifier = Modifier.wrapContentHeight()) {
                    Text(
                        text = titleText,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = descriptionText,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog.value = false
                        resultCallback.invoke(true)
                    }
                ) {
                    Text(positiveButtonText)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog.value = false
                        resultCallback.invoke(false)
                    }
                ) {
                    Text(negativeButtonText)
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = false
            )
        )
    }

}

@Composable
private fun HealthCard(
    modifier: Modifier,
    health: WiliotHealth
) {

    val shape = RoundedCornerShape(16.dp)
    val elevation = 2.dp

    Surface(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        shape = shape,
        shadowElevation = elevation
    ) {

        ConstraintLayout(modifier = Modifier.fillMaxWidth()) {

            val (title, monitor) = createRefs()

            Text(
                modifier = Modifier.constrainAs(title) {
                    top.linkTo(parent.top, margin = 16.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                    width = Dimension.fillToConstraints
                },
                text = "Health Monitor"
            )

            Column(
                modifier = Modifier.constrainAs(monitor) {
                    top.linkTo(title.bottom, margin = 16.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                    width = Dimension.fillToConstraints
                    height = Dimension.wrapContent
                }
            ) {

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Uptime:",
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = health.getUptime(),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Uplink connected:",
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = health.mqttClientConnected.toString()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Uplink last delivery:",
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = health.getLastUplinkSentTime()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Uplink last delivery timestamp:"
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = health.lastUplinkDataSentTime.toString()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Phoenix (service) enabled:"
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = Wiliot.configuration.phoenix.toString()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "Downlink msg counter:"
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = health.downlinkMessagesCounter.toString()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "BT pkts last min:"
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = if (Wiliot.configuration.btPacketsCounterEnabled)
                            health.btPacketsLastMinute.toString()
                        else
                            "DISABLED"
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = "BT pkts last 10 min:"
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )
                    Text(
                        text = if (Wiliot.configuration.btPacketsCounterEnabled)
                            health.btPacketsLast10minutes.toString()
                        else
                            "DISABLED"
                    )
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

            }

        }

    }

}