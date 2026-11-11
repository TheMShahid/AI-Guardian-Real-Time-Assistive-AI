# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a camera-based assistive visual intelligence prototype designed to help blind and visually impaired people understand selected aspects of their surroundings through concise audio feedback.

## Current implementation

### Feature 1 — Object detection
- YOLOv8 nano object detector
- OpenCV camera runner for continuous local webcam inference
- Object labels, confidence scores, and bounding boxes

### Feature 2 — Spoken object announcements
- Text-to-speech using the computer's installed system voice (pyttsx3)
- Short spoken descriptions of detected object labels
- Approximate horizontal image position: left, centre, or right

### Streamlit interface
The Streamlit prototype lets a user capture a still image, analyze it, inspect the annotated detections, and listen to the spoken summary. This camera-input workflow processes one captured image at a time; it is not continuous live video.

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

The first run downloads the YOLO model weights automatically. Keep the camera permission enabled.

## Technology foundation

- Python
- PyTorch and Ultralytics YOLO
- OpenCV
- Streamlit
- pyttsx3 (offline system text-to-speech)

## Safety and limitations

AI Guardian is an assistive awareness prototype, not a certified mobility aid. Object detection can fail. Left/centre/right describes position in the camera image, not physical distance or a verified collision path. Do not rely on this prototype alone for road crossing, navigation, or obstacle avoidance.

## Development approach

Features are implemented incrementally and tested independently. The next milestones include integrating speech with continuous camera frames, duplicate-announcement suppression, object tracking, richer scene context, and evaluation on target edge hardware.
