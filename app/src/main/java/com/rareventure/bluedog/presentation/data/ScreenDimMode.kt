package com.rareventure.bluedog.presentation.data

import android.content.Context
import com.rareventure.bluedog.R

/**
 * Represents the different screen dimming modes available when the device is inactive.
 */
enum class ScreenDimMode(
    val brightness: Float,
    val showOverlay: Boolean,
    val stringResId: Int,
    val displayName: String  // Kept for backward compatibility with SharedPreferences
) {
    BLACK(0.00f, true, R.string.dim_mode_black, "Black"),
    DARK(0.00f, false, R.string.dim_mode_dark, "1%"),
    DIM(0.05f, false, R.string.dim_mode_dim, "5%");

    companion object {
        /**
         * Converts a string value from SharedPreferences to a ScreenDimMode enum.
         * Defaults to BLACK if the value is not recognized.
         */
        fun fromString(value: String): ScreenDimMode {
            return values().find { it.displayName == value } ?: BLACK
        }
    }

    /**
     * Converts this enum to a string for storage in SharedPreferences.
     */
    fun toStorageString(): String = displayName

    /**
     * Gets the localized display name for this mode.
     */
    fun getLocalizedName(context: Context): String = context.getString(stringResId)
}
