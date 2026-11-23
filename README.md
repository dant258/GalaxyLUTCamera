# Galaxy LUT Camera — Alpha source

Target device: Samsung Galaxy S26 SM-S942B, One UI 9 / Android 17 (as reported by the user).

## Alpha features in this source
- Uses Android Camera2 to enumerate and open back-facing camera IDs exposed to third-party apps.
- Shows a live preview.
- Imports a 3D `.cube` LUT using Android's document picker.
- Captures JPEG and applies the imported LUT with trilinear interpolation before saving to `Pictures/GalaxyLUTCamera`.
- Cycles through camera IDs discovered by Camera2.

## Important limitations
- This is a standalone camera app. It does not inject code into Samsung Camera and is not a Good Lock module. Samsung does not provide a public API for third-party apps to add a custom LUT processor inside the stock Camera app.
- This alpha only exposes camera IDs returned by `CameraManager.cameraIdList`. It does not guarantee that the S26's three rear modules are all exposed independently. The phone must be tested with the app; logical/physical camera routing varies by Samsung firmware.
- Output is JPEG after LUT processing. No DNG/RAW pipeline, live LUT preview, video LUT, manual ISO/shutter/WB controls, or HEIF export in this alpha.
- The source has not yet been built or device-tested. It is not an already verified APK.

## Easiest APK build
See [README-INSTALL.md](README-INSTALL.md). A GitHub Actions workflow is included at `.github/workflows/build-apk.yml` to build a debug APK automatically.

## Local build
1. Open this folder in Android Studio.
2. Install Android SDK Platform 35 and JDK 17.
3. Sync Gradle and build `app`.
4. Install the debug APK on the SM-S942B.
5. Grant camera permission, tap IMPORT LUT, choose a `.cube` file, then tap the shutter.
6. Tap LENS to cycle among discovered back camera IDs. Record the status line for each ID to verify which physical camera is active.

## LUT format
Supports 3D `.cube` files with `LUT_3D_SIZE` between 2 and 65, `DOMAIN_MIN`, and `DOMAIN_MAX`. 1D LUTs are not supported.
