# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a camera-based assistive visual intelligence prototype designed to help blind and visually impaired people understand selected aspects of their surroundings through visual detection and concise audio feedback.

## Current implementation

### Feature 1 — Live object detection
- YOLOv8 nano object detector
- OpenCV local webcam runner for continuous inference
- Streamlit WebRTC live camera detection with annotated video
- Configurable detection confidence
- Live status panel with processed-frame count, latest detections, and pipeline errors

### Feature 2 — Spoken object announcements
- Text-to-speech using the computer's installed system voice (pyttsx3)
- Snapshot-based spoken scene summaries
- Live scene announcements when the detected object/position set changes
- Seven-second minimum announcement interval to reduce repetitive speech
- Left, centre, and right refer only to horizontal image position

## Streamlit interface

Run:

```bash
streamlit run app.py
```

Press START in the live video panel and allow camera access. The returning video should show YOLO bounding boxes. The live status panel refreshes once per second and reports whether frames are reaching the detector. The sidebar confidence slider controls the minimum detection confidence; lower values can increase detections and false positives.

The live voice feature is rate-limited and announces changed detection scenes. Browser autoplay policies may require the user to start audio playback. The separate snapshot workflow remains available for an on-demand spoken summary.

For local OpenCV camera detection (visual output only):

```bash
python run_camera.py
```

## Setup

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

## Technology foundation

- Python
- PyTorch and Ultralytics YOLO
- OpenCV
- Streamlit and Streamlit WebRTC
- pyttsx3 (offline system text-to-speech)

## Safety and limitations

AI Guardian is an assistive awareness prototype, not a certified mobility aid. Object detection can fail. Left/centre/right describes position in the camera image, not physical distance or a verified collision path. Do not rely on this prototype alone for road crossing, navigation, or obstacle avoidance.

## Development approach

Features are implemented incrementally and tested independently. Next milestones include object tracking, temporal scene understanding, more robust speech scheduling, spatial reasoning, and evaluation on target edge hardware.
