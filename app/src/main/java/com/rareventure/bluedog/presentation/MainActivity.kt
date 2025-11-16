package com.rareventure.bluedog.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.view.Display
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.rememberScalingLazyListState
import com.rareventure.bluedog.presentation.data.BlockedDevice
import com.rareventure.bluedog.presentation.data.ScannedDevice
import com.rareventure.bluedog.presentation.data.ScreenDimMode
import com.rareventure.bluedog.presentation.data.SettingsManager
import com.rareventure.bluedog.presentation.screens.ChimeSelectionScreen
import com.rareventure.bluedog.presentation.screens.DivisionsSelectionScreen
import com.rareventure.bluedog.presentation.screens.ReviewBlockedScreen
import com.rareventure.bluedog.presentation.screens.ScannerScreen
import com.rareventure.bluedog.presentation.screens.SettingsScreen
import com.rareventure.bluedog.presentation.theme.BLEScannerTheme
import com.rareventure.bluedog.R
import com.rareventure.bluedog.presentation.utils.AppDefaults
import com.rareventure.bluedog.presentation.utils.runIfInteractionAllowed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "BlueDogAlert"
private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "ble_scanner_channel"

// Simple navigation enum
private enum class Screen { SCANNER, SETTINGS, REVIEW_BLOCKED, CHIME_SELECTION, DIVISIONS_SELECTION }

/**
 * Calculates which slots should be excluded (reserved for gear icon area).
 * Slots are centered at the bottom (6 o'clock position).
 *
 * @param actualDivisions Total number of divisions in the dial
 * @param usableSlots Number of slots available for devices
 * @return Set of slot indices to exclude
 */
private fun calculateExcludedSlots(actualDivisions: Int, usableSlots: Int): Set<Int> {
    val numExcluded = actualDivisions - usableSlots
    if (numExcluded <= 0) return emptySet()

    val centerSlot = actualDivisions / 2
    val halfExcluded = numExcluded / 2

    return if (numExcluded % 2 == 0) {
        // Even number of excluded slots: distribute evenly on both sides
        (centerSlot - halfExcluded until centerSlot + halfExcluded).toSet()
    } else {
        // Odd number: include center slot plus equal number on each side
        ((centerSlot - halfExcluded)..(centerSlot + halfExcluded)).toSet()
    }
}

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private lateinit var settingsManager: SettingsManager

    // Track manual screen dimming
    private var isScreenDimmed: Boolean = false
    private var lastUserInteractionTime: Long = 0
    private var dimTimeoutMillis: Long = AppDefaults.DEFAULT_DIM_TIMEOUT_MS // Dim after inactivity
    private var dimLevel: ScreenDimMode = ScreenDimMode.BLACK // Dimming level

    // Battery monitoring
    private var batteryLevel: Float = 1.0f
    private var batteryLevelCallback: ((Float) -> Unit)? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryLevel = level.toFloat() / scale.toFloat()
                    batteryLevelCallback?.invoke(batteryLevel)
                }
            }
        }
    }

    /**
     * Dims the screen manually by setting window brightness based on dim level.
     */
    private fun dimScreen(updateComposeState: ((Boolean) -> Unit)? = null) {
        if (!isScreenDimmed) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = dimLevel.brightness
            window.attributes = layoutParams
            isScreenDimmed = true
            updateComposeState?.invoke(true)
            Log.d(TAG, "Screen manually dimmed to level: ${dimLevel.displayName}")
        }
    }

    /**
     * Brightens the screen by restoring system default brightness.
     */
    private fun brightenScreen(updateComposeState: ((Boolean) -> Unit)? = null) {
        if (isScreenDimmed) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = -1.0f // Use system default brightness
            window.attributes = layoutParams
            isScreenDimmed = false
            updateComposeState?.invoke(false)
            Log.d(TAG, "Screen brightness restored")
        }
    }

    /**
     * Records user interaction and brightens screen if dimmed.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        lastUserInteractionTime = System.currentTimeMillis()
        brightenScreen()
    }

    /**
     * Handle physical button presses to wake screen from dimmed state.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Record interaction and brighten screen if dimmed
        lastUserInteractionTime = System.currentTimeMillis()
        if (isScreenDimmed) {
            brightenScreen()
            return true // Consume the event if screen was dimmed
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Gets the last user interaction time (public for Composable access).
     */
    fun getLastUserInteractionTime(): Long = lastUserInteractionTime

    /**
     * Gets the dim timeout (public for Composable access).
     */
    fun getDimTimeout(): Long = dimTimeoutMillis

    /**
     * Sets the dim timeout (public for Composable access).
     */
    fun setDimTimeout(timeoutMs: Long) {
        dimTimeoutMillis = timeoutMs
    }

    /**
     * Sets the dim level (public for Composable access).
     */
    fun setDimLevel(level: ScreenDimMode) {
        dimLevel = level
    }

    /**
     * Gets the dim level (public for Composable access).
     */
    fun getDimLevel(): ScreenDimMode = dimLevel

    /**
     * Dims the screen (public for Composable access).
     */
    fun dimScreenPublic(updateComposeState: ((Boolean) -> Unit)? = null) = dimScreen(updateComposeState)

    /**
     * Brightens the screen (public for Composable access).
     */
    fun brightenScreenPublic(updateComposeState: ((Boolean) -> Unit)? = null) {
        lastUserInteractionTime = System.currentTimeMillis()
        brightenScreen(updateComposeState)
    }

    /**
     * Checks if a tap should be ignored because screen was just brightened.
     * Returns true if screen was dimmed and this is the first tap to brighten it.
     */
    fun shouldIgnoreTapAfterDimmed(updateComposeState: ((Boolean) -> Unit)? = null): Boolean {
        if (isScreenDimmed) {
            Log.d(TAG, "Screen was dimmed - ignoring tap to brighten")
            lastUserInteractionTime = System.currentTimeMillis()
            brightenScreen(updateComposeState) // Pass the callback to update Compose state
            return true
        }
        onUserInteraction() // Update interaction time
        return false
    }

    /**
     * Returns whether the screen is currently dimmed.
     */
    fun isScreenCurrentlyDimmed(): Boolean = isScreenDimmed

    /**
     * Gets the current battery level (0.0 to 1.0).
     */
    fun getBatteryLevel(): Float = batteryLevel

    /**
     * Sets a callback to be notified of battery level changes.
     */
    fun setBatteryLevelCallback(callback: (Float) -> Unit) {
        batteryLevelCallback = callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(applicationContext)
        dimTimeoutMillis = settingsManager.loadDimTimeout()
        dimLevel = ScreenDimMode.fromString(settingsManager.loadDimLevel())
        createNotificationChannel()

        // Register battery receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(batteryReceiver, filter)
        // Get initial battery level
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryLevel = level.toFloat() / scale.toFloat()
            }
        }

        setContent {
            BLEScannerTheme {
                BleScannerApp(bleScanner, this, settingsManager)
            }
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.app_name)
        val descriptionText = getString(R.string.ongoing_ble_scanning)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    fun startOngoingActivity() {
        Log.d(TAG, "Starting OngoingActivity")
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.scanning_for_devices))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NOTIFICATION_ID,
            notificationBuilder
        )
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().build())
            .build()

        ongoingActivity.apply(applicationContext)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        // Keep screen on for BLE scanning
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "OngoingActivity started with FLAG_KEEP_SCREEN_ON")
    }

    fun stopOngoingActivity() {
        Log.d(TAG, "Stopping OngoingActivity")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        // Restore screen brightness to system default
        val layoutParams = window.attributes
        layoutParams.screenBrightness = -1.0f
        window.attributes = layoutParams
        isScreenDimmed = false

        // Clear keep screen on flag
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "OngoingActivity stopped, brightness restored, and FLAG_KEEP_SCREEN_ON cleared")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister battery receiver
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        // Ensure brightness is restored on app destroy
        val layoutParams = window.attributes
        layoutParams.screenBrightness = -1.0f
        window.attributes = layoutParams
    }
}


@SuppressLint("MissingPermission")
@Composable
fun BleScannerApp(
    bleScanner: android.bluetooth.le.BluetoothLeScanner?,
    context: Context,
    settingsManager: SettingsManager
) {
    BleScannerContent(
        bleScanner = bleScanner,
        context = context,
        settingsManager = settingsManager
    )
}

@SuppressLint("MissingPermission")
@Composable
fun BleScannerContent(
    bleScanner: android.bluetooth.le.BluetoothLeScanner?,
    context: Context,
    settingsManager: SettingsManager
) {
    // --- Permissions Handling (Initialize Early) ---
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.VIBRATE
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.VIBRATE
        )
    }

    // --- State Management ---
    var scannedDevices by remember { mutableStateOf(mapOf<String, ScannedDevice>()) }
    val addressToSlotMap = remember { mutableStateMapOf<String, Int>() }
    var isScanning by remember { mutableStateOf(false) }
    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var currentScreen by remember { mutableStateOf(Screen.SCANNER) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var isTestMode by remember { mutableStateOf(false) }

    // Track screen dimmed state in Compose
    var isScreenDimmedState by remember { mutableStateOf(false) }

    // Track battery level in Compose
    var batteryLevel by remember { mutableStateOf((context as? MainActivity)?.getBatteryLevel() ?: 1.0f) }

    // Set up battery level callback
    DisposableEffect(Unit) {
        (context as? MainActivity)?.setBatteryLevelCallback { level ->
            batteryLevel = level
        }
        onDispose {
            (context as? MainActivity)?.setBatteryLevelCallback { }
        }
    }

    // --- Settings State (Initialized from SettingsManager) ---
    var vibrationDurationMs by remember { mutableStateOf(settingsManager.loadVibration()) }
    var isChimeOn by remember { mutableStateOf(settingsManager.loadChime()) }
    var chimeTone by remember { mutableStateOf(settingsManager.loadChimeTone()) }
    var usableSlots by remember { mutableStateOf(settingsManager.loadUsableSlots()) }
    var dimTimeoutMs by remember { mutableStateOf(settingsManager.loadDimTimeout()) }
    var dimLevel by remember { mutableStateOf(ScreenDimMode.fromString(settingsManager.loadDimLevel())) }
    val blockedDevices = remember { mutableStateOf(settingsManager.loadBlockedDevices()) }

    // Calculate actual divisions from usable slots (reserve ~9% for gear icon)
    val actualDivisions = remember(usableSlots) {
        kotlin.math.ceil(usableSlots * 1.09).toInt()
    }

    // Helper to get just the addresses for filtering
    val blockedAddresses = remember(blockedDevices.value) {
        blockedDevices.value.map { it.address }.toSet()
    }

    // --- Scroll States for Screens ---
    val settingsScrollState = rememberScalingLazyListState()
    val chimeSelectionScrollState = rememberScalingLazyListState()
    val divisionsSelectionScrollState = rememberScalingLazyListState()
    val reviewBlockedScrollState = rememberScalingLazyListState()

    // --- Animation Timer ---
    LaunchedEffect(isScanning, isTestMode) {
        if (isScanning || isTestMode) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(50L) // Update ~20 times per second for smooth animation
            }
        }
    }

    // --- Inactivity Timer for Screen Dimming ---
    LaunchedEffect(isScanning, isTestMode) {
        if (isScanning || isTestMode) {
            (context as? MainActivity)?.onUserInteraction() // Initialize timer
            while (true) {
                delay(1000L) // Check every second
                val mainActivity = context as? MainActivity
                if (mainActivity != null) {
                    val dimTimeout = mainActivity.getDimTimeout()
                    // Only dim if timeout is enabled (> 0)
                    if (dimTimeout > 0) {
                        val timeSinceInteraction = System.currentTimeMillis() - mainActivity.getLastUserInteractionTime()
                        if (timeSinceInteraction > dimTimeout) {
                            mainActivity.dimScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                        }
                    }
                }
            }
        }
    }

    // --- Haptic Feedback (Vibrator) ---
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // --- Audio Managers for different sound types ---
    // Use rememberUpdatedState to ensure we always have a fresh ToneGenerator reference
    val toneGeneratorRef = remember { mutableStateOf<ToneGenerator?>(null) }

    // Initialize ToneGenerator and clean it up properly
    DisposableEffect(Unit) {
        try {
            toneGeneratorRef.value = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            Log.d(TAG, "ToneGenerator created")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to create ToneGenerator: $e")
            toneGeneratorRef.value = null
        }

        onDispose {
            toneGeneratorRef.value?.release()
            toneGeneratorRef.value = null
            Log.d(TAG, "ToneGenerator released")
        }
    }

    val audioManager = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Load sound effects for system sounds
        am.loadSoundEffects()
        am
    }

    // --- Chime Queue using Channel ---
    val chimeQueue = remember { Channel<Int>(Channel.UNLIMITED) }
    val coroutineScope = rememberCoroutineScope()

    // Coroutine to process chime queue sequentially
    LaunchedEffect(Unit) {
        for (chimeId in chimeQueue) {
            // Play the chime based on type
            when {
                chimeId < 1000 -> {
                    // ToneGenerator sound - use the current ref value
                    val generator = toneGeneratorRef.value
                    if (generator != null) {
                        try {
                            generator.startTone(chimeId, 500)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error playing tone: $e")
                        }
                    }
                }
                chimeId in 1000..1999 -> {
                    // Ringtone sound - use MediaPlayer with proper audio attributes
                    val ringtoneType = when (chimeId) {
                        1000 -> RingtoneManager.TYPE_NOTIFICATION
                        1001 -> RingtoneManager.TYPE_ALARM
                        1002 -> RingtoneManager.TYPE_RINGTONE
                        else -> RingtoneManager.TYPE_NOTIFICATION
                    }
                    try {
                        val uri = RingtoneManager.getDefaultUri(ringtoneType)
                        if (uri != null) {
                            val mediaPlayer = android.media.MediaPlayer()
                            mediaPlayer.setDataSource(context, uri)

                            // Set audio attributes to match ToneGenerator (STREAM_MUSIC)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mediaPlayer.setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build()
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
                            }

                            mediaPlayer.setVolume(1.0f, 1.0f)
                            mediaPlayer.prepare()
                            mediaPlayer.start()

                            // Stop after 500ms and release
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    if (mediaPlayer.isPlaying) {
                                        mediaPlayer.stop()
                                    }
                                    mediaPlayer.release()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error stopping MediaPlayer: $e")
                                }
                            }, 500)
                        } else {
                            Log.w(TAG, "Ringtone URI is null for type: $ringtoneType")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing ringtone: $e")
                    }
                }
                chimeId >= 2000 -> {
                    // System sound
                    val effectId = chimeId - 2000
                    try {
                        audioManager.playSoundEffect(effectId, 1.0f)
                        Log.d(TAG, "Playing system sound effect: $effectId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing system sound: $e")
                    }
                }
            }
            // Wait for chime to finish before playing next one
            delay(550L) // 500ms chime + 50ms gap
        }
    }

    // Helper function to queue chimes
    val playChime: (Int) -> Unit = { chimeId ->
        coroutineScope.launch {
            chimeQueue.send(chimeId)
        }
    }

    // Clean up channel on dispose
    DisposableEffect(chimeQueue) {
        onDispose {
            chimeQueue.close()
        }
    }

    // --- Shared Device Discovery Logic ---
    val handleDeviceDiscovery: (String, String, Int) -> Unit = { deviceName, address, rssi ->
        // Skip blocked devices early
        if (address !in blockedAddresses) {
            val previousDeviceState = scannedDevices[address]

            val triggerDiscoveryFeedback = {
                val chimeId = if (isChimeOn) chimeTone else AppDefaults.CHIME_OFF_ID
                if (chimeId != AppDefaults.CHIME_OFF_ID) {
                    playChime(chimeId)
                }

                val vibrationDuration = vibrationDurationMs
                if (vibrationDuration > 0L) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                vibrationDuration,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(vibrationDuration)
                    }
                }
            }

            val excludedSlots = calculateExcludedSlots(actualDivisions, usableSlots)
            var shouldAddDevice = previousDeviceState != null
            var targetSlot: Int? = addressToSlotMap[address]

            if (previousDeviceState == null) {
                shouldAddDevice = true
                triggerDiscoveryFeedback()

                if (addressToSlotMap.size < usableSlots) {
                    val preferredSlot = abs(address.hashCode()) % actualDivisions
                    var candidateSlot = preferredSlot

                    while ((addressToSlotMap.containsValue(candidateSlot) || candidateSlot in excludedSlots)) {
                        candidateSlot = (candidateSlot + 151) % actualDivisions
                        assert(candidateSlot != preferredSlot) {
                            "Looped back to start, no slot available, usableSlots = $usableSlots, addressToSlotMap.size = ${addressToSlotMap.size}"
                        }
                    }

                    targetSlot = candidateSlot
                } else {
                    val displayedDevices = scannedDevices
                        .filter { it.key in addressToSlotMap }
                        .map { (addr, device) -> addr to device }

                    val deviceToReplace = displayedDevices.minByOrNull { (_, device) -> device.lastSeen }
                    val freedSlot = deviceToReplace?.first?.let { addressToSlotMap[it] }

                    if (deviceToReplace != null && freedSlot != null) {
                        addressToSlotMap.remove(deviceToReplace.first)
                        scannedDevices = scannedDevices - deviceToReplace.first
                        targetSlot = freedSlot
                    } else {
                        shouldAddDevice = false
                    }
                }
            } else if (previousDeviceState.rssi == -120) {
                triggerDiscoveryFeedback()
            }

            if (shouldAddDevice) {
                targetSlot?.let { addressToSlotMap[address] = it }
                scannedDevices = scannedDevices + (address to ScannedDevice(deviceName, address, rssi))
            }
        }
    }

    // --- Test Mode Device Generator ---
    LaunchedEffect(isTestMode, usableSlots, blockedAddresses) {
        if (isTestMode) {
            // Adjust speed based on number of usable slots
            // More slots = faster device generation to fill them
            val delayMs = when {
                usableSlots >= 30 -> 500L
                usableSlots >= 20 -> 750L
                else -> 1000L
            }

            while (true) {
                delay(delayMs)
                val randomAddress = String.format(
                    "%02X:%02X:%02X:%02X:%02X:%02X",
                    kotlin.random.Random.nextInt(256),
                    kotlin.random.Random.nextInt(256),
                    kotlin.random.Random.nextInt(256),
                    kotlin.random.Random.nextInt(256),
                    kotlin.random.Random.nextInt(256),
                    kotlin.random.Random.nextInt(256)
                )

                // Skip if address already exists or is blocked
                if (randomAddress !in scannedDevices && randomAddress !in blockedAddresses) {
                    val randomRssi = kotlin.random.Random.nextInt(-90, -40)
                    handleDeviceDiscovery("${context.getString(R.string.test_prefix)}${randomAddress.takeLast(5)}", randomAddress, randomRssi)
                }
            }
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }


    // --- BLE Scan Callback ---
    val scanCallback = remember(blockedAddresses) {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    // Filter out blocked devices at the source
                    if (device.address in blockedAddresses) return

                    val deviceName = try { device.name ?: context.getString(R.string.unknown) } catch(e: SecurityException) { context.getString(R.string.no_perm) }
                    handleDeviceDiscovery(deviceName, device.address, result.rssi)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan Failed with error code: $errorCode")
                isScanning = false
            }
        }
    }

    // --- Restart scan when blockedAddresses changes during active scan ---
    DisposableEffect(isScanning, scanCallback) {
        if (isScanning && bleScanner != null) {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner.startScan(null, scanSettings, scanCallback)
            Log.d(TAG, "Scan started/restarted with current blockedAddresses")
        }

        onDispose {
            if (bleScanner != null && isScanning) {
                try {
                    bleScanner.stopScan(scanCallback)
                    Log.d(TAG, "Scan callback stopped in dispose")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping scan in dispose: $e")
                }
            }
        }
    }

    // --- Device Timeout Logic ---
    LaunchedEffect(isScanning, scannedDevices) {
        if (isScanning) {
            while (true) {
                delay(AppDefaults.DEVICE_TIMEOUT_CHECK_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val timeoutMs = AppDefaults.DEVICE_STALE_TIMEOUT_MS
                val activeAddresses = scannedDevices.filterValues { now - it.lastSeen <= timeoutMs }.keys
                val timedOutDevices = scannedDevices.keys - activeAddresses

                if (timedOutDevices.isNotEmpty()) {
                    val updatedDevices = scannedDevices.toMutableMap()
                    timedOutDevices.forEach { address ->
                        updatedDevices[address]?.let {
                            if (it.rssi != -120) {
                                updatedDevices[address] = it.copy(rssi = -120)
                            }
                        }
                    }
                    scannedDevices = updatedDevices
                }
            }
        }
    }

    // --- Scan Control Functions ---
    val stopScan: () -> Unit = lambda@{
        isScanning = false
        (context as? MainActivity)?.stopOngoingActivity()

        return@lambda
    }

    val startScan: () -> Unit = lambda@{
        if (hasPermissions && bleScanner != null) {
            scannedDevices = mapOf()
            addressToSlotMap.clear()
            isScanning = true
            (context as? MainActivity)?.startOngoingActivity()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
        return@lambda
    }

    // Observe lifecycle to stop scanning on pause and reset dimmed state on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Stop scanning when app goes to background
                    if (isScanning) {
                        stopScan()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Ensure screen is not dimmed when app resumes
                    // Always reset the Compose state to ensure UI is synchronized
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed ->
                        isScreenDimmedState = isDimmed
                    }
                    // Force Compose state to false in case MainActivity state was already reset
                    isScreenDimmedState = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- Screen Navigation ---
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.SCANNER -> {
                BackHandler(enabled = isScreenDimmedState) {
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                }
                BackHandler(enabled = selectedAddress != null && !isScreenDimmedState) {
                    selectedAddress = null
                }
                BackHandler(enabled = isTestMode && !isScreenDimmedState) {
                    isTestMode = false
                    scannedDevices = mapOf()
                    addressToSlotMap.clear()
                    selectedAddress = null
                    currentScreen = Screen.SETTINGS
                }

                val excludedSlots = calculateExcludedSlots(actualDivisions, usableSlots)

                ScannerScreen(
                    isScanning = isScanning,
                    hasPermissions = hasPermissions,
                    scannedDevices = scannedDevices.filterKeys { it !in blockedAddresses },
                    slotMap = addressToSlotMap,
                    numDivisions = actualDivisions,
                    currentTime = currentTime,
                    onStartScan = startScan,
                    onStopScan = stopScan,
                    onPermissionsRequest = { permissionLauncher.launch(requiredPermissions) },
                    onSettingsClick = {
                        stopScan()
                        isTestMode = false
                        scannedDevices = mapOf()
                        addressToSlotMap.clear()
                        selectedAddress = null
                        currentScreen = Screen.SETTINGS
                    },
                    onSliceTapped = { address ->
                        context.runIfInteractionAllowed({ isScreenDimmedState = it }) {
                            selectedAddress = address
                        }
                    },
                    onDismissOverlay = {
                        context.runIfInteractionAllowed({ isScreenDimmedState = it }) {
                            selectedAddress = null
                        }
                    },
                    selectedDevice = selectedAddress?.let { scannedDevices[it] },
                    onBlockDevice = { address ->
                        val deviceName = scannedDevices[address]?.deviceName
                        val newBlocked = blockedDevices.value.toMutableSet().apply {
                            add(BlockedDevice(address, deviceName))
                        }
                        blockedDevices.value = newBlocked
                        settingsManager.saveBlockedDevices(newBlocked)
                        addressToSlotMap.remove(address)
                        scannedDevices = scannedDevices - address
                        selectedAddress = null
                    },
                    excludedSlots = excludedSlots,
                    isTestMode = isTestMode,
                    onTestModeEnd = {
                        isTestMode = false
                        scannedDevices = mapOf()
                        addressToSlotMap.clear()
                        selectedAddress = null
                        currentScreen = Screen.SETTINGS
                    },
                    context = context,
                    batteryLevel = batteryLevel
                )
            }

            Screen.SETTINGS -> {
                BackHandler(enabled = isScreenDimmedState) {
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                }
                BackHandler(enabled = !isScreenDimmedState) {
                    currentScreen = Screen.SCANNER
                }

                SettingsScreen(
                    vibrationDurationMs = vibrationDurationMs,
                    onVibrationChange = {
                        vibrationDurationMs = it
                        settingsManager.saveVibration(it)
                    },
                    isChimeOn = isChimeOn,
                    onChimeChange = {
                        isChimeOn = it
                        settingsManager.saveChime(it)
                    },
                    usableSlots = usableSlots,
                    onSelectDivisions = { currentScreen = Screen.DIVISIONS_SELECTION },
                    dimTimeoutMs = dimTimeoutMs,
                    onDimTimeoutChange = {
                        dimTimeoutMs = it
                        settingsManager.saveDimTimeout(it)
                        (context as? MainActivity)?.setDimTimeout(it)
                    },
                    dimLevel = dimLevel,
                    onDimLevelChange = {
                        dimLevel = it
                        settingsManager.saveDimLevel(it.toStorageString())
                        (context as? MainActivity)?.setDimLevel(it)
                    },
                    onBackClick = { currentScreen = Screen.SCANNER },
                    onReviewBlocked = { currentScreen = Screen.REVIEW_BLOCKED },
                    onTestClick = {
                        scannedDevices = mapOf()
                        addressToSlotMap.clear()
                        selectedAddress = null
                        isTestMode = true
                        currentScreen = Screen.SCANNER
                    },
                    onSelectChime = { currentScreen = Screen.CHIME_SELECTION },
                    scrollState = settingsScrollState
                )
            }

            Screen.CHIME_SELECTION -> {
                BackHandler(enabled = isScreenDimmedState) {
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                }
                BackHandler(enabled = !isScreenDimmedState) {
                    currentScreen = Screen.SETTINGS
                }

                ChimeSelectionScreen(
                    selectedTone = if (isChimeOn) chimeTone else AppDefaults.CHIME_OFF_ID,
                    onToneSelected = { tone ->
                        chimeTone = tone
                        settingsManager.saveChimeTone(tone)
                        if (tone != AppDefaults.CHIME_OFF_ID) {
                            isChimeOn = true
                            settingsManager.saveChime(true)
                        } else {
                            isChimeOn = false
                            settingsManager.saveChime(false)
                        }
                    },
                    onBackClick = { currentScreen = Screen.SETTINGS },
                    onPlayTone = playChime,
                    scrollState = chimeSelectionScrollState,
                    isChimeEnabled = isChimeOn,
                    onChimeEnabledChange = { enabled ->
                        isChimeOn = enabled
                        settingsManager.saveChime(enabled)
                    }
                )
            }

            Screen.DIVISIONS_SELECTION -> {
                BackHandler(enabled = isScreenDimmedState) {
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                }
                BackHandler(enabled = !isScreenDimmedState) {
                    currentScreen = Screen.SETTINGS
                }

                DivisionsSelectionScreen(
                    selectedUsableSlots = usableSlots,
                    onSlotsSelected = { slots ->
                        usableSlots = slots
                        settingsManager.saveUsableSlots(slots)
                    },
                    onBackClick = { currentScreen = Screen.SETTINGS },
                    scrollState = divisionsSelectionScrollState
                )
            }

            Screen.REVIEW_BLOCKED -> {
                BackHandler(enabled = isScreenDimmedState) {
                    (context as? MainActivity)?.brightenScreenPublic { isDimmed -> isScreenDimmedState = isDimmed }
                }

                ReviewBlockedScreen(
                    blockedDevices = blockedDevices.value,
                    onSaveAndUnblock = { devicesToUnblock ->
                        val newBlocked = blockedDevices.value.toMutableSet().apply {
                            removeAll(devicesToUnblock)
                        }
                        blockedDevices.value = newBlocked
                        settingsManager.saveBlockedDevices(newBlocked)
                    },
                    onBackClick = { currentScreen = Screen.SETTINGS },
                    scrollState = reviewBlockedScrollState
                )
            }
        }
    }

    // Overlay when screen is dimmed - intercepts taps to wake screen
    // Black mode: black overlay, Dark/Dim modes: transparent overlay
    if (isScreenDimmedState) {
        val dimLevel = (context as? MainActivity)?.getDimLevel()
        val overlayColor = if (dimLevel?.showOverlay == true) Color.Black else Color.Transparent

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor)
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Tap detected - brighten screen
                        (context as? MainActivity)?.onUserInteraction()
                        isScreenDimmedState = false
                    }
                }
        )
    }
}
