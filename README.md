# BlueDog

A Wear OS application that scans for Bluetooth Low Energy (BLE) devices and displays them on an interactive circular dial interface with real-time proximity alerts.

## Features

- **Circular Dial Display**: Visual representation of nearby BLE devices arranged in a color-coded circular layout
- **Real-time Scanning**: Continuous BLE device discovery with low-latency updates
- **Proximity Visualization**:
  - Outer ring shows device presence with time-based brightness fade
  - Inner ring indicates proximity based on signal strength (RSSI)
- **Haptic & Audio Feedback**: Configurable vibration and chime alerts when devices are discovered
- **Device Management**: Block unwanted devices from appearing on the dial
- **Battery Monitoring**: Circular battery indicator around the center button
- **Configurable Settings**:
  - Adjustable vibration duration
  - Toggle audio chimes
  - Customize dial divisions (4-24 slots)

## Requirements

- Wear OS device (API level 28+)
- Bluetooth Low Energy support
- Android SDK 34

## Build Instructions

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

## Permissions

The app requires the following permissions:
- **Bluetooth Scan/Connect** (Android 12+)
- **Location Access** (required for BLE scanning)
- **Vibrate** (for haptic feedback)

## How It Works

### Dial Interface

Each detected BLE device is assigned a "slot" (slice) on the circular dial:
- **Outer Ring**: Shows device presence with brightness that fades over 5 seconds
- **Inner Ring**: Displays proximity based on signal strength (closer = brighter)
- **Color**: Each device gets a unique color based on its MAC address

### Device Discovery

- When a device is first discovered, it appears at maximum brightness
- The outer ring brightness gradually fades to 30% over 5 seconds
- When a device is seen again, brightness resets to maximum
- Devices not seen for 5 seconds are marked as out of range (RSSI = -120)

### Device Management

Tap any device on the dial to:
- View device details (name, MAC address, signal strength)
- Block the device from future scans

Blocked devices can be reviewed and unblocked in the Settings screen.

## Architecture

- **Platform**: Wear OS with Jetpack Compose
- **Language**: Kotlin
- **UI Framework**: Compose for Wear OS
- **Pattern**: Single-activity architecture with composable screens

### Key Components

- `MainActivity.kt`: BLE scanning lifecycle and permissions management
- `ScannerScreen.kt`: Main dial view with scan controls
- `DeviceDial.kt`: Canvas-based circular dial visualization
- `SettingsScreen.kt`: User preferences configuration
- `SettingsManager.kt`: Persistent settings storage

## Project Structure

```
com.rareventure.bluedog
└── presentation/
    ├── MainActivity.kt
    ├── screens/
    │   ├── ScannerScreen.kt
    │   ├── SettingsScreen.kt
    │   └── ReviewBlockedScreen.kt
    ├── components/
    │   ├── DeviceDial.kt
    │   └── DeviceOverlay.kt
    ├── data/
    │   ├── SettingsManager.kt
    │   └── ScannedDevice.kt
    ├── theme/
    │   └── Theme.kt
    └── utils/
        ├── AppDefaults.kt
        └── util.kt
```
