# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a camera-based assistive visual intelligence prototype designed to help blind and visually impaired people understand selected aspects of their surroundings through live object detection, temporal tracking, and spoken feedback.

## Current implementation

### Live visual awareness
- YOLOv8 nano object detection
- Streamlit WebRTC live camera stream with annotated video
- Ultralytics ByteTrack object association with temporary track IDs
- Approximate left/right movement in camera-image coordinates
- Live status metrics and current object table
- Configurable detection confidence

### Improved speech experience
- Natural neural text-to-speech through Edge TTS
- Offline `pyttsx3` fallback when neural speech or internet access is unavailable
- English US (Aria) and English UK (Sonia) voice choices
- Adjustable speaking speed
- Complete object-category summaries without a fixed five-object truncation
- Grouping of repeated detections by label and camera-image position
- Test Voice control to check audio before starting the camera
- Manual audio playback controls; browser autoplay is not assumed
- Cached speech audio to avoid regenerating identical announcements repeatedly

## Run the application

```bash
pip install -r requirements.txt
streamlit run app.py
```

Press START in the live camera panel and allow camera access. The status area reports camera processing, visible objects, tracking IDs, and image-plane movement.

Use the sidebar to:
1. Adjust detection sensitivity.
2. Enable or disable live voice announcements.
3. Select the speech voice and speaking speed.
4. Press **Test voice** and then **Play** to verify that audio works.

Natural neural speech requires internet access. If the service cannot be reached, AI Guardian attempts to use the computer's installed offline voice. The displayed speech-engine label identifies which engine produced the audio.

For an on-demand scene summary, use the separate still-image capture section.

## Setup

```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate

pip install -r requirements.txt
```

## Technology

- Python and PyTorch
- Ultralytics YOLO and ByteTrack
- OpenCV
- Streamlit and Streamlit WebRTC
- Edge TTS for natural neural speech
- pyttsx3 for offline fallback speech

## Safety and limitations

AI Guardian is an experimental assistive awareness prototype, not a certified mobility aid. Object detection and tracking can fail, especially with occlusion, poor lighting, camera motion, or unfamiliar objects. Left/right movement describes changes in camera-image coordinates only. It does not establish physical distance, whether an object is approaching, collision probability, or a safe route. Do not rely on this prototype alone for road crossing, navigation, or obstacle avoidance.

## Development approach

Features are implemented incrementally and validated on the target computer and camera. Planned work includes improved scene understanding, speech interruption and prioritization, spatial reasoning, and evaluation on target edge hardware.
