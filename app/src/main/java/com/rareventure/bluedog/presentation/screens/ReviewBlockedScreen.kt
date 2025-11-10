package com.rareventure.bluedog.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.rareventure.bluedog.R
import com.rareventure.bluedog.presentation.data.BlockedDevice
import com.rareventure.bluedog.presentation.utils.AppDefaults

@Composable
fun ReviewBlockedScreen(
    blockedDevices: Set<BlockedDevice>,
    onSaveAndUnblock: (Set<BlockedDevice>) -> Unit,
    onBackClick: () -> Unit,
    scrollState: ScalingLazyListState
) {
    // Local state to track which devices are currently blocked (toggled on)
    val blockedStates = remember(blockedDevices) {
        mutableStateOf(blockedDevices.associateWith { true }.toMutableMap())
    }

    // Helper to save changes when going back
    val saveAndGoBack = {
        // Get the set of devices to unblock (those toggled off)
        val devicesToUnblock = blockedStates.value.filter { !it.value }.keys
        if (devicesToUnblock.isNotEmpty()) {
            onSaveAndUnblock(devicesToUnblock)
        }
        onBackClick()
    }

    // Handle physical back button
    BackHandler(enabled = true) {
        saveAndGoBack()
    }
    ScalingLazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDefaults.DEFAULT_VERTICAL_ITEM_SPACING),
        contentPadding = AppDefaults.DEFAULT_SCREEN_PADDING
    ) {
        if (blockedDevices.isEmpty()) {
            item { Text(stringResource(R.string.no_devices_blocked), style = MaterialTheme.typography.body2) }
        } else {
            items(blockedDevices.toList()) { device ->
                val isBlocked = blockedStates.value[device] ?: true
                ToggleChip(
                    checked = isBlocked,
                    onCheckedChange = { checked ->
                        blockedStates.value = blockedStates.value.toMutableMap().apply {
                            this[device] = checked
                        }
                    },
                    label = {
                        Text(
                            device.deviceName ?: stringResource(R.string.unknown_device),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            device.address,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = { Switch(checked = isBlocked) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Unblock All button at the bottom
            item { Spacer(modifier = Modifier.height(AppDefaults.DEFAULT_SECTION_SPACING)) }
            item {
                Chip(
                    onClick = {
                        // Toggle all devices off
                        blockedStates.value = blockedStates.value.mapValues { false }.toMutableMap()
                    },
                    label = { Text(stringResource(R.string.unblock_all)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = MaterialTheme.colors.error)
                )
            }
        }
    }
}
