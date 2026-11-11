# AI Guardian — Real-Time Assistive AI

AI Guardian is a camera-based assistive visual-awareness prototype for blind and visually impaired people. It combines live object detection, multi-frame tracking, image-plane position descriptions, and spoken scene summaries.

> **Safety notice:** AI Guardian is an experimental awareness aid, not a certified mobility or navigation device. It must not be relied on for road crossing, route selection, or collision avoidance.

## Product capabilities

### Live visual awareness
- YOLOv8 nano object detection.
- Live browser camera streaming through WebRTC.
- Object association across frames with ByteTrack.
- Temporary track IDs and approximate left/right movement in image coordinates.
- Live camera, processing, and detection status.
- Configurable detection confidence.
- Still-image scene analysis on demand.

### Intelligent voice assistant
- Neural speech through Edge TTS with offline pyttsx3 fallback.
- Selectable English US and UK voices.
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

Use Python 3.11 or 3.12 for a consistent development environment.

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

- Community Cloud is a shared environment. Model loading and live inference may be slower than on a capable local computer.
- YOLO model weights are downloaded by Ultralytics on first use; the first camera start may take longer.
- Browser camera permission and a working WebRTC network path are required.
- A TURN service may be necessary on restrictive networks. TURN credentials are not bundled with this repository.
- Browser autoplay policies may block the first spoken announcement. Use the audio player's Play control if needed.
- A public cloud app should not be treated as a private or guaranteed real-time safety service.

## Controls

- Detection sensitivity: confidence threshold for object detection.
- Automatic live voice: enables or disables spoken scene updates.
- Speech voice / speed: selects neural voice and delivery rate.
- Test voice: checks speech generation independently of the camera.
- On-demand scene description: captures and analyzes a still image.

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

1. Robust live camera and speech experience.
2. Temporal scene understanding and announcement scheduling.
3. Depth-aware spatial awareness with explicit uncertainty.
4. Hazard-awareness research and controlled evaluation.
5. Edge deployment profiling and accessibility testing with blind and low-vision participants.

## Technology

Python · PyTorch · Ultralytics YOLO · ByteTrack · OpenCV · Streamlit · streamlit-webrtc · Edge TTS · pyttsx3
