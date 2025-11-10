package com.rareventure.blescanner2.presentation.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.rareventure.blescanner2.R
import com.rareventure.blescanner2.presentation.components.BatteryIndicator
import com.rareventure.blescanner2.presentation.components.DeviceOverlay
import com.rareventure.blescanner2.presentation.components.DeviceDial
import com.rareventure.blescanner2.presentation.data.ScannedDevice
import com.rareventure.blescanner2.presentation.utils.AppDefaults
import com.rareventure.blescanner2.presentation.utils.runIfInteractionAllowed

@Composable
fun ScannerScreen(
    isScanning: Boolean,
    hasPermissions: Boolean,
    scannedDevices: Map<String, ScannedDevice>,
    slotMap: Map<String, Int>,
    numDivisions: Int,
    currentTime: Long,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPermissionsRequest: () -> Unit,
    onSettingsClick: () -> Unit,
    onSliceTapped: (String) -> Unit,
    selectedDevice: ScannedDevice?,
    onDismissOverlay: () -> Unit,
    onBlockDevice: (String) -> Unit,
    excludedSlots: Set<Int> = emptySet(),
    isTestMode: Boolean = false,
    onTestModeEnd: () -> Unit = {},
    context: Context,
    batteryLevel: Float = 1.0f
) {
    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            DeviceDial(
                devices = scannedDevices,
                slotMap = slotMap,
                numDivisions = numDivisions,
                onSliceTapped = onSliceTapped,
                modifier = Modifier.fillMaxSize(AppDefaults.DIAL_CANVAS_SIZE_FRACTION),
                excludedSlots = excludedSlots,
                isScanning = isScanning,
                currentTime = currentTime
            )

            // Central control column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isTestMode) {
                    val testModeColors = ButtonDefaults.buttonColors(backgroundColor = Color.Green)
                    Button(
                        onClick = {
                            context.runIfInteractionAllowed { onTestModeEnd() }
                        },
                        colors = testModeColors
                    ) {
                        Text(stringResource(R.string.stop_test), textAlign = TextAlign.Center, color = Color.Black)
                    }
                } else if (!isScanning) {
                    if (hasPermissions) {
                        Button(onClick = { context.runIfInteractionAllowed { onStartScan() } }) {
                            Text(stringResource(R.string.start_scan), textAlign = TextAlign.Center)
                        }
                    } else {
                        Chip(
                            onClick = {
                                context.runIfInteractionAllowed { onPermissionsRequest() }
                            },
                            label = { Text(stringResource(R.string.grant_permissions)) },
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                } else {
                    val stopButtonColors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    Button(
                        onClick = {
                            context.runIfInteractionAllowed { onStopScan() }
                        },
                        colors = stopButtonColors
                    ) {
                        Text(stringResource(R.string.stop_scan), textAlign = TextAlign.Center)
                    }
                }
            }

            // Settings Icon at the bottom with battery percentage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Spacer to push battery percentage down away from indicator
                    Spacer(modifier = Modifier.height(AppDefaults.BATTERY_SPACER_HEIGHT))

                    // Battery percentage text
                    Text(
                        text = " ${(batteryLevel * AppDefaults.BATTERY_PERCENT_MULTIPLIER).toInt()}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )

                    // Settings gear icon
                    Button(
                        onClick = {
                            context.runIfInteractionAllowed { onSettingsClick() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Transparent,
                            contentColor = MaterialTheme.colors.onBackground
                        )
                    ) {
                        Text(
                            text = AppDefaults.SETTINGS_BUTTON_EMOJI,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            // Battery indicator drawn on top (only show when permissions granted)
            if (hasPermissions) {
                BatteryIndicator(
                    batteryLevel = batteryLevel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(AppDefaults.DIAL_CANVAS_SIZE_FRACTION)
                )
            }

// Render overlay last so it appears on top
            if (selectedDevice != null) {
                DeviceOverlay(
                    device = selectedDevice,
                    onBlock = { onBlockDevice(selectedDevice.address) },
                    onDismiss = onDismissOverlay,
                    currentTime = currentTime,
                    isScanning = isScanning
                )
            }
        }
    }
}
