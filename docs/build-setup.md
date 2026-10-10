# Android build setup

## Requirements

- JDK 17
- Android SDK Platform 35 and Android Build Tools 35.0.0
- A connected physical Android device with USB debugging enabled for instrumented tests; emulator verification is not used

Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and application dependencies are pinned in the checked-in wrapper and `gradle/libs.versions.toml`. The wrapper downloads and verifies the Gradle distribution using its configured SHA-256 checksum.

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the Android SDK location and accept the SDK licenses. On Windows, run commands from PowerShell using `.gradlew.bat`; on Linux or macOS, use `./gradlew`.

## Verification

From the repository root, run the foundation gates:

```shell
./gradlew spotlessCheck lint testDebugUnitTest assembleDebug
```

For instrumented startup and route coverage, connect the physical Android device with USB debugging enabled, then run:

```shell
./gradlew connectedDebugAndroidTest
```

The Windows equivalent is:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

These commands build and install the debug test APK and run `HomeStartupTest` on the connected device. Record the device model and Android API level with the result.

## WSL on Windows

Do not run Gradle with the working directory exposed as a Windows UNC path such as `\\wsl.localhost\Ubuntu\...`; Gradle/Java on Windows may reject that path as the current directory. Use one of these supported arrangements:

- Run Gradle inside WSL with a Linux JDK 17 and Android SDK installed in the Linux environment, from the Linux filesystem; or
- Run the Windows Gradle wrapper from a Windows-local checkout/path (for example, under `%USERPROFILE%\source`) with a Windows JDK 17 and Android SDK.

Keep builds on a single filesystem and toolchain. A temporary Windows-local copy can be used when the project itself is stored in the WSL filesystem and Gradle is being run by Windows Java.

## Setup and Settings display checks

After the full connected suite passes, run the focused setup journeys with large/bold text, high contrast, and reduced motion on the physical device:

```powershell
.\scripts\verify-setup-display.ps1 -Serial <physical-device-serial>
```

The script snapshots and restores the device display settings and screen timeout in a finally block. It runs SetupSettingsJourneyTest with font scale 2.0, weight adjustment 300, high text contrast enabled, and all three system animation scales at zero, then collects screenshots for visual inspection. Keep the device unlocked while testing. Results and the SPEC-009 acceptance mapping are recorded in [setup/settings proof](proofs/setup-settings.md).
