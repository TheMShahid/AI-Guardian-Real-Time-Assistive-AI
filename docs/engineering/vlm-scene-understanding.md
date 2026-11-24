# On-Device Vision-Language Model (VLM) Scene Understanding

## Status

The Android application includes an on-demand VLM scene-description flow using LiteRT-LM and the Apache-2.0-licensed `litert-community/SmolVLM2-500M` model.

The model bundle is approximately 361 MB and is intentionally downloaded after an explicit user action. It is not committed to Git and is not bundled in the APK.

## Runtime architecture

- YOLOv8n ONNX remains the continuous, low-latency object-detection and obstacle-alert path.
- SmolVLM2-500M is an on-demand image-and-text model used for broader scene description.
- Camera frames are not sent to a remote inference service. VLM inference runs locally through LiteRT-LM.
- The VLM receives one independently captured camera snapshot per request.
- A new conversation is created for each image request and closed after inference.
- The VLM result is spoken only when voice announcements are enabled and TTS is ready.
- The VLM must never be treated as a route-safety authority or a source of exact physical distance.

## First-time model setup

1. Open the camera options menu.
2. Select **Download VLM model (about 361 MB)**.
3. Keep the app open and maintain a reliable internet connection until the download completes.
4. The app downloads the model to its private application storage under `files/models/`.
5. The temporary download is renamed into the final model path only after SHA-256 verification.
6. Once the model is ready, choose **Describe scene** whenever a scene-level explanation is needed.

The download is user-initiated. The app verifies the downloaded bundle against the published SHA-256 digest before making it available to the inference engine.

## Model source and license

- Repository: `litert-community/SmolVLM2-500M`
- Bundle: `SmolVLM2-500M.litertlm`
- License: Apache-2.0
- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.19.1`
- Bundle SHA-256 pinned by the downloader: `b808b328d845a600a33c5295f93d9217487317bd334dbc91b2a8d50e26e60ad0`

The source repository may update its main branch. Before changing the pinned checksum, verify the exact model revision and published digest.

## Privacy and resource constraints

- Camera snapshots are held in app memory only for the active inference request and recycled afterward.
- Model files remain in app-private storage.
- No image upload or cloud VLM API is used.
- The model consumes substantial storage and RAM. Device-specific latency, memory pressure, thermal behavior, and GPU compatibility must be measured on the target phone.
- First model initialization can take several seconds and must run off the UI thread.
- The current implementation uses GPU first with CPU fallback. A native GPU driver crash cannot always be recovered by an application-level exception handler; verify on the target device.

## Safety constraints

- VLM descriptions are observations, not guarantees.
- Never announce that a route is safe or instruct the user to walk based on VLM output.
- A single RGB image cannot provide reliable metric distance or predict collision.
- YOLO obstacle alerts remain a separate, continuous path and must not wait for VLM inference.
- The model can hallucinate, omit hazards, misidentify objects, or misunderstand spatial relationships. Keep spoken descriptions concise and treat uncertain scenes as uncertain.

## Target-device validation

On the realme RMX3834, validate:

1. Download completion and SHA-256 verification on Wi-Fi.
2. Model initialization without blocking the camera preview or UI.
3. GPU initialization and CPU fallback behavior.
4. Scene-description latency and device temperature over repeated requests.
5. English, Urdu and Pashto prompt/voice behavior.
6. Whether YOLO obstacle announcements continue while VLM inference runs.
7. Snapshot cleanup after success, failure, and camera stop.

Do not claim production-grade scene understanding until representative indoor and outdoor test scenes have been evaluated by human reviewers.
