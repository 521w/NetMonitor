# NetMonitor

Android network security monitoring app for inspecting active connections, local VPN capture, and potential real-IP exposure.

This project is focused on practical mobile network visibility: what apps are connecting, where traffic is going, and whether traffic may bypass the expected route.

## What It Does

- Monitors active TCP/UDP connections from Android network tables
- Shows protocol, IP, port, state, and app ownership where available
- Provides local VPN-based packet capture without requiring root
- Records potential exposure events for later review
- Offers root-enhanced diagnostics when root is available
- Generates device/network diagnostic reports

## Features

| Feature | Description |
| --- | --- |
| Connection monitor | Reads active network connections and maps them to apps when possible |
| VPN packet capture | Uses Android `VpnService` to observe outbound/inbound traffic locally |
| Exposure detection | Flags suspicious direct connections and possible real-IP leaks |
| Root mode | Uses extra network commands when root permission is available |
| Filtering | Filters by protocol, state, app, and traffic type |
| Export | Stores exposure logs and exports diagnostic reports |

## Good For

- Android network visibility
- VPN leak investigation
- App traffic auditing
- Rooted-device network diagnostics
- Learning how Android network monitoring works

## Tech Stack

| Area | Tech |
| --- | --- |
| Language | Kotlin |
| Platform | Android 8.0+ |
| Architecture | MVVM, ViewModel, LiveData, Repository |
| Async | Kotlin Coroutines, SharedFlow |
| Capture | Android `VpnService`, `/proc/net/*`, optional root commands |
| Build | Gradle Kotlin DSL |

## Project Structure

```text
app/src/main/java/com/netmonitor/app/
├── model/                  # connection, packet, exposure, filter models
├── repository/             # network data layer
├── service/                # foreground monitor and VPN capture services
├── ui/                     # home, connections, packets, exposure, settings
├── util/                   # parsers, logging, root shell, event bus
└── viewmodel/              # monitor view model
```

## Build

```bash
git clone https://github.com/521w/NetMonitor.git
cd NetMonitor
./gradlew assembleDebug
```

Install the debug APK:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Verification

Build was checked in Termux, but this environment currently cannot run the Android build because Java and Android SDK are not installed:

```text
java: command not found
javac: command not found
ANDROID_HOME=unset
ANDROID_SDK_ROOT=unset
```

Use Android Studio, a configured Android SDK environment, or CI with Java + Android Gradle Plugin support for full APK verification.

## Permissions

The app requests network, VPN, foreground-service, notification, package-query, boot, storage, and optional root/system-level permissions. Some protected permissions only work on rooted, system, or debug environments.

## Notes

- VPN capture uses a local Android VPN tunnel and does not mean traffic is sent to an external VPN provider.
- Root mode is optional but improves visibility.
- Android versions and OEM restrictions may limit how much per-app network data can be collected.

## License

MIT
