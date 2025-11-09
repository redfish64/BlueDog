package com.rareventure.blescanner2.presentation.data

// Data class to hold the latest info for a scanned device
data class ScannedDevice(
    val deviceName: String?,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
