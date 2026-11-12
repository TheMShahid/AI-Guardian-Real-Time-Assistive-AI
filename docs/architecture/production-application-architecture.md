# AI Guardian — Production Application Architecture

| Attribute | Value |
|---|---|
| Project | AI Guardian — Real-Time Assistive AI |
| Document ID | ARCH-APP-001 |
| Version | 1.0 |
| Status | Proposed architecture baseline |
| Last Updated | 2026-11-12 |
| Primary Product | Accessible real-time visual-awareness application |
| Initial Target Platform | Android |
| Existing Prototype | Python, Streamlit, WebRTC, YOLO, ByteTrack |

## 1. Purpose

This document defines the target architecture for evolving AI Guardian from a browser-based research prototype into a maintainable application that can be evaluated with blind and low-vision users.

The architecture prioritizes reliable camera capture, low-latency local awareness, accessible speech interaction, privacy, graceful offline behavior, measurable performance, and explicit safety boundaries.

The existing Streamlit application remains useful for model experiments, dataset evaluation, and rapid feature validation. It is not the intended final mobile product.

## 2. Product goals and non-goals

### 2.1 Product goals

1. Provide continuous visual awareness from a phone camera.
2. Describe multiple recognizable objects and their approximate positions within the camera image.
3. Track objects across frames where tracking is available, without suppressing untracked detections.
4. Speak concise, prioritized updates without flooding the user with repeated narration.
5. Read visible printed text on demand, initially supporting English, Urdu, and Pashto.
6. Keep essential perception and user controls available when internet connectivity is unavailable, subject to the selected on-device models and voices.
7. Provide accessible controls compatible with Android accessibility services, screen readers, and Bluetooth audio devices.
8. Make model latency, dropped frames, errors, and speech delivery observable during development and evaluation.

### 2.2 Non-goals for the initial release

- Certified mobility assistance or autonomous navigation.
- Guaranteed obstacle detection or collision avoidance.
- Road-crossing decisions or traffic-safety instructions.
- Guaranteed metric distance or object approach estimation from monocular video.
- Continuous cloud upload of camera frames.
- Replacing a white cane, guide dog, orientation-and-mobility training, or human assistance.

## 3. Architecture principles

- **Edge-first:** run latency-sensitive perception on the phone whenever the target hardware supports it.
- **Hybrid by consent:** cloud inference is optional, feature-specific, and disabled unless the user explicitly enables it.
- **Modular models:** each model has a narrow responsibility and a documented input/output contract.
- **Event-driven orchestration:** do not run every expensive model on every camera frame.
- **Speech is a scarce channel:** prioritize useful changes, suppress duplicates, and allow interruption.
- **Fail visibly and safely:** communicate uncertainty and unavailable capabilities rather than inventing a result.
- **Privacy by default:** process frames locally where possible; do not persist or upload camera content without an explicit user action and clear disclosure.
- **Test before trust:** measure performance and usability under representative conditions before expanding claims.

## 4. Target system context

```text
Android phone camera + microphone
          |
          v
Accessible Android application
  |       |        |
  |       |        +---- Bluetooth headset / speaker
  |       |
  |       +------------- Voice commands and accessibility controls
  |
  v
On-device AI runtime
  |
  +--> Object detection
  +--> Multi-object tracking
  +--> OCR (on demand)
  +--> Optional depth estimation
  +--> Optional scene understanding
  |
  v
Perception and context orchestrator
  |
  +--> Scene event prioritizer
  +--> Speech queue and interruption manager
  +--> User settings and privacy policy
  |
  +--> Local text-to-speech
  |
  +--> Optional authenticated backend API
             |
             +--> Optional cloud model services
             +--> Model/configuration delivery
             +--> Opt-in diagnostics and feedback
```

## 5. Application layers

### 5.1 Android presentation layer

Responsibilities:

- Camera permission and lifecycle management.
- Accessible start, pause, resume, stop, and emergency-stop controls.
- Large touch targets, screen-reader labels, high-contrast visual status, and haptic feedback where useful.
- Language, voice, speech-rate, privacy, and model settings.
- Bluetooth audio routing through Android audio APIs.
- Clear connectivity and capability status.

The initial mobile application should use Kotlin and Jetpack Compose with Android CameraX. Compose semantics and TalkBack support must be included from the beginning, not added after the interface is complete.

### 5.2 Camera and frame pipeline

Use CameraX to acquire frames with explicit lifecycle management. A bounded frame queue must prevent unbounded memory growth when inference is slower than capture.

Initial processing policy:

- Camera capture target: 15–30 frames per second, subject to device capability.
- Detection target: begin at 5–10 inferences per second on a measured reference device; do not assume this target is achievable on every phone.
- Drop stale queued frames rather than accumulating latency.
- Preserve timestamps so tracking and latency measurements use the correct temporal order.
- Adapt input size and inference frequency based on measured device load and thermal state.

These are engineering targets for profiling, not guaranteed product performance.

### 5.3 Perception model group

| Module | Responsibility | Execution policy |
|---|---|---|
| Object detector | Detect supported object categories and bounding boxes | Continuous, lightweight |
| Multi-object tracker | Associate detections across frames and estimate image-plane movement | Continuous when detections are available |
| OCR | Extract visible printed text | User-triggered or context-triggered |
| Monocular depth | Estimate relative depth structure | Selective; only after device profiling |
| Vision-language model | Produce richer descriptions for ambiguous or requested scenes | On demand or event-triggered |
| Speech recognition | Interpret supported voice commands | Push-to-talk initially; wake-word only after privacy and battery review |

The current YOLOv8n + ByteTrack pipeline is the baseline for experiments. Model replacements must be benchmarked for accuracy, latency, memory, license, supported hardware, and deployment format before adoption.

### 5.4 Perception and context orchestrator

The orchestrator receives timestamped detections and context events. It should:

1. Normalize object labels, confidence values, bounding boxes, and timestamps.
2. Associate detections with tracker IDs when available.
3. Retain valid detections when tracker IDs are absent.
4. Calculate left/centre/right relative to the camera image.
5. Detect meaningful scene changes across a configurable time window.
6. Prioritize events using transparent rules and confidence thresholds.
7. Request OCR, depth, or scene-understanding inference only when needed.
8. Attach uncertainty and source information to each generated event.
9. Send speech-ready events to the speech scheduler.

The orchestrator must not convert image position into physical direction unless camera orientation and user-device pose are known and validated.

### 5.5 Speech and interaction manager

Responsibilities:

- Queue short announcements and avoid overlapping playback.
- Deduplicate repeated scene descriptions.
- Prioritize user-requested speech over routine scene updates.
- Allow the user to pause, repeat, interrupt, or silence narration.
- Support English, Urdu, and Pashto through language-matched voices.
- Prefer on-device TTS where quality and language coverage are adequate; use a disclosed online voice service only when enabled.
- Expose speech-generation and playback failures to the application state.

The Android application must use native Android audio playback and audio-focus APIs. Browser autoplay behavior in the Streamlit prototype is not a suitable foundation for the mobile speech experience.

### 5.6 Backend and cloud services

Use Python and FastAPI for optional backend capabilities, not as a mandatory relay for every camera frame.

Initial backend responsibilities:

- Authentication and account/session management if accounts become necessary.
- Versioned model/configuration metadata.
- Optional cloud inference endpoints for models unsuitable for local devices.
- Explicitly opted-in feedback and diagnostics.
- Health, readiness, and service-version endpoints.

The backend must not receive continuous camera video by default. API requests must be authenticated, encrypted in transit, rate-limited, and validated. Uploaded media requires explicit consent, a stated purpose, retention limits, and deletion controls.

## 6. Model execution strategy

Use a tiered inference policy:

- **Tier 1 — continuous:** compact object detector and tracker.
- **Tier 2 — selective:** OCR and depth estimation, invoked by user action or a relevant scene event.
- **Tier 3 — on demand:** vision-language scene explanation or cloud inference, only when requested or clearly enabled.

Do not execute all models on every frame. A scheduler should consider model latency, battery, thermal state, network availability, user settings, and the age of the frame.

Every model adapter should expose a consistent contract containing:

- Model identifier and version.
- Input requirements and preprocessing metadata.
- Output schema and confidence/uncertainty fields.
- Device/backend used for inference.
- Processing duration and frame timestamp.
- Failure state and actionable diagnostic code.

## 7. Core data contracts

### 7.1 Detection event

```json
{
  "label": "person",
  "confidence": 0.91,
  "box_xyxy": [120, 80, 260, 420],
  "track_id": 12,
  "frame_timestamp_ms": 1731400000123,
  "image_position": "centre",
  "position_reference": "camera_image"
}
```

The example is illustrative. A missing track ID is valid and must not invalidate the detection.

### 7.2 Scene event

```json
{
  "event_type": "scene_summary",
  "created_at_ms": 1731400000456,
  "language": "en",
  "summary": "A person is visible near the centre of the camera view.",
  "confidence": 0.84,
  "uncertainty": ["image_position_only"],
  "source_modules": ["object_detector", "object_tracker"]
}
```

### 7.3 Speech job

```json
{
  "job_id": "local-generated-id",
  "priority": "normal",
  "text": "A person is visible near the centre of the camera view.",
  "language": "en",
  "interruptible": true,
  "deduplication_key": "scene-person-centre"
}
```

Use generated identifiers in implementation. Do not treat example values as production identifiers.

## 8. Privacy, security, and data governance

- Camera and microphone permissions are requested only when the corresponding feature is started.
- Local processing is the default.
- No background recording or hidden capture.
- Cloud processing requires a clear, feature-specific opt-in and a visible indication while active.
- Do not store raw camera frames by default.
- If a user explicitly saves a capture or submits feedback, explain where it is stored and how it can be deleted.
- Keep credentials and signing keys out of source control.
- Use TLS for all remote API communication.
- Minimize diagnostic logs; do not log raw images, transcripts, or sensitive personal content by default.
- Define retention and deletion policies before enabling user accounts or cloud media storage.

## 9. Reliability and performance requirements

The following are initial engineering acceptance targets to validate on named reference devices. They are not claims about current prototype performance.

| Area | Initial target / acceptance condition |
|---|---|
| Camera lifecycle | Start, pause, resume, and stop without leaving the camera active unexpectedly |
| Frame queue | Bounded queue; stale frames are dropped rather than increasing latency |
| Detection latency | Measure p50, p95, and maximum latency on each reference device |
| Freshness | Display and speak only results associated with recent frames |
| Speech | No overlapping routine announcements; user command can interrupt routine narration |
| Offline | Core local detection and supported local voice remain usable without network |
| Errors | Camera, model, OCR, TTS, and backend failures are distinguishable |
| Battery/thermal | Profile sustained use and reduce workload when thermal pressure rises |
| Accessibility | Critical workflows pass TalkBack testing with no sighted assistance |
| Privacy | No cloud image transfer unless the user has enabled the specific cloud feature |

Before setting numeric latency service-level objectives, collect measurements on low-, mid-, and high-range Android devices.

## 10. Testing and validation strategy

### 10.1 Automated tests

- Model adapter input validation and output normalization.
- Detections without tracker IDs.
- Tracker ID changes and stale-track expiry.
- Scene event prioritization and deduplication.
- Speech queue ordering, interruption, and cancellation.
- OCR language selection and empty-result handling.
- Offline/network failure behavior.
- Permission denial and camera lifecycle transitions.
- API authentication, schema validation, and rate limits.

### 10.2 Device and field tests

- Indoor and outdoor lighting.
- Static and moving camera.
- Crowded and uncluttered scenes.
- Motion blur, partial occlusion, and small/background objects.
- Weak and absent network.
- Sustained operation and thermal throttling.
- Wired and Bluetooth audio routes.
- TalkBack and common Android accessibility settings.

### 10.3 Human-centred evaluation

Conduct supervised usability studies with blind and low-vision participants only after safety review and an appropriate consent process. Evaluate comprehension, speech usefulness, cognitive load, interruption behavior, trust calibration, and failure recovery. Never ask participants to rely on an unvalidated build for independent mobility or road crossing.

## 11. Delivery roadmap

| Phase | Deliverable | Exit condition |
|---|---|---|
| 0. Baseline | Preserve and test the current Python prototype | Reproducible tests and documented limitations |
| 1. Architecture foundation | Model contracts, orchestrator interfaces, benchmark harness | Components can be tested independently |
| 2. Android camera shell | Kotlin/Compose app with CameraX and accessible controls | Reliable permission and camera lifecycle |
| 3. On-device vision | Detector and tracker integrated through a model adapter | Measured live detections on reference phones |
| 4. Native speech | Speech queue, Android audio playback, interruption controls | No dependence on browser autoplay |
| 5. OCR and language | On-demand OCR and language-matched speech | English, Urdu, Pashto test coverage |
| 6. Selective intelligence | Depth and scene-understanding adapters | Profiling and uncertainty validation |
| 7. Backend integration | Optional FastAPI services and consent-controlled cloud inference | Authenticated, observable, privacy-reviewed APIs |
| 8. Real-world evaluation | Accessibility, reliability, privacy, and safety evaluation | Documented results and release review |

## 12. Architecture decisions

| ID | Decision | Rationale | Status |
|---|---|---|---|
| ADR-APP-001 | Android-first native application using Kotlin, Jetpack Compose, and CameraX | Native camera lifecycle, audio routing, accessibility, and on-device inference integration | Proposed |
| ADR-APP-002 | Keep Streamlit as a research/prototype interface | Avoid treating browser component limitations as mobile product constraints | Accepted |
| ADR-APP-003 | Edge-first inference with optional cloud augmentation | Reduce latency and preserve core operation during network loss | Proposed |
| ADR-APP-004 | Use a modular model adapter and event-driven orchestrator | Allow independent model replacement and selective execution | Proposed |
| ADR-APP-005 | Use native Android audio playback for the mobile product | Control audio focus, interruption, Bluetooth routing, and playback lifecycle | Proposed |
| ADR-APP-006 | No default continuous camera upload or raw-frame persistence | Privacy by default and reduced data exposure | Proposed |
| ADR-APP-007 | Do not make navigation or collision-avoidance claims | Current perception evidence does not establish safe mobility capability | Accepted |

## 13. Release gate

A build must not be described as ready for independent real-world mobility use solely because it detects objects or speaks scene descriptions. A release candidate requires documented device testing, accessibility evaluation, privacy/security review, failure-mode analysis, and supervised evaluation with intended users.

Until those gates are met, AI Guardian remains an experimental visual-awareness aid and must be used alongside established mobility tools and practices.
