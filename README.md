# AI Guardian — Real-Time Assistive AI

AI Guardian is an experimental camera-based visual-awareness system for blind and visually impaired people. The current research prototype combines live object detection, multi-frame tracking, image-plane position descriptions, OCR, and spoken scene summaries. The target product is an accessible Android application with edge-first AI inference and optional privacy-controlled cloud services.

> **Safety notice:** AI Guardian is an experimental awareness aid, not a certified mobility or navigation device. It must not be relied on for road crossing, route selection, or collision avoidance.

## Product capabilities

### Live visual awareness
- YOLOv8 nano object detection, using a 416-pixel inference size by default.
- Cloud-friendly inference throttling targets up to 5 AI inference passes per second; intermediate camera frames reuse the latest annotated result.
- Lazy-loads the Ultralytics model package when the live processor starts rather than during the initial page import.
- Live browser camera streaming through WebRTC.
- On-demand Wi-Fi IP-camera frame capture over RTSP/RTSPS, HTTP JPEG snapshots, or HTTP MJPEG streams, followed by YOLO scene analysis and spoken description; the app must run on a computer that can reach the camera on the same network.
- Bluetooth headset playback through the operating system's selected audio output; pair the headset in device settings and use the Test voice control. The Streamlit browser app does not perform Bluetooth pairing.
- Object association across frames with ByteTrack; raw detections remain available for scene announcements even before a persistent track ID is assigned.
- Live AI inference-latency and model-initialization telemetry to distinguish slow model startup from slow per-frame inference.
- Temporary track IDs, approximate left/centre/right image position, and left/right image-plane movement.
- A compact, centred live preview that displays the server-annotated video stream.
- Input-frame conversion is skipped between throttled inference passes to reduce avoidable CPU work.
- Live camera, processing, and detection status.
- Configurable detection confidence.
- Still-image scene analysis on demand.
- OCR text reading from camera snapshots using Tesseract for English, Urdu, and Pashto printed text.
- Extracted text is displayed and read aloud in the selected assistant language.
- Long OCR readings are split at sentence/word boundaries into smaller TTS requests and played sequentially through one accessible audio player, with pause/resume and replay-all controls.

### Multilingual visual and voice assistance
- Assistant language selector: English, Urdu (اردو), and Pashto (پښتو).
- Localized live scene summaries and image-relative position phrases.
- Microsoft neural voices: English, Pakistani Urdu (Asad / Uzma), and Pashto (Gul Nawaz).
- Neural speech through Edge TTS with offline pyttsx3 fallback for system-supported voices.
- Live speech synthesis runs in a background worker so network delays do not block the status interface.
- Live announcement audio is rendered outside the two-second auto-refreshing status fragment to prevent status refreshes from resetting playback.
- Urdu/Pashto speech does not silently fall back to an unrelated English system voice when the neural voice service is unavailable; a clear error is shown instead.
- Selectable language-specific voices.
- Adjustable speaking speed.
- General awareness categories are prioritized in concise scene summaries.
- Stable-scene gating and cooldown reduce repeated announcements.
- Voice test control and manual playback fallback for browser autoplay restrictions.

### Deployment readiness
- Streamlit Community Cloud-compatible root entrypoint: app.py.
- Root-level Python dependencies in requirements.txt.
- Linux runtime packages in packages.txt.
- Central theme and server settings in .streamlit/config.toml.
- WebRTC STUN configuration with optional TURN credentials supplied through Streamlit Secrets.

## Native Android application

The first native Android foundation is now available in [android/](android/README.md). It provides a CameraX live phone-camera preview, explicit camera permission and start/stop controls, and a native Android voice test intended for Bluetooth headset verification.

The Android app is being developed separately from the Streamlit research prototype. The initial Android milestone does not yet include on-device YOLO inference, OCR, Wi-Fi IP-camera streaming, or camera-glasses adapters. A GitHub Actions workflow builds a debug APK when Android project files change; use the Android Debug APK workflow artifact for device installation after a successful build.

## Run locally

Use Python 3.11 or 3.12 for a consistent development environment. The cloud-oriented dependency set uses headless OpenCV to avoid installing desktop GUI components.

~~~bash
python -m venv .venv

# Windows
.venv\\Scripts\\activate

# macOS / Linux
source .venv/bin/activate

pip install -r requirements.txt
streamlit run app.py
~~~

Allow browser camera access, then press START in the live camera panel. The app starts the camera only after the user action. Automatic live voice is enabled by default.

## Deploy to Streamlit Community Cloud

1. Sign in at https://share.streamlit.io/ using GitHub.
2. Authorize Streamlit to access the GitHub account/repository. The repository owner must grant the required access.
3. Select Create app and choose "Yup, I have an app".
4. Select repository: TheMShahid/AI-Guardian-Real-Time-Assistive-AI.
5. Select branch: main.
6. Set the main file path to: app.py.
7. In Advanced settings, select Python 3.12, then deploy.

Streamlit Community Cloud installs Python dependencies from requirements.txt and Linux packages from packages.txt. The app is served over HTTPS, which is required for browser camera access on a remote host.

### WebRTC connectivity and optional TURN

The app includes Google's public STUN server and can request temporary Cloudflare TURN credentials. To enable the optional Cloudflare TURN relay, add the following values in the app's Settings → Secrets:

~~~toml
CF_TURN_KEY_ID = "your-cloudflare-turn-key-id"
CF_TURN_API_TOKEN = "your-cloudflare-api-token"
~~~

Replace the example values with credentials from Cloudflare Calls TURN. Do not commit real credentials to GitHub. The long-lived API token remains server-side; the application requests temporary ICE credentials for the browser. If these secrets are absent, the app attempts STUN-only connectivity.

### Cloud deployment limitations

- Community Cloud is a shared environment. Model loading and live inference may be slower than on a capable local computer. Inference is deliberately throttled to reduce CPU pressure, so the annotated preview is not guaranteed to update at the camera's full frame rate.
- YOLO model weights are downloaded by Ultralytics on first use; the first camera start may take longer.
- Browser camera permission and a working WebRTC network path are required.
- Wi-Fi IP-camera support currently captures one frame on demand from RTSP/RTSPS, HTTP JPEG snapshot endpoints (for example, `/shot.jpg`), or HTTP MJPEG endpoints (for example, `/video`). It does not provide continuous IP-camera playback. The Streamlit host must be able to reach the camera over the local network; a cloud-hosted app normally cannot reach a private home-network camera.
- Bluetooth pairing and audio-route selection are handled by Windows/Android/iOS settings. The app can play speech through the system-selected output but cannot manage pairing.
- A TURN service may be necessary on restrictive networks. TURN credentials are not bundled with this repository.
- Browser autoplay policies may block the first spoken announcement. Use the audio player's Play control if needed.
- Long OCR speech is generated one segment at a time; generation time depends on text length and the online neural TTS service.
- A public cloud app should not be treated as a private or guaranteed real-time safety service.

## OCR setup

The OCR engine and language data are separate from Python packages. On Windows, install Tesseract 5 (for example, the UB Mannheim build), and ensure its installation directory is on PATH. During setup, include English, Urdu (`urd`), and Pashto (`pus`) language data. Verify with:

~~~powershell
tesseract --list-langs
~~~

The list should include `eng`, `urd`, and `pus`. On Debian/Ubuntu, `packages.txt` installs the engine and these language packs. OCR quality depends on focus, lighting, print quality, font, and script; Urdu/Pashto Nastaliq and handwriting may be imperfect.

## Controls

- Assistant language: selects English, Urdu, or Pashto for scene summaries and OCR speech.
- Detection sensitivity: confidence threshold for object detection.
- Automatic live voice: enables or disables spoken scene updates.
- Speech voice / speed: selects neural voice and delivery rate.
- Test voice: checks speech generation independently of the camera.
- Wi-Fi IP camera: enter an RTSP/RTSPS URL, HTTP JPEG snapshot URL, or HTTP MJPEG URL to capture and analyze one frame. For IP Webcam, use `http://PHONE_IP:8080/shot.jpg` or `http://PHONE_IP:8080/video`. The URL field is masked because it may contain credentials.
- On-demand scene description: captures and analyzes a still image.
- Read visible text: extracts printed text from the captured image and reads it aloud.

## Architecture overview

~~~text
Browser camera
    |
    v
WebRTC video stream
    |
    v
YOLO object detector ---> ByteTrack association
    |                          |
    v                          v
Object labels, boxes       Temporary track IDs
    |                          |
    +------------+-------------+
                 |
                 v
       Scene announcement builder
                 |
                 v
       Edge TTS / offline TTS
                 |
                 v
       Browser audio playback
~~~

## Production application direction

The production target is an Android-first native application using Kotlin, Jetpack Compose, and CameraX. The intended architecture separates continuous lightweight object detection/tracking from selective OCR, depth estimation, and vision-language scene understanding. A perception orchestrator will prioritize events and manage speech through native Android audio APIs. FastAPI will provide optional backend services; continuous camera upload is not the default.

The full architecture, model execution strategy, privacy requirements, testing plan, and staged delivery gates are documented in [Production Application Architecture](docs/architecture/production-application-architecture.md).

The Streamlit application remains the research and model-validation prototype. Browser autoplay, shared cloud compute, and WebRTC constraints make it unsuitable as the final mobile interface.

## Current limitations and responsible use

- Object recognition is limited to the classes learned by the selected YOLO model.
- Performance can degrade in low light, motion blur, occlusion, crowded scenes, and unfamiliar environments.
- Left, centre, and right refer to the camera image, not the user's body orientation or a world-coordinate map.
- Image-plane motion is not evidence that an object is physically approaching.
- No reliable metric distance, collision probability, safe walking path, or road-crossing instruction is currently provided.
- Audio playback depends on browser policies, network access, and available system voices.

## Roadmap

1. Define model contracts, orchestration interfaces, and a reproducible benchmark harness.
2. Build the accessible Android camera application and native speech pipeline.
3. Integrate on-device detection/tracking and multilingual OCR.
4. Add selective depth and scene-understanding models after device profiling.
5. Integrate optional FastAPI/cloud services with explicit consent.
6. Complete privacy, accessibility, reliability, and supervised real-world evaluation.

## Technology

Python · PyTorch · Ultralytics YOLO · ByteTrack · OpenCV · Streamlit · streamlit-webrtc · Edge TTS · pyttsx3
