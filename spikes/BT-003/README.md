# BT-003 disposable Android proof spike

Package: `dev.bubblespend.bt003proof` (unique proof package). Target SDK 35, Compose UI, fixed synthetic values. Nothing in this folder is production app code. The spike has no network access or telemetry and logs only UI/service state transitions.

## Build on the configured Windows machine

Use Android Studio's bundled JDK (17) and the SDK under `%LOCALAPPDATA%\Android\Sdk`. Open PowerShell in this directory and run:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Set-Location "$PWD"
gradle --version
gradle --no-daemon :app:assembleDebug
```

The build requires Gradle 8.9 (compatible with AGP 8.7.3), SDK platform 35, and Build Tools 35.0.0. Android Studio can supply Gradle 8.9 via its Gradle distribution settings; no wrapper is checked in because this workspace has no Gradle installation in its Linux shell. The output is `app\build\outputs\apk\debug\app-debug.apk`.

Install on the dedicated emulator only after reviewing the artifact/package:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 install app\build\outputs\apk\debug\app-debug.apk
```

The overlay is opt-in from the app. Android's special access screen is opened only after the explanation and explicit enable action. Use the visible disable control to remove the spike overlay. No foreground service is used: a normal service owns the window for this foreground-triggered proof, returns `START_NOT_STICKY`, and does not attempt automatic restart.

## Emulator scope and limits

The spike supports manual checks for permission denial/grant/revocation, in-app availability, TYPE_APPLICATION_OVERLAY, collapsed/expanded synthetic entry, close without save, one synthetic save, drag/clamp/edge snap, launching the app, screen-off removal, rotation/display re-layout, and force-stop/relaunch. It does not claim these scenarios have been run. Process death is not automatically recovered; D-01 remains open. Screen-off removal avoids showing entry contents while locked; this is only the spike's observed design choice and does not resolve D-06.

The analytics screen contains fixed synthetic category shares, zero-filled daily trend bars, comparative bars, an ordered exact-value list, semantics labels, and matching-entry drill-down. Manually verify TalkBack, touch, text scaling, contrast, and empty/zero states on the emulator. There is no real-device or OEM evidence in this spike, no persistent expense model, and no chart library selection.
