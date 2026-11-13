# AI Guardian — Native Android Application

The `android/` directory contains the native Android client for AI Guardian. It is developed independently from the Python/Streamlit research prototype so camera capture, on-device inference, accessibility audio, and device integrations can use Android-native APIs.

## 1. Current implementation

The current Android MVP provides:

- Kotlin application code with Jetpack Compose.
- CameraX back-camera preview.
- Runtime camera-permission handling.
- Start/stop camera controls.
- Native Android Text-to-Speech (TTS) test.
- Accessibility-oriented speech audio attributes.
- No camera-frame upload in this camera-only milestone.

**Not implemented yet:** on-device YOLO inference, detection overlays, object tracking, OCR, scene announcements, Wi-Fi/IP-camera input, and camera-glasses/vendor SDK adapters.

The existing Python/Streamlit application remains the research and model-experimentation environment. The Android client is the target runtime for the native mobile experience.

## 2. Build environment

The project uses the following verified toolchain:

| Component | Version / requirement |
| --- | --- |
| Operating system | Windows, Linux, or macOS |
| JDK | 17 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| Android SDK Platform | 36 |
| Android Build Tools | 36.0.0 |
| Minimum Android API | 26 |
| Compile / target API | 36 |

Android Studio is **not required**. The project can be built using the checked-in Gradle Wrapper and Android SDK command-line tools.

### Windows environment variables

Install JDK 17 and Android SDK command-line tools. Set the SDK location for the current Command Prompt session (adjust the path if your SDK is installed elsewhere):

```cmd
set "ANDROID_HOME=C:\Android\Sdk"
set "ANDROID_SDK_ROOT=C:\Android\Sdk"
```

Ensure the SDK contains these packages:

- `platform-tools`
- `platforms;android-36`
- `build-tools;36.0.0`

If needed, install them with `sdkmanager.bat` from your Android command-line tools installation:

```cmd
sdkmanager.bat "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

Make sure `java -version` reports JDK 17.

## 3. Build the debug APK

Run commands from the repository root:

```cmd
cd android
gradlew.bat :app:assembleDebug
```

If the SDK variables are not already set in the current terminal, set them before running the build:

```cmd
set "ANDROID_HOME=C:\Android\Sdk" && set "ANDROID_SDK_ROOT=C:\Android\Sdk" && gradlew.bat :app:assembleDebug
```

The generated APK is:

```text
android\app\build\outputs\apk\debug\app-debug.apk
```

The first build may take longer while Gradle downloads the required distribution and dependencies.

## 4. Install and launch on a physical Android phone

A physical phone is recommended because camera behavior and audio routing should be tested on the target device.

1. Enable Developer options and USB debugging on the phone.
2. Connect the phone by USB, unlock it, and accept the USB debugging authorization prompt.
3. Confirm that ADB detects the device:

   ```cmd
   adb devices
   ```

4. From the `android` directory, install the debug APK:

   ```cmd
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

5. Launch the app:

   ```cmd
   adb shell monkey -p org.aiguardian.mobile 1
   ```

6. Grant camera permission when prompted, then test Start camera and Stop camera.
7. Connect Bluetooth earphones using Android Settings, press the app's voice-test control, and verify the audio route.

Android manages Bluetooth pairing and system audio routing. The app does not silently pair with, select, or take over a headset.

## 5. Useful development commands

Run these from the `android` directory:

| Task | Command |
| --- | --- |
| Build debug APK | `gradlew.bat :app:assembleDebug` |
| Run unit tests | `gradlew.bat :app:testDebugUnitTest` |
| Check Android project configuration | `gradlew.bat :app:tasks` |
| Clean generated build outputs | `gradlew.bat clean` |

To inspect device logs:

```cmd
adb logcat
```

To filter logs for the app process, first obtain its PID:

```cmd
adb shell pidof org.aiguardian.mobile
```

Then use the returned PID with your preferred logcat filtering workflow.

## 6. Architecture direction

The Android client follows an Android-first, edge-oriented architecture:

- **Kotlin + Jetpack Compose:** native application and accessible user interface.
- **CameraX:** camera lifecycle, preview, and future frame analysis.
- **On-device inference:** planned object detection using a validated mobile model/runtime.
- **Tracking and announcement control:** planned temporal filtering to reduce repeated or unstable spoken alerts.
- **Android TextToSpeech:** spoken feedback through the operating system's audio routing.
- **Future adapters:** Wi-Fi camera streams and supported glasses/device integrations, subject to device interfaces.

The next implementation milestone is to validate the existing detector/model contract and integrate on-device object detection without blocking the camera preview. Model input dimensions, preprocessing, output tensor layout, class labels, and post-processing must be verified against the actual exported model before inference is wired into the app.

## 7. Privacy and safety limitations

- The current camera-only MVP does not upload camera frames.
- Future network or cloud features must define explicit data flows, consent, and privacy controls before implementation.
- AI Guardian is an experimental visual-awareness aid, not a certified mobility or navigation device.
- Do not rely on it for road crossing, route selection, obstacle avoidance, or collision prevention.
- Spoken detections, when implemented, must be treated as potentially incomplete or incorrect.

## 8. Build artifact hygiene

Generated Gradle caches, build directories, machine-specific SDK configuration, and local APK outputs must not be committed to Git. The repository's root `.gitignore` excludes these local artifacts.
