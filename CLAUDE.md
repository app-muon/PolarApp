# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all checks
./gradlew build

# Install on connected device
./gradlew installDebug
```

There are no automated tests in this project.

## Architecture

Single-activity Android app (`MainActivity.kt`) that connects to Polar sports watches via BLE and displays real-time heart rate data.

**Key components:**

- **`MainActivity.kt`** — All app logic lives here. Manages the Polar BLE API, permissions, device scanning/connection, HR data aggregation, and UI updates.
- **`ui/PulseGraphView.kt`** — Custom `View` that renders a HR-over-time polyline graph using `Canvas`. Data is fed via `setData(List<Int>)` where each element is a 5-second HR bin.

**Data flow:**

1. User taps "Scan" → `api.startListenForPolarHrBroadcasts(null)` finds first Polar device by listening for BLE broadcasts
2. User taps "Connect" → `api.connectToDevice(id)` + subscribes to HR broadcasts filtered by device ID
3. Each HR reading → `onHeartRate(hr)` → updates stats (current/max/min), appends to `hrBins` list, calls `pulseGraph.setData(hrBins)`
4. `PulseGraphView.onDraw()` renders the bins as a polyline with time (minutes) on X-axis and BPM (60–180) on Y-axis

**BLE / SDK:** Uses `polar-ble-sdk:6.7.0` (via JitPack). The SDK features initialized are `FEATURE_HR` and `FEATURE_DEVICE_INFO`. All async operations use RxJava3 with a `CompositeDisposable` cleared on disconnect and in `onDestroy`.

**Session state** (`hrBins`, `currentHr`, `maxHr`, `minHr`, `sessionStartMs`) resets on first HR received after connect and on manual reset.

## Key Details

- **Min SDK 26** (Android 8), **Target SDK 36** (Android 15)
- Bluetooth permissions are requested at runtime: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on Android 12+, `ACCESS_FINE_LOCATION` on older
- Screen is kept on via `FLAG_KEEP_SCREEN_ON` while the activity is open
- All strings are externalized in `res/values/strings.xml` — add new UI text there
- Theme: Material3 with navy primary (`#001F3F`), defined in `res/values/themes.xml` and `colors.xml`
- ProGuard is enabled for release builds; if adding RxJava or Polar SDK reflection-dependent features, add rules to `proguard-rules.pro`
