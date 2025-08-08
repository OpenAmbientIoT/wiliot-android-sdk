# Wiliot Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.wiliot/wiliot-bom.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.wiliot/wiliot-bom)

## Description

The **Wiliot Android SDK** is an official Android library project provided by Wiliot. It enables Android applications to act as **Wiliot Gateways**, bringing native support for interacting with Wiliot's Ambient IoT ecosystem.

---

## Table of Contents

- [Modules Overview](#modules-overview)
- [Integration Guide](#integration-guide)

---

## Modules Overview

The SDK is structured into several key modules, each addressing a specific part of Wiliot's integration:

### `wiliot-bom`

A Bill of Materials (BOM) module that provides centralized version management for all Wiliot SDK modules. While it contains no runtime logic, it ensures:

- Version compatibility across all SDK modules.
- Simplified dependency declarations — just specify the BOM version once, and omit versions for individual modules.
- Recommended for all integrations to avoid version mismatches.

### `wiliot-core`

The essential foundation of the Wiliot Android SDK. This module provides:

- All data models used across other SDK modules.
- Contracts and interfaces for connecting and coordinating other modules.
- Mandatory for all integrations using the Wiliot SDK.

### `wiliot-upstream`

The core processing pipeline of the Wiliot SDK. It listens for Bluetooth Low Energy (BLE) advertisements, filters and parses them, performs required computations, and pushes the processed data to the Wiliot Cloud via MQTT.

- Essential for real-time Pixel data ingestion and telemetry.
- Handles the full upstream flow from BLE scan to cloud delivery.

### `wiliot-downstream`

Enables cloud-managed control over the Mobile Gateway. It listens to cloud-to-device commands delivered via MQTT and performs appropriate actions:

- Applies configuration changes to the Mobile Gateway.
- Delegates broadcast actions (e.g., Bridge Reboot events) to the :wiliot-advertising module to emit Bluetooth packets.
- Optional, but required for cloud-driven Mobile Gateway orchestration.

### `wiliot-calibration`

Enables frequency calibration of Wiliot Pixels by broadcasting specialized Bluetooth packets. This helps Pixels fine-tune their transmission frequencies for better reliability and scanning accuracy.

- Optional, but recommended to improve Pixel performance.
- Works in conjunction with the upstream scanning pipeline.

### `wiliot-queue`

Handles all MQTT-based communication within the SDK. It serves as a shared transport layer used by both:

- :wiliot-upstream — for sending Pixel data to the cloud.
- :wiliot-downstream — for receiving cloud commands.

### `wiliot-edge`

Provides an abstraction layer for executing long-running or resource-intensive jobs on the Mobile Gateway (Edge). Typical use cases include:

- Performing OTA firmware upgrades for Wiliot Bridges.
- Handling command execution initiated by the :wiliot-downstream module.
- Optional, but required for cloud-triggered OTA and similar operations.

### `wiliot-advertising`

Responsible for broadcasting BLE packets on demand. It doesn't initiate broadcasts on its own but acts as a utility module triggered by others (like :wiliot-downstream) to emit specific Bluetooth advertisements.

- Optional, but required for use cases involving custom BLE packet broadcasting (e.g., reboot events, bridge discovery).

### `wiliot-dfu`

Implements full Device Firmware Upgrade (DFU) logic for Wiliot Bridges. This includes:

- Downloading the appropriate firmware binaries.
- Delegating reboot and preparation commands to :wiliot-advertising.
- Executing the actual firmware flashing process over BLE.
- Optional, but required for managing OTA firmware updates on Bridges.

### `wiliot-resolve-data`

Responsible for resolving Pixel metadata using their BLE advertisements. It contains domain logic only, delegating cloud communication to the :wiliot-network-meta module.

- Enables decoding of raw Pixel identifiers into meaningful metadata.
- Optional, but essential for applications that require enriched Pixel information.

### `wiliot-resolve-edge`

Contains domain logic for resolving metadata about Edge devices (e.g., Bridges). It acts as a high-level abstraction layer built on top of :wiliot-network-edge and works alongside the :wiliot-upstream module.

- Optional, but necessary when edge-device metadata resolution is required.
- Delegates network calls to wiliot-network-edge.

### `wiliot-network-meta`

Implements communication with Wiliot Cloud APIs to retrieve metadata about Pixels, based on information extracted from BLE advertisements.

- Used internally by :wiliot-resolve-data to enrich BLE data.
- Optional, unless Pixel metadata resolution is required.

### `wiliot-network-edge`

Handles communication with Wiliot Cloud APIs related to Edge devices (e.g., Bridges). This module enables fetching metadata or configuration details for edges directly from the cloud.

- Optional: Required only if you're using :wiliot-resolve-edge.
- Used internally by wiliot-resolve-edge as its network layer.

### `wiliot-virtual-bridge`

wiliot-virtual-bridge
Partially simulates the internal datapath logic of a physical Wiliot Bridge. Specifically, it handles:

- Direct Pixel packets, applying pacing mechanisms.
- Enriches packets with additional metadata (e.g., nfpkt, alias bridge ID).
- Forwards processed packets back to the :wiliot-upstream module for cloud delivery.
- Optional, but useful for replicating Bridge datapath behavior within the mobile SDK.

---

## Integration Guide

### 1. Minimum Requirements

- Android API level: 29+ (Android 10 and higher)

- Required Gradle plugins:

    - `kotlin-android`

    - `kotlin-kapt`

### 2. Add Maven Central

Ensure `mavenCentral()` is included in your top-level Gradle file:

<details> <summary><strong>For <code>settings.gradle.kts</code></strong></summary>

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

</details>

<details> <summary><strong>For <code>settings.gradle</code></strong></summary>

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```
</details>

### 3. Add SDK Dependencies

Use the BOM to align all SDK module versions automatically.

💡 Replace `$latest_bom_version` with the actual version, which you can find:

- In the Maven Central badge at the top of this README
- Or directly at 👉 https://central.sonatype.com/artifact/com.wiliot/wiliot-bom

<details> <summary><strong>For <code>build.gradle.kts</code></strong></summary>

```kotlin
plugins {
    id("com.android.application") // or "com.android.library"
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 29
        targetSdk = 34
    }
}

dependencies {
    // BOM for centralized version management
    implementation(platform("com.wiliot:wiliot-bom:$latest_bom_version"))

    // Core modules
    implementation("com.wiliot:wiliot-core")
    implementation("com.wiliot:wiliot-queue")
    implementation("com.wiliot:wiliot-network-edge")
    implementation("com.wiliot:wiliot-upstream")
    implementation("com.wiliot:wiliot-downstream")
    implementation("com.wiliot:wiliot-edge")
    implementation("com.wiliot:wiliot-dfu")
    implementation("com.wiliot:wiliot-virtual-bridge")
    implementation("com.wiliot:wiliot-advertising")
}
```

</details>

<details> <summary><strong>For <code>build.gradle</code></strong></summary>

```groovy
plugins {
    id 'com.android.application' // or 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'
}

android {
    compileSdk 34

    defaultConfig {
        minSdk 29
        targetSdk 34
    }
}

dependencies {
    // BOM for centralized version management
    implementation platform("com.wiliot:wiliot-bom:$latest_bom_version")

    // Core modules
    implementation "com.wiliot:wiliot-core"
    implementation "com.wiliot:wiliot-queue"
    implementation "com.wiliot:wiliot-network-edge"
    implementation "com.wiliot:wiliot-upstream"
    implementation "com.wiliot:wiliot-downstream"
    implementation "com.wiliot:wiliot-edge"
    implementation "com.wiliot:wiliot-dfu"
    implementation "com.wiliot:wiliot-virtual-bridge"
    implementation "com.wiliot:wiliot-advertising"
}
```

</details>

### 4. Update `AndroidManifest.xml`

To operate properly, the Wiliot Android SDK requires a range of permissions to enable Bluetooth scanning, network access, location services, and foreground service execution.

Add the following entries to your `AndroidManifest.xml`:

#### 🔗 Network Permissions

```xml
<!-- Network Permissions -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

#### 📡 Bluetooth Permissions

```xml
<!-- Bluetooth Permissions -->
<uses-permission
    android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission
    android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

#### 📍 Location Permissions

```xml
<!-- Location Permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

#### 🛠️ Foreground Service Permissions

```xml
<!-- Foreground Service Permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

#### 🔔 Notifications Permission

```xml
<!-- Notifications Permission -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 4. Wiliot SDK Initialization

The Wiliot SDK must be initialized early in your app’s lifecycle — ideally in your `Application` class. This ensures proper configuration of MQTT, BLE scanning, virtual bridge logic, and other internal services.

#### ✅ Step-by-Step Setup

1. Implement `Wiliot.ContextInitializationProvider` in your custom `Application` class.

2. Configure the SDK using `WiliotAppConfigurationSource`.

3. Initialize the SDK with required and optional components.

4. Register context provider, location manager, API key, and lifecycle callbacks.

#### 🧩 Example

```kotlin
class App : Application(), Wiliot.ContextInitializationProvider {

    override fun onCreate() {
        super.onCreate()
        initWiliot()
    }

    private fun initWiliot() {
        // Step 1: Configure global SDK preferences
        WiliotAppConfigurationSource.initialize(
            object : WiliotAppConfigurationSource.DefaultSdkPreferenceSource() {
                override fun ownerId(): String = "wiliot" // 🔄 Replace with your real owner ID

                // Optional: enable Bluetooth packet counting
                override fun btPacketsCounterEnabled(): Boolean = true

                // Optional: automatically restart service if it crashes
                override fun isServicePhoenixEnabled(): Boolean = true
            }
        )

        // Step 2: Set your actual application package and main activity
        Wiliot.applicationPackage = "com.yourdomain.yourapp" // 🔄 Replace with your app's package
        Wiliot.launcherActivity = "com.yourdomain.yourapp.MainActivity" // 🔄 Replace with your main activity

        // Step 3: Initialize the Wiliot SDK
        Wiliot.init {
            // Provide context (from Application)
            this contextProviderBy this@App

            // Set your Wiliot API key (required)
            setApiKey("<YOUR_API_KEY>") // 🔐 Replace with your actual API key

            // Provide application info via framework delegate
            this frameworkDelegateBy object : FrameworkDelegate() {
                override fun applicationName(): String = "Wiliot SDK Sample App"

                override fun applicationVersion(): Int = getAppVersion().first
                override fun applicationVersionName(): String = getAppVersion().second
            }

            // Provide LocationManager implementation
            this locationManagerBy LocationManagerImpl

            // Optional: listen for initialization result
            this initializationCallbackBy object : Wiliot.InitializationCallback {
                override fun onInitializationFinished() {
                    Log.i("App", "Wiliot initialization finished")
                }
            }

            // Step 4: Initialize SDK modules (required for core functionality)
            initQueue()
            initEdge()
            initEdgeNetwork()
            initVirtualBridge()
            initUpstream()
            initDownstream()
        }
    }

    override fun provideContext(): Application = this

    private fun getAppVersion(): Pair<Int, String> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.versionCode to packageInfo.versionName
    }
}
```

#### 📝 Notes

- You must replace the placeholder values:

    - `ownerId()`: Your Wiliot account identifier.

    - `applicationPackage` and `launcherActivity`: Your app’s package name and entry point activity.

    - `setApiKey("<YOUR_API_KEY>")`: Your real Wiliot API key.

    - `applicationName()`: A human-readable name for your app (for internal SDK diagnostics or cloud-side identification).

    - `applicationVersion()` and `applicationVersionName()`: Your app's version code and version name — both are required and used for identifying the client version in telemetry and diagnostics.

- If `isServicePhoenixEnabled()` is set to true, the SDK will automatically restart its background service if it's crashed by any reason.

- `LocationManagerImpl` should be your actual implementation that handles runtime location permission checks and GPS availability (you can customize it based on your app’s needs). Example: [LocationManagerImpl](https://github.com/OpenAmbientIoT/wiliot-android-sdk/blob/master/app/src/main/java/com/wiliot/wiliotandroidsdk/utils/LocationManagerImpl.kt)

### 5. Runtime Permissions

Many permissions required by the Wiliot SDK must be explicitly requested at runtime — especially those related to Bluetooth, location, and notifications.

Failing to request these permissions will result in limited or broken SDK functionality (e.g., BLE scanning won't start, cloud communication may fail, or required services won't launch).

#### 📋 Permissions Overview

Below is a breakdown of runtime-requested permissions and their purposes:

| Permission                   | Required on | Purpose                                                             |
| ---------------------------- | ----------- | ------------------------------------------------------------------- |
| `BLUETOOTH_SCAN`             | Android 12+ | Scan for BLE advertisements from Pixels                             |
| `BLUETOOTH_CONNECT`          | Android 12+ | Connect to BLE devices if needed                                    |
| `BLUETOOTH_ADVERTISE`        | Android 12+ | Broadcast BLE packets (e.g., bridge reboot, calibration)            |
| `ACCESS_FINE_LOCATION`       | Android 6+  | Required for BLE scanning to work                                   |
| `ACCESS_COARSE_LOCATION`     | Android 6+  | Alternative to fine location (less accurate, not always sufficient) |
| `ACCESS_BACKGROUND_LOCATION` | Android 10+ | Required if scanning should happen in background                    |
| `POST_NOTIFICATIONS`         | Android 13+ | Required to show foreground service or error notifications          |

#### ✅ Best Practices

- Always check permission status before initializing the Wiliot SDK.
- If any critical permissions are missing, delay SDK start and request them first.
- After permissions are granted, proceed with full SDK initialization.

#### 📎 Sample Code

An example implementation of a permission request helper can be found here:

👉 [Permission Helper Example (GitHub)](https://github.com/OpenAmbientIoT/wiliot-android-sdk/tree/master/app/src/main/java/com/wiliot/wiliotandroidsdk/permissions)

### 6. Run the Wiliot SDK

Once the SDK has been fully initialized and required permissions are granted, you can start and stop the Mobile Gateway Mode using simple lifecycle calls.

#### 🚀 Start Gateway Mode

This activates all initialized SDK modules (BLE scanning, MQTT communication, virtual bridge, etc.):

```kotlin
Wiliot.start()
```

Call this when you’re ready to begin collecting data and acting as a Wiliot Gateway (typically after app startup and permission grant).

#### ⏹️ Stop Gateway Mode

To gracefully shut down scanning, communication, and related services:

```
Wiliot.stop()
```

You may call this when the user logs out, disables Gateway Mode, or exits the app.

### 🌐 Optional: Using a Custom Environment

By default, the Wiliot SDK is configured to work with Wiliot’s built-in cloud environment (hosted on AWS). However, in some enterprise or on-prem deployments, Wiliot Cloud may be hosted in a customer-specific infrastructure.

In such cases, you must configure the SDK to target a custom environment before initialization.

#### 🧩 How to Define a Custom Environment

You should define your environment before any other SDK calls:

```kotlin
val environment = "CUSTOM_ENV"

Environments.addCustomEnvironment(
    EnvironmentBuilder.with(
        envName = environment,
        apiBaseUrl = "https://api.mydomain.cloud",          // Replace with your API base
        mqttUrl = "ssl://mqtt.mydomain.cloud:8883"          // Replace with your MQTT broker URL
    )
)
```

#### 🔧 Apply the Custom Environment in SDK Configuration

When initializing `WiliotAppConfigurationSource`, make sure to explicitly provide your environment:

```kotlin
WiliotAppConfigurationSource.initialize(
    object : WiliotAppConfigurationSource.WiliotSdkPreferenceSource {
        override fun environment(): EnvironmentWiliot =
            Environments.getForName(environment)

        // other configuration (ownerId, phoenix mode, etc.)
        override fun ownerId(): String = "your-owner-id"
    }
)
```

#### ⚠️ Important

You must register the custom environment before calling `WiliotAppConfigurationSource.initialize(...)`.
This ensures the SDK uses the correct API and MQTT endpoints during its setup and network binding phase.

#### 💡 Complete example

```kotlin
Wiliot.init {
    // Always define custom environment first!
    val environment = "CUSTOM_ENV"

    Environments.addCustomEnvironment(
        EnvironmentBuilder.with(
            envName = environment,
            apiBaseUrl = "https://api.mydomain.cloud",          // 🔄 Replace with your API base URL
            mqttUrl = "ssl://mqtt.mydomain.cloud:8883"          // 🔄 Replace with your MQTT broker URL
        )
    )

    // Configure the environment in SDK preferences
    WiliotAppConfigurationSource.initialize(
        object : WiliotAppConfigurationSource.WiliotSdkPreferenceSource {
            override fun environment(): EnvironmentWiliot =
                Environments.getForName(environment)

            override fun ownerId(): String = "your-owner-id"
        }
    )

    // Continue with other setup...
    contextProviderBy this@App
    setApiKey("<YOUR_API_KEY>")
    initQueue()
    initUpstream()
    // ...
}
```

