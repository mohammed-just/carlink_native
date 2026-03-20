# Android 9 T7 Port Notes

## Target Snapshot

- Extracted target image reports `ro.build.version.sdk=28` and `ro.build.version.release=9`.
- Hardware identifiers point to the Allwinner T7 / S311 family:
  - `ro.product.manufacturer=Allwinner`
  - `ro.product.model=QUAD-CORE T7 p1`
- The image exposes automotive pieces such as `android.hardware.type.automotive.xml`.
- The stock image includes `system/app/EryaVLink/EryaVLink.apk`, which is useful as a lifecycle and behavior reference only.

## Build Flavors

- `modernAaos`
  - Keeps the current Android 12+ flow.
  - Includes `CarAppActivity`, Templates Host metadata, and the live cluster service/session classes.
- `t7Api28`
  - Targets Android 9 / API 28.
  - Uses the compatibility display sizing path.
  - Disables the modern Templates Host flow in the normal APK.
  - Treats legacy cluster support as capability-probed and experimental.

## Build And Install

- Windows / Android Studio:
  - Open the repo in Android Studio.
  - Select the `t7Api28Debug` or `t7Api28Release` variant.
- Windows / CLI:
  - `.\gradlew.bat :app:assembleT7Api28Debug`
  - `.\gradlew.bat :app:assembleModernAaosDebug`
- Normal T7 install track:
  - Install the regular `t7Api28` APK as a user app.
  - Expect baseline projection only: USB, video, audio, mic, GPS forwarding, logs, settings.
- Experimental privileged track:
  - Only for the extracted T7 head unit where OEM-style deployment is possible.
  - Use a system or OEM app path only after baseline projection is stable.
  - Keep privileged cluster experiments isolated from the normal APK rollout.

For step-by-step deployment instructions, use `documents/reference/t7_install_guide.md`.

## Runtime Differences On T7

- Display sizing uses a compatibility provider:
  - API 30+ keeps `WindowMetrics`.
  - API 28 falls back to real display metrics and legacy inset handling.
- Decoder selection avoids API 29-only `MediaCodecInfo.isHardwareAccelerated()` on Android 9.
- PendingIntent flags are API-aware so Android 9 never depends on Android 12+ mutability behavior.
- T7 audio uses the conservative profile:
  - Larger buffers
  - No low-latency performance mode
  - Runtime sample-rate probing
- Cluster controls stay unavailable unless a compatible host path is detected.

## Cluster Expectations

- Normal `t7Api28` builds do not depend on `androidx.car.app.activity.CarAppActivity`.
- If only legacy `android.car` exists without grants, cluster remains a clean no-op.
- If privileged legacy permissions are present, the app reports that path as experimental rather than enabling it automatically.
- `modernAaos` remains the only live Templates Host cluster path in this repo.

## Bring-Up Checklist

1. Install and launch the `t7Api28` build.
2. Confirm there is no startup verifier crash.
3. Plug the adapter in and grant USB permission.
4. Verify session start, video render, audio playback, microphone capture, disconnect, and reconnect.
5. Check display sizing:
   - Native bounds
   - Safe-area math
   - Chosen adapter resolution
6. Check audio behavior:
   - Detected sample rate
   - Buffer stability
   - No underrun storms during a 30-minute run
7. Verify settings persistence, file-log export, and GPS forwarding.

## Failure Signatures To Watch

- `NoSuchMethodError`
- `VerifyError`
- MediaCodec init failures or repeated decoder resets
- USB permission not sticking after reconnect
- Audio underrun bursts or repeated track recreation
- Car or cluster permission denials from `android.car`
- Any attempt to launch modern Templates Host components on Android 9

## Rollback

- Reinstall the last known-good `modernAaos` build for the GM path.
- On T7, disable or uninstall the test APK before switching to privileged experiments.
- If cluster-related testing destabilizes the head unit, return to the normal `t7Api28` APK with cluster left disabled.
