# AI Guardian — Real-Time Assistive AI for Blind and Visually Impaired People

AI Guardian is a real-time, camera-based assistive visual intelligence system designed to help blind and visually impaired people understand their surroundings through concise audio feedback.

## Initial implementation scope

The first development milestone focuses on a local webcam prototype that detects common objects in live video using OpenCV and a lightweight YOLO model. Later milestones will add spatial context, object tracking, scene descriptions, and speech feedback.

## Current status

**In development — Feature 1: Live Camera + Object Detection.**

## Planned capabilities

- Live camera capture and frame processing
- Real-time object detection and visual annotations
- Relative object direction (left, centre, right)
- Object tracking and scene context
- Text-to-speech announcements
- Optional OCR and voice-command support
- Evaluation for eventual edge-device deployment

## Technology foundation

- Python
- OpenCV
- PyTorch
- Ultralytics YOLO

## Safety statement

AI Guardian is an assistive awareness prototype, not a certified mobility aid. Object detection can fail, camera-only distance estimates can be inaccurate, and users must not rely on this system alone for road crossing, obstacle avoidance, or other safety-critical decisions.

## Development approach

Features will be implemented incrementally, tested independently, and documented alongside their technical purpose and limitations.
