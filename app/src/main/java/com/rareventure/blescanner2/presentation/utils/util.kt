package com.rareventure.blescanner2.presentation.utils

import kotlin.math.max
import kotlin.math.min

// Helper function to map RSSI to a 0.0f-1.0f value for color brightness
fun mapRssiToValue(rssi: Int, minRssi: Int = -100, maxRssi: Int = -40): Float {
    val clampedRssi = max(minRssi, min(rssi, maxRssi))
    return (clampedRssi - minRssi).toFloat() / (maxRssi - minRssi).toFloat()
}
