# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BLEScanner2 is an Android Wear OS application that scans for Bluetooth Low Energy (BLE) devices and displays them on a circular dial interface. The app provides real-time proximity alerts via haptic and audio feedback when devices are detected.

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Install debug build on connected device
./gradlew installDebug

# Clean build
./gradlew clean

# Run lint checks
./gradlew lint
```

Note: please build after you make changes to verify there are no errors.

## Architecture

### Technology Stack
- **Platform**: Android Wear OS (minSdk 28, targetSdk 34)
- **Language**: Kotlin with Java 11 target
- **UI Framework**: Jetpack Compose for Wear OS
- **State Management**: Compose state with MutableState and remember

### Core Architecture Pattern

The app follows a **single-activity architecture** with composable screens:

1. **MainActivity.kt**: Entry point that hosts the entire Compose UI and manages BLE scanning lifecycle
   - Contains the BLE scanner initialization and scan callback logic
   - Manages all runtime permissions (Bluetooth, Location, Vibrate)
   - Handles device timeout logic (marks devices as out-of-range after 5 seconds)
   - Provides haptic and audio feedback through Vibrator and ToneGenerator
   - Navigation is handled via simple enum-based screen switching

2. **State Management Flow**:
   - `scannedDevices`: Map of MAC address → ScannedDevice data
   - `addressToSlotMap`: Maps device addresses to dial slice positions (persistent during scan)
   - `blockedAddresses`: Persisted user preferences via SettingsManager
   - Settings are loaded on app startup and saved immediately on change

3. **Screen Components** (in `presentation/screens/`):
   - `ScannerScreen`: Main dial view with start/stop controls
   - `SettingsScreen`: Vibration, chime, and dial divisions configuration
   - `ReviewBlockedScreen`: Manage blocked device list

4. **Key Components** (in `presentation/components/`):
   - `DeviceDial`: Circular Canvas-based dial that displays devices as colored arcs
     - Each device gets a "slot" (slice of the circle) based on address hash
     - Outer ring: device presence with time-based brightness fade
     - Inner ring: proximity visualization based on RSSI
   - `DeviceOverlay`: Modal popup when tapping a device on dial (block action)

### Important Data Flow

**Device Discovery & Slot Assignment**:
- When a new device is discovered, it's assigned a "preferred slot" via `abs(address.hashCode()) % numDivisions`
- If that slot is occupied, the app searches for the next available slot in a circular manner
- Slots are only assigned if there's room (< numDivisions active devices)
- Blocked devices are filtered out immediately in the scan callback

**Device Timeout & "Rediscovery"**:
- Devices not seen for 5 seconds have their RSSI set to -120 (out of range indicator)
- When a "timed out" device reappears, it triggers discovery feedback again
- The device keeps its original slot assignment throughout the scan session

**Feedback Triggers**:
- Discovery feedback (vibration + chime) occurs when:
  1. A brand new device is discovered
  2. A previously timed-out device comes back in range

### Data Layer

**SettingsManager** (`presentation/data/SettingsManager.kt`):
- Uses SharedPreferences for persistent storage
- All save operations use `.apply()` for async writes
- Settings: vibration duration, chime on/off, dial divisions, blocked addresses set

**ScannedDevice** (`presentation/data/ScannedDevice.kt`):
- Immutable data class with: deviceName, address, rssi, lastSeen timestamp

## Package Structure

```
com.rareventure.blescanner2
└── presentation/
    ├── MainActivity.kt          # Entry point, BLE logic, navigation
    ├── screens/                 # Composable screens
    │   ├── ScannerScreen.kt
    │   ├── SettingsScreen.kt
    │   └── ReviewBlockedScreen.kt
    ├── components/              # Reusable UI components
    │   ├── DeviceDial.kt        # Main dial visualization
    │   └── DeviceOverlay.kt     # Device detail popup
    ├── data/                    # Data layer
    │   ├── SettingsManager.kt
    │   └── ScannedDevice.kt
    ├── theme/
    │   └── Theme.kt
    └── utils/
        └── util.kt              # Contains mapRssiToValue for proximity calculation
```

## Key Implementation Details

### BLE Scanning
- Uses `ScanSettings.SCAN_MODE_LOW_LATENCY` for rapid device updates
- All BLE operations require `@SuppressLint("MissingPermission")` as permissions are checked at runtime
- Scan callback is memoized with `remember {}` to prevent recreation on recomposition

### Permission Handling
- Android 12+ requires: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION, VIBRATE
- Android 11 and below: BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, VIBRATE
- Permissions are requested via ActivityResultContracts.RequestMultiplePermissions

### Screen State Management
- Keep screen on is managed via `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` when scanning
- Animation timer runs at ~20fps (50ms delay) via LaunchedEffect when scanning is active

### Dial Interaction
- Tap detection: Converts tap coordinates to angle, determines which slice was tapped
- The pointerInput modifier is keyed to `slotMap.keys` to force recomposition when devices change

## Development Notes

- The app maintains a **single source of truth** in MainActivity's compose state
- All state updates flow down through composable parameters
- Callbacks flow up from child composables to MainActivity
- SharedPreferences operations are always async (`.apply()` not `.commit()`)
- Device filtering (blocked devices) happens at multiple layers for consistency
- Put all constants in AppDefaults.kt
- Product has not been released yet, no need to worry about migrating existing users.
