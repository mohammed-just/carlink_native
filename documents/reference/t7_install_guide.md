# T7 APK Install Guide

## Purpose

This guide explains how to install and validate the Android 9 / Allwinner T7 build of Carlink.

Use this guide with the `t7Api28` flavor.

## Before You Start

- Target device:
  - Allwinner T7 / S311 style Android 9 automotive head unit
  - API 28
- Recommended APK:
  - `app/build/outputs/apk/t7Api28/debug/app-t7Api28-debug.apk`
  - or the matching `t7Api28Release` APK if you build a release variant
- The normal T7 APK is intended for baseline projection only:
  - USB
  - video
  - audio
  - microphone
  - GPS forwarding
  - settings
  - logs
- Cluster support is not expected in the normal T7 APK.

## Build The APK

### Android Studio

1. Open the project in Android Studio.
2. Select the `t7Api28Debug` or `t7Api28Release` build variant.
3. Build the APK.

### Windows CLI

1. Open PowerShell in the repo root.
2. Run:

```powershell
.\gradlew.bat :app:assembleT7Api28Debug
```

3. Find the APK at:

```text
app/build/outputs/apk/t7Api28/debug/app-t7Api28-debug.apk
```

## Install Methods

### Method 1: File Manager Install On The Head Unit

Use this if the head unit can browse local storage or USB storage.

1. Copy the APK to a USB drive or local storage the head unit can read.
2. Open the file manager on the head unit.
3. Browse to the APK.
4. Install it.
5. If Android blocks unknown apps, allow installs from that source and retry.

### Method 2: ADB Install

Use this if ADB is available on the head unit.

1. Connect ADB.
2. Verify the device:

```powershell
adb devices
```

3. Install or replace the APK:

```powershell
adb install -r app\build\outputs\apk\t7Api28\debug\app-t7Api28-debug.apk
```

4. If install fails because an older incompatible build is present, uninstall first:

```powershell
adb uninstall zeno.carlink
adb install app\build\outputs\apk\t7Api28\debug\app-t7Api28-debug.apk
```

### Method 3: Experimental OEM / System Deployment

Use this only if you already know your T7 unit supports OEM or system app workarounds.

- Do not start here.
- First verify the normal APK is stable.
- Keep this path separate from the normal install track.
- Treat this as experimental, especially for cluster-related work.

## First Launch Checklist

After installation:

1. Launch Carlink.
2. Grant microphone permission.
3. Grant location permission if prompted.
4. Connect the Carlinkit adapter.
5. Approve the USB permission dialog.
6. Wait for projection to start.

Expected T7 behavior:

- The app should open without an immediate crash.
- Cluster controls may appear unavailable or experimental.
- Projection should still work even if cluster is unavailable.

## What To Test Right Away

1. App launches cleanly.
2. USB permission sticks.
3. CarPlay or Android Auto session starts.
4. Video renders at the correct size.
5. Audio plays without constant underruns.
6. Microphone works for calls or voice assistant.
7. Disconnect and reconnect works.
8. Settings persist after restart.
9. Log export works if the device has a compatible documents/files app.

## Settings Notes

- Display mode affects usable video size.
- Adapter Configuration changes require restart.
- On T7, cluster navigation should be treated as unavailable unless you have a confirmed privileged path.

## Troubleshooting

### App Will Not Install

- Make sure installs from unknown sources are allowed.
- If upgrading from a differently signed APK, uninstall the old one first.
- Check available storage space.

### App Opens Then Crashes

Watch for:

- `VerifyError`
- `NoSuchMethodError`
- class verification failures

These usually indicate an API-level compatibility problem.

### Adapter Connects But Projection Does Not Start

Check:

- USB permission prompt was accepted
- adapter cable and power are stable
- the adapter is supported
- logs for USB attach/detach loops

### Video Size Looks Wrong

Try:

- changing Display Mode
- reopening the app
- using the default AUTO video resolution first

### Audio Is Choppy

Check:

- the detected sample rate in logs
- whether the unit is under heavy load
- whether a lower-risk configuration is needed before experimenting further

## Log Collection

If the app is running but behavior is wrong:

1. Enable file logging in the app.
2. Reproduce the issue.
3. Export the log.

If ADB is available, also collect:

```powershell
adb logcat
```

## Rollback

If the T7 build is unstable:

1. Uninstall the test APK:

```powershell
adb uninstall zeno.carlink
```

2. Reinstall your last known-good build.
3. Leave cluster-related experiments disabled until baseline projection is stable.

## Related Files

- `documents/reference/t7_android9_port.md`
- `README.md`
- `app/build/outputs/apk/t7Api28/debug/app-t7Api28-debug.apk`
