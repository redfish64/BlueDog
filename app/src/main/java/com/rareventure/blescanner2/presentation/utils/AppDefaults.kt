package com.rareventure.blescanner2.presentation.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Central location for commonly reused literal values to avoid magic numbers.
 */
object AppDefaults {
    // Timeouts / intervals
    const val DEVICE_STALE_TIMEOUT_MS: Long = 5_000L
    const val DEVICE_TIMEOUT_CHECK_INTERVAL_MS: Long = 2_000L
    const val DEFAULT_DIM_TIMEOUT_MS: Long = 5_000L
    const val DIM_TIMEOUT_THIRTY_SECONDS_MS: Long = 30_000L
    const val DIM_TIMEOUT_DISABLED_MS: Long = 0L
    const val DEVICE_HISTORY_RETENTION_MS: Long = 90L * 24 * 60 * 60 * 1000 // 90 days

    // UI literals
    const val DIAL_CANVAS_SIZE_FRACTION: Float = 0.9f
    const val BATTERY_INDICATOR_RADIUS_FRACTION: Float = 0.35f
    const val BATTERY_START_ANGLE_DEGREES: Float = -90f
    const val BATTERY_MAX_SWEEP_DEGREES: Float = 360f
    const val BATTERY_PERCENT_MULTIPLIER: Int = 100
    const val BATTERY_SEGMENTS: Int = 360

    const val SETTINGS_BUTTON_EMOJI = "⚙"
    const val BLOCK_BUTTON_EMOJI = "🚫"
    const val TRIANGLE_INDICATOR_ALPHA: Float = 0.7f

    const val CHIME_OFF_ID: Int = -1

    val DEFAULT_SCREEN_PADDING = PaddingValues(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
    val DEFAULT_VERTICAL_ITEM_SPACING = 4.dp
    val MEDIUM_VERTICAL_ITEM_SPACING = 8.dp
    val DEFAULT_SECTION_SPACING = 16.dp
    val BATTERY_SPACER_HEIGHT = 16.dp
    val BATTERY_INDICATOR_STROKE_WIDTH = 6.dp
    val TRIANGLE_INDICATOR_PADDING_END = 20.dp
    val TRIANGLE_INDICATOR_WIDTH = 8.dp
    val TRIANGLE_INDICATOR_HEIGHT = 16.dp
    val SWIPE_MIN_DISTANCE = 40.dp
    const val GROUP_LABEL_VERTICAL_FRACTION: Float = 0.20f
}
