package com.rareventure.blescanner2.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.rareventure.blescanner2.R
import com.rareventure.blescanner2.presentation.data.ScreenDimMode
import com.rareventure.blescanner2.presentation.utils.AppDefaults

private const val VIBRATION_OFF_DURATION_MS = 0L
private const val VIBRATION_SHORT_DURATION_MS = 50L
private const val VIBRATION_LONG_DURATION_MS = 200L
private val SETTINGS_VIBRATION_OPTIONS = listOf(
    VIBRATION_OFF_DURATION_MS,
    VIBRATION_SHORT_DURATION_MS,
    VIBRATION_LONG_DURATION_MS
)
private val SETTINGS_SCREEN_PADDING = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
private val DIM_TIMEOUT_OPTIONS = listOf(
    AppDefaults.DEFAULT_DIM_TIMEOUT_MS,
    AppDefaults.DIM_TIMEOUT_THIRTY_SECONDS_MS,
    AppDefaults.DIM_TIMEOUT_DISABLED_MS
)

@Composable
fun SettingsScreen(
    vibrationDurationMs: Long,
    onVibrationChange: (Long) -> Unit,
    isChimeOn: Boolean,
    onChimeChange: (Boolean) -> Unit,
    usableSlots: Int,
    onSelectDivisions: () -> Unit,
    dimTimeoutMs: Long,
    onDimTimeoutChange: (Long) -> Unit,
    dimLevel: ScreenDimMode,
    onDimLevelChange: (ScreenDimMode) -> Unit,
    onBackClick: () -> Unit,
    onReviewBlocked: () -> Unit,
    onTestClick: () -> Unit,
    onSelectChime: () -> Unit,
    scrollState: ScalingLazyListState
) {
    ScalingLazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDefaults.MEDIUM_VERTICAL_ITEM_SPACING),
        contentPadding = SETTINGS_SCREEN_PADDING
    ) {
        // --- Vibration Settings ---
        item { Text(stringResource(R.string.vibration_alert_ms), style = MaterialTheme.typography.caption1) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDefaults.DEFAULT_VERTICAL_ITEM_SPACING)) {
                for (duration in SETTINGS_VIBRATION_OPTIONS) {
                    val isSelected = vibrationDurationMs == duration
                    Chip(
                        onClick = { onVibrationChange(duration) },
                        label = { Text(if (duration == VIBRATION_OFF_DURATION_MS) stringResource(R.string.off) else "$duration") },
                        colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }

        // --- Chime Setting ---
        item {
            Chip(
                onClick = onSelectChime,
                label = { Text(stringResource(R.string.audible_chime)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- Screen Dim Timeout Settings ---
        item { Spacer(modifier = Modifier.height(AppDefaults.MEDIUM_VERTICAL_ITEM_SPACING)) }
        item { Text(stringResource(R.string.screen_dim_timeout), style = MaterialTheme.typography.caption1) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDefaults.DEFAULT_VERTICAL_ITEM_SPACING)) {
                val timeouts = DIM_TIMEOUT_OPTIONS // 5s, 30s, Off
                val labels = listOf(stringResource(R.string.timeout_5s), stringResource(R.string.timeout_30s), stringResource(R.string.off))
                for (i in timeouts.indices) {
                    val timeout = timeouts[i]
                    val label = labels[i]
                    val isSelected = dimTimeoutMs == timeout
                    Chip(
                        onClick = { onDimTimeoutChange(timeout) },
                        label = { Text(label) },
                        colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }

        // --- Dimming Level Settings ---
        item { Text(stringResource(R.string.screen_dim_level), style = MaterialTheme.typography.caption1) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDefaults.DEFAULT_VERTICAL_ITEM_SPACING)) {
                val levels = ScreenDimMode.values()
                val isDimmingDisabled = dimTimeoutMs == AppDefaults.DIM_TIMEOUT_DISABLED_MS
                for (level in levels) {
                    val isSelected = dimLevel == level
                    Chip(
                        onClick = { if (!isDimmingDisabled) onDimLevelChange(level) },
                        label = { Text(stringResource(level.stringResId)) },
                        colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                        enabled = !isDimmingDisabled
                    )
                }
            }
        }

        // --- Radar Divisions Settings ---
        item { Spacer(modifier = Modifier.height(AppDefaults.MEDIUM_VERTICAL_ITEM_SPACING)) }
        item {
            Chip(
                onClick = onSelectDivisions,
                label = { Text(stringResource(R.string.radar_divisions_format, usableSlots)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- Favorites management ---
        item { Chip(onClick = onReviewBlocked, label = { Text(stringResource(R.string.review_blocked)) }, modifier = Modifier.fillMaxWidth()) }

        // --- Test Mode ---
        // IMPORTANT: Keep this button at the bottom of the settings list
        item { Chip(onClick = onTestClick, label = { Text(stringResource(R.string.test)) }, modifier = Modifier.fillMaxWidth()) }
    }
}
