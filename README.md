# AI Guardian — Real-Time Assistive AI

AI Guardian is a camera-based assistive visual-awareness prototype for blind and visually impaired people. It combines live object detection, multi-frame tracking, image-plane position descriptions, and spoken scene summaries.

> **Safety notice:** AI Guardian is an experimental awareness aid, not a certified mobility or navigation device. It must not be relied on for road crossing, route selection, or collision avoidance.

## Product capabilities

### Live visual awareness
- YOLOv8 nano object detection, using a 416-pixel inference size by default.
- Cloud-friendly inference throttling targets up to 5 AI inference passes per second; intermediate camera frames reuse the latest annotated result.
- Lazy-loads the Ultralytics model package when the live processor starts rather than during the initial page import.
- Live browser camera streaming through WebRTC.
- Object association across frames with ByteTrack.
- Temporary track IDs, approximate left/centre/right image position, and left/right image-plane movement.
- A compact, centred live preview that displays the server-annotated video stream.
- Input-frame conversion is skipped between throttled inference passes to reduce avoidable CPU work.
- Live camera, processing, and detection status.
- Configurable detection confidence.
- Still-image scene analysis on demand.
- OCR text reading from camera snapshots using Tesseract for English, Urdu, and Pashto printed text.
- Extracted text is displayed and read aloud in the selected assistant language.

### Multilingual visual and voice assistance
- Assistant language selector: English, Urdu (اردو), and Pashto (پښتو).
- Localized live scene summaries and image-relative position phrases.
- Microsoft neural voices: English, Pakistani Urdu (Asad / Uzma), and Pashto (Gul Nawaz).
- Neural speech through Edge TTS with offline pyttsx3 fallback for system-supported voices.
- Live speech synthesis runs in a background worker so network delays do not block the status interface.
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

The app includes Google's public STUN server for WebRTC connectivity. Some networks and hosting paths require a TURN relay as well. If the camera fails to connect after deployment, configure a TURN service and add these values in the app's Settings → Secrets:

~~~toml
TURN_URLS = ["turn:your-turn-host:3478"]
TURN_USERNAME = "your-turn-username"
TURN_CREDENTIAL = "your-turn-credential"
~~~

Replace the example values with credentials from your TURN provider. Do not commit real credentials to GitHub. The application reads these values from Streamlit Secrets and adds them to its WebRTC ICE configuration. If your provider supplies multiple TURN URLs, include them all in the TURN_URLS array.

### Cloud deployment limitations

- Community Cloud is a shared environment. Model loading and live inference may be slower than on a capable local computer. Inference is deliberately throttled to reduce CPU pressure, so the annotated preview is not guaranteed to update at the camera's full frame rate.
- YOLO model weights are downloaded by Ultralytics on first use; the first camera start may take longer.
- Browser camera permission and a working WebRTC network path are required.
- A TURN service may be necessary on restrictive networks. TURN credentials are not bundled with this repository.
- Browser autoplay policies may block the first spoken announcement. Use the audio player's Play control if needed.
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

## Current limitations and responsible use

- Object recognition is limited to the classes learned by the selected YOLO model.
- Performance can degrade in low light, motion blur, occlusion, crowded scenes, and unfamiliar environments.
- Left, centre, and right refer to the camera image, not the user's body orientation or a world-coordinate map.
- Image-plane motion is not evidence that an object is physically approaching.
- No reliable metric distance, collision probability, safe walking path, or road-crossing instruction is currently provided.
- Audio playback depends on browser policies, network access, and available system voices.

## Roadmap

1. Robust live camera and speech experience, including annotated preview and image-relative object positions.
2. Temporal scene understanding and announcement scheduling.
3. Depth-aware spatial awareness with explicit uncertainty.
4. Hazard-awareness research and controlled evaluation.
5. Edge deployment profiling and accessibility testing with blind and low-vision participants.

## Technology

Python · PyTorch · Ultralytics YOLO · ByteTrack · OpenCV · Streamlit · streamlit-webrtc · Edge TTS · pyttsx3
