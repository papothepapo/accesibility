# Exalt Accessibility Switcher

A small Android 6+ app that watches the foreground package with Usage Access and switches to the first enabled matching accessibility service rule.

The app does not declare an accessibility service of its own, so it does not consume the single service slot. When no enabled rule matches the current foreground package, it keeps the current service instead of forcing a fallback.

## Build

For a normal checkout, use Android Studio or run the included Gradle wrapper with JDK 17 and an Android SDK installed:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This particular device also has a portable local toolchain under `.toolchain/`, which is intentionally not committed. To rebuild from the current workspace in PowerShell:

```powershell
$env:JAVA_HOME='C:\Users\yitzp\Downloads\codex\.toolchain\jdk\jdk-17.0.18+8'
$env:JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStore=C:\Users\yitzp\Downloads\codex\.toolchain\truststore\windows-root.jks -Djavax.net.ssl.trustStorePassword=changeit'
$env:ANDROID_HOME='C:\Users\yitzp\Downloads\codex\.toolchain\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:ANDROID_USER_HOME='C:\Users\yitzp\Downloads\codex\.android-home'
$env:GRADLE_USER_HOME='C:\Users\yitzp\Downloads\codex\.gradle-home'
$env:PATH="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
.\.toolchain\gradle\gradle-8.7\bin\gradle.bat --no-daemon testDebugUnitTest assembleDebug
```

## Device Setup

Install the APK, then grant the secure-settings permission once:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.exalt.accessibilityswitcher android.permission.WRITE_SECURE_SETTINGS
```

Open the app, choose `Permissions`, and enable Usage Access for Exalt Accessibility Switcher. The app includes buttons for Usage Access, Accessibility settings, diagnostics, automation on/off, and hold-current-service mode.
