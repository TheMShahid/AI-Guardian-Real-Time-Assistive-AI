# AI Guardian Android App

This directory contains the first native Android application foundation. It is separate from the existing Streamlit research prototype.

## Current implementation

- Kotlin + Jetpack Compose accessible application shell.
- CameraX back-camera live preview with explicit camera permission and start/stop controls.
- Native Android TextToSpeech voice test.
- Speech uses Android's accessibility audio usage. When a Bluetooth headset is connected and selected by Android, system audio routing normally sends speech to that headset.
- No camera frames are uploaded by this initial app shell.

## Build environment

- Android Studio with support for Android Gradle Plugin 9.4.0.
- JDK 17.
- Android SDK Platform 36.
- Gradle 9.6.0.
- A physical Android phone is recommended for camera and Bluetooth testing.

Open this android directory as a project in Android Studio. If Android Studio asks to create or configure the Gradle wrapper, use Gradle 9.6.0. Then allow Gradle sync to complete.

## First phone test

1. Enable Developer options and USB debugging on the Android phone.
2. Connect the phone to the development computer and accept the USB debugging prompt.
3. In Android Settings, pair and connect Bluetooth earphones/headset.
4. In Android Studio, select the phone as the deployment target and run the app configuration.
5. In AI Guardian, grant camera permission and press Start camera.
6. Press Test voice. Confirm that the spoken test is audible through the selected Bluetooth headset. Android controls the system media/accessibility audio route; the app does not silently pair with or take over a headset.
7. Stop the camera before leaving the app.

## Current limitations

- This first milestone is a native camera/audio foundation. YOLO detection, ByteTrack, OCR, Wi-Fi IP-camera input, and glasses/vendor SDK adapters are not integrated into this Android shell yet.
- The existing Python/Streamlit prototype remains available for model experiments.
- Bluetooth audio output relies on Android's normal connected-device routing. A future settings screen may expose device status and route diagnostics.
- Wi-Fi camera support will be added through a camera-source adapter after phone-camera capture and on-device inference are validated.
- Camera glasses support depends on whether a particular device exposes a supported UVC, network stream, or vendor SDK interface.

## Safety

AI Guardian is an experimental visual-awareness aid, not a certified mobility or navigation device. Do not rely on it for road crossing, route selection, or collision avoidance.
