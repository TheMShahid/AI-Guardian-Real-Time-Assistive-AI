# AI Guardian — Depth Estimation and Distance Ranging Architecture

| Attribute | Value |
|---|---|
| Project | AI Guardian — Real-Time Assistive AI |
| Document ID | ARCH-DEPTH-001 |
| Version | 0.1 |
| Status | Investigation and design baseline |
| Last Updated | 2026-11-23 |
| Target Platform | Android |
| Current Camera Pipeline | CameraX ImageAnalysis + YOLO |

## 1. Purpose

This document defines a safe engineering path for adding scene-depth awareness and, only after validation, approximate metric distance to AI Guardian.

The current application detects objects and estimates their position within the camera image. Its bounding boxes, detector confidence, visual relevance score, and temporal confirmation state are not physical range measurements. They must never be converted directly into metres.

The initial objective is to determine which depth sources are actually available on the reference phone, define a hardware-independent depth contract, and build a measurable validation process before distance is exposed to users.

## 2. Current baseline and explicit limitations

The Android MVP currently uses CameraX Preview and ImageAnalysis with RGBA_8888 frames, YOLO object detection, a 2D forward-corridor heuristic, and temporal obstacle confirmation.

Current outputs include object category and 2D bounding box, approximate left/centre/right position in the camera image, a heuristic indicating whether a detection overlaps the configured forward image corridor, and confirmation that an obstacle-like detection persisted across separate analysis frames.

Current outputs do not include camera-to-object depth in metres, reliable object approach velocity, a validated traversable free-space map, or ground-plane, stair-edge, curb, or drop-off guarantees.

The PathAwareObstacleAnalyzer relevance score is a ranking heuristic only. The ObstacleStabilityTracker confirms temporal persistence only. Neither is a range sensor.

## 3. Reference device investigation

The reference handset reported by the project is realme model RMX3834, identified in public device records as realme Note 50. Google's current ARCore supported-device list does not return a match for RMX3834 or Note 50.

This means ARCore support and Depth API availability must be treated as unconfirmed and not assumed. A runtime capability check on the physical handset remains necessary. Device model names, Android version, and the presence of Google Play Services for AR alone do not prove Depth API support.

Sources:
- Google ARCore supported devices: https://developers.google.com/ar/devices
- Google ARCore Depth API overview: https://developers.google.com/ar/develop/depth
- Google ARCore Session.isDepthModeSupported reference: https://developers.google.com/ar/reference/java/com/google/ar/core/Session
- realme security advisory mapping RMX3834 to Note 50 / C60: https://www.realme.com/global/legal/AndroidSecurityAdvisories

## 4. Candidate depth sources

### 4.1 ARCore Depth API

ARCore Depth uses depth-from-motion and may combine hardware depth sensors when available. A ToF sensor is not required for ARCore Depth support. Depth mode is disabled by default and must be enabled after checking Session.isDepthModeSupported(Config.DepthMode.AUTOMATIC).

Advantages:
- Produces per-pixel depth images suitable for scene geometry.
- Can provide depth on supported RGB-camera devices without a dedicated ToF sensor.
- Offers a documented runtime support check.

Constraints:
- Only supported devices can use it; support must be checked at runtime.
- Depth quality depends on motion, texture, lighting, distance, and scene conditions.
- The current application owns camera capture through CameraX. ARCore also manages camera capture for its session. Integrating both pipelines is not a trivial dependency addition: the architecture must avoid competing camera ownership. A future integration may need to migrate the camera source to ARCore and feed ARCore CPU images to the detector, or use a specifically supported shared-camera design after technical validation.
- ARCore depth values still require filtering, confidence/quality handling, and real-world validation before safety-critical speech.

Decision: keep ARCore as a conditional candidate, not the assumed implementation for RMX3834.

### 4.2 Monocular depth model

A monocular model estimates relative or scale-ambiguous depth from one RGB image. Some models can produce metric-like outputs under their training assumptions, but generalization to this phone, camera, environment, and target objects must be demonstrated.

Advantages:
- Does not require ARCore certification or a second camera.
- Can be integrated into the existing image pipeline as a separate inference adapter.
- Can potentially run on a broader range of Android devices.

Constraints:
- May be computationally expensive on the reference handset.
- Estimates can fail on reflective, transparent, textureless, dark, or unfamiliar surfaces.
- Absolute scale and calibration may not transfer to the target device.
- A plausible-looking depth map is not proof of metric accuracy.

Decision: evaluate lightweight candidates offline first. Do not integrate a model or announce metres until latency and error are measured on the target phone.

### 4.3 Camera geometry and known-size ranging

Camera intrinsics and known physical object dimensions can support range estimation for a known target under calibrated conditions. The current general-purpose object detector does not know the true physical dimensions of arbitrary chairs, people, bags, or vehicles.

Decision: do not use assumed class dimensions as a general-purpose distance solution. This may be a constrained experiment for carefully defined targets only.

### 4.4 Hardware depth sensors and stereo

A ToF sensor or calibrated stereo camera can provide more direct geometric evidence, but the reference handset's hardware must be inspected rather than inferred from its model family or camera count.

Decision: no ToF or stereo capability is assumed on RMX3834.

## 5. Target architecture and depth contract

Depth must be a separate perception adapter. The navigation layer must not depend directly on a particular model or vendor SDK.

A future normalized depth result should contain source (ARCore, monocular model, stereo, or hardware sensor), frame timestamp, width and height, depth units, per-pixel depth or a documented region statistic, validity mask and confidence/quality where available, camera intrinsics and coordinate-space metadata, processing latency and device/backend metadata, and an explicit status of available, unsupported, temporarily unavailable, or invalid.

A detection-to-depth association must sample a robust region inside the detection box, reject invalid pixels, and report a distribution (for example median and interquartile range), not a single unchecked pixel. The sample must be aligned to the same camera frame and coordinate system as the detection.

## 6. Safety and user-facing output policy

Until a depth source is validated:
- Do not announce numeric distances.
- Do not infer distance from bounding-box size, detector confidence, or relevance score.
- Do not say that a path is safe because no obstacle was detected.
- Preserve conservative obstacle warnings and uncertainty.

After validation, user-facing range should initially use coarse, calibrated bands rather than false precision. Numeric metres require a documented error envelope across distance, object type, lighting, camera motion, and environment. Missing or low-quality depth must result in an explicit unknown range, not a guessed value.

Depth alone does not establish a safe route. Safe navigation also requires free-space and floor understanding, obstacle geometry, camera pose, motion context, and dedicated treatment of stairs, curbs, road edges, and drop-offs. The app must not replace a white cane, guide dog, or orientation-and-mobility training.

## 7. Validation plan

1. Record device model, Android build, ARCore package/service availability, camera IDs, camera characteristics, and sensor inventory.
2. Check ARCore installation and runtime Depth API support on the physical handset; do not rely on the public device list alone.
3. If ARCore Depth is unavailable, benchmark monocular depth candidates using the existing camera frames.
4. Use measured target distances and a repeatable test protocol. Record absolute error, relative error, invalid-pixel rate, latency, frame alignment, and thermal/battery impact.
5. Test multiple surfaces and objects, indoor/outdoor lighting, static and moving camera, and near/far ranges.
6. Define conservative quality gates and fail-closed behavior before enabling spoken range.
7. Conduct supervised usability and safety evaluation; do not make collision-avoidance or safe-navigation claims based only on model metrics.

## 8. Immediate next engineering action

Run a read-only ADB capability inventory on the connected handset. Confirm the runtime model identifiers and whether Google Play Services for AR is installed. Then inspect camera and sensor characteristics. This is an inspection step only; it does not install, modify, or configure anything on the phone.
