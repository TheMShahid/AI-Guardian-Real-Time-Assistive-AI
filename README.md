# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a camera-based assistive visual intelligence prototype designed to help blind and visually impaired people understand selected aspects of their surroundings through visual detection, temporal tracking, and spoken feedback.

## Current implementation

### Feature 1 — Live object detection
- YOLOv8 nano object detector
- OpenCV local webcam runner for continuous inference
- Streamlit WebRTC live camera detection with annotated video
- Configurable detection confidence
- Live status panel with processed-frame count, detections, and pipeline errors

### Feature 2 — Complete spoken scene summaries
- Offline text-to-speech using the computer's installed system voice (pyttsx3)
- Snapshot-based and live spoken scene summaries
- All detected object categories are included; repeated objects are grouped by category and image position
- Clearer speech rate and full-volume synthesis
- Live announcements are rate-limited to avoid constant repetition
- Browser autoplay policies may require the user to press Play
- Left, centre, and right refer only to horizontal camera-image position

### Feature 3 — Object tracking and image-plane movement
- Ultralytics ByteTrack associates detections across consecutive frames
- Temporary track IDs help distinguish visible objects over time
- Per-track horizontal image movement is classified as moving left, moving right, or stationary
- Stale track history is discarded after an object disappears
- Track IDs are temporary and may change after occlusion or tracking loss

## Streamlit interface

Run:

```bash
streamlit run app.py
```

Press START in the live video panel and allow camera access. YOLO bounding boxes should appear on the returning video. The live status panel refreshes once per second and shows frame processing, current tracked objects, track IDs, confidence, and image-plane movement.

The sidebar confidence slider controls the minimum detection confidence. If you change the threshold while a camera stream is running, stop and start the stream again so the processor uses the new setting.

For spoken feedback, enable live voice announcements. The system groups repeated detections instead of silently dropping everything after the first five objects. If the browser blocks autoplay, press Play on the generated audio player.

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
- ByteTrack (included with Ultralytics)
- OpenCV
- Streamlit and Streamlit WebRTC
- pyttsx3 (offline system text-to-speech)

## Safety and limitations

AI Guardian is an experimental assistive awareness prototype, not a certified mobility aid. Object detection and tracking can fail, especially with occlusion, poor lighting, camera motion, or unfamiliar objects. Track movement describes changes in image coordinates only. It does not establish physical distance, whether an object is approaching, collision probability, or a safe route. Do not rely on this prototype alone for road crossing, navigation, or obstacle avoidance.

## Development approach

Features are implemented incrementally and must be validated on the target computer and camera. Next milestones include temporal scene reasoning, robust speech queuing and interruption controls, spatial awareness, and evaluation on target edge hardware.
