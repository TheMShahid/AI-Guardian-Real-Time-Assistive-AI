# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a camera-based assistive visual intelligence prototype designed to help blind and visually impaired people understand selected aspects of their surroundings through visual detection and concise audio feedback.

## Current implementation

### Feature 1 — Object detection
- YOLOv8 nano object detector
- OpenCV local webcam runner for continuous inference
- Streamlit WebRTC live camera detection with annotated video
- Object labels, confidence scores, and bounding boxes

### Feature 2 — Spoken object announcements
- Text-to-speech using the computer's installed system voice (pyttsx3)
- Short spoken summaries from a captured image
- Approximate horizontal image position: left, centre, or right

### Streamlit interface
The Streamlit app provides a continuous webcam detection panel using `streamlit-webrtc`. It also provides a separate still-image capture workflow for generating a spoken summary. Continuous spoken announcements on every live frame are not enabled yet; duplicate suppression and announcement timing need to be implemented before that is appropriate.

## Setup

Create and activate a virtual environment, then install dependencies:

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

Run the Streamlit interface:

```bash
streamlit run app.py
```

Run the continuous local webcam detector (visual output only):

```bash
python run_camera.py
```

The first run downloads the YOLO model weights automatically. Allow camera access in your browser. WebRTC camera access generally requires localhost or a secure HTTPS connection.

## Technology foundation

- Python
- PyTorch and Ultralytics YOLO
- OpenCV
- Streamlit and Streamlit WebRTC
- pyttsx3 (offline system text-to-speech)

## Safety and limitations

AI Guardian is an assistive awareness prototype, not a certified mobility aid. Object detection can fail. Left/centre/right describes position in the camera image, not physical distance or a verified collision path. Do not rely on this prototype alone for road crossing, navigation, or obstacle avoidance.

## Development approach

Features are implemented incrementally and tested independently. Next milestones include controlled speech announcements for live detections, duplicate-announcement suppression, object tracking, richer scene context, and evaluation on target edge hardware.
