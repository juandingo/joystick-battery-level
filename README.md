# Joystick Battery Level

Android application that displays the battery level of a connected Bluetooth device using Bluetooth Low Energy (BLE). The app connects to a specific device, reads the standard BLE Battery Service, and shows the battery percentage and firmware information in the interface.

## Features

* Connects to a Bluetooth Low Energy device
* Reads battery level using the standard BLE Battery Service (`0x180F`)
* Displays firmware version from Device Information Service (`0x180A`)
* Real-time battery updates using BLE notifications
* Pull-to-refresh connection
* Visual battery level indicator (color coded)
* Bluetooth state monitoring

## Technologies

* Kotlin
* Android SDK
* Bluetooth Low Energy (BLE)
* AndroidX libraries
* SwipeRefreshLayout

## BLE Services Used

| Service                      | UUID                                   | Purpose            |
| ---------------------------- | -------------------------------------- | ------------------ |
| Battery Service              | `0000180F-0000-1000-8000-00805f9b34fb` | Battery level      |
| Battery Level Characteristic | `00002A19-0000-1000-8000-00805f9b34fb` | Battery percentage |
| Device Information Service   | `0000180A-0000-1000-8000-00805f9b34fb` | Device metadata    |
| Firmware Revision            | `00002A26-0000-1000-8000-00805f9b34fb` | Firmware version   |

## Configuration

The application reads sensitive configuration values from `local.properties`, which is not committed to the repository.

Create a file named:

```
local.properties
```

in the project root.

Example:

```
DEVICE_MAC=XX:XX:XX:XX:XX:XX
DEVICE_NAME=My Device
```

An example configuration file is included:

```
local.properties.example
```

Copy it and update the values for your device.

## Setup

1. Clone the repository

```
git clone https://github.com/yourusername/joystick-battery-level.git
```

2. Create `local.properties` based on the example

3. Open the project in Android Studio

4. Sync Gradle

5. Run the app on a physical Android device with Bluetooth enabled

## Requirements

* Android device with BLE support
* Bluetooth enabled
* Android SDK

## Notes

The app connects to a predefined BLE device using its MAC address and reads the standard Battery Service exposed by the device.

## License

This project is provided for educational and personal use.
