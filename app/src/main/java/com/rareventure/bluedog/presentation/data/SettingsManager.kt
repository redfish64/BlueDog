package com.rareventure.bluedog.presentation.data

import android.content.Context
import android.content.SharedPreferences
import android.media.ToneGenerator
import com.rareventure.bluedog.presentation.utils.AppDefaults

/**
 * Manages saving and loading of app settings using SharedPreferences.
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ble_scanner_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VIBRATION = "vibration_duration_ms"
        private const val KEY_CHIME = "is_chime_on"
        private const val KEY_CHIME_TONE = "chime_tone"
        private const val KEY_DIVISIONS = "num_divisions" // Legacy key (stored actual divisions)
        private const val KEY_USABLE_SLOTS = "usable_slots" // New key (stores usable slots)
        private const val KEY_BLOCKED = "blocked_set"
        private const val KEY_DIM_TIMEOUT = "dim_timeout_ms"
        private const val KEY_DIM_LEVEL = "dim_level"
    }

    fun saveVibration(duration: Long) {
        prefs.edit().putLong(KEY_VIBRATION, duration).apply()
    }

    fun loadVibration(): Long {
        return prefs.getLong(KEY_VIBRATION, 50L) // Default value
    }

    fun saveChime(isOn: Boolean) {
        prefs.edit().putBoolean(KEY_CHIME, isOn).apply()
    }

    fun loadChime(): Boolean {
        return prefs.getBoolean(KEY_CHIME, false) // Default value false (no chime)
    }

    fun saveChimeTone(tone: Int) {
        prefs.edit().putInt(KEY_CHIME_TONE, tone).apply()
    }

    fun loadChimeTone(): Int {
        return prefs.getInt(KEY_CHIME_TONE, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD) // Default tone
    }

    fun saveUsableSlots(slots: Int) {
        prefs.edit().putInt(KEY_USABLE_SLOTS, slots).apply()
    }

    fun loadUsableSlots(): Int {
        // Check if new key exists
        if (prefs.contains(KEY_USABLE_SLOTS)) {
            return prefs.getInt(KEY_USABLE_SLOTS, 22)
        }

        // Migrate from old key if it exists
        if (prefs.contains(KEY_DIVISIONS)) {
            val oldDivisions = prefs.getInt(KEY_DIVISIONS, 24)
            val migratedSlots = when (oldDivisions) {
                12 -> 10
                24 -> 22
                36 -> 32
                else -> oldDivisions - 1 // Assume 1 excluded slot for unknown values
            }
            // Save migrated value with new key
            saveUsableSlots(migratedSlots)
            return migratedSlots
        }

        return 22 // Default value: 22 usable slots
    }

    fun saveDimTimeout(timeoutMs: Long) {
        prefs.edit().putLong(KEY_DIM_TIMEOUT, timeoutMs).apply()
    }

    fun loadDimTimeout(): Long {
        return prefs.getLong(KEY_DIM_TIMEOUT, AppDefaults.DEFAULT_DIM_TIMEOUT_MS) // Default value 5 seconds
    }

    fun saveBlockedDevices(blocked: Set<BlockedDevice>) {
        val encodedSet = blocked.map { device ->
            "${device.address}|${device.deviceName ?: ""}"
        }.toSet()
        prefs.edit().putStringSet(KEY_BLOCKED, encodedSet).apply()
    }

    fun loadBlockedDevices(): MutableSet<BlockedDevice> {
        val encodedSet = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        return encodedSet.map { encoded ->
            val parts = encoded.split("|", limit = 2)
            BlockedDevice(
                address = parts[0],
                deviceName = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            )
        }.toMutableSet()
    }

    fun clearBlocked() {
        prefs.edit().remove(KEY_BLOCKED).apply()
    }

    fun saveDimLevel(level: String) {
        prefs.edit().putString(KEY_DIM_LEVEL, level).apply()
    }

    fun loadDimLevel(): String {
        return prefs.getString(KEY_DIM_LEVEL, "Black") ?: "Black" // Default value "Black"
    }

}
