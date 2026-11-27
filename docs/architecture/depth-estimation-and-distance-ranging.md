# AI Guardian — Depth Estimation and Distance Ranging Architecture

| Attribute | Value |
|---|---|
| Project | AI Guardian — Real-Time Assistive AI |
| Document ID | ARCH-DEPTH-001 |
| Version | 0.2 |
| Status | Device investigation complete; depth adapter contract implemented; metric source pending |
| Last Updated | 2026-11-27 |
| Target Platform | Android |
| Current Camera Pipeline | CameraX ImageAnalysis + YOLO |

## 1. Purpose

This document defines a safe engineering path for adding scene-depth awareness and, only after validation, approximate metric distance to AI Guardian.

The current application detects objects and estimates their position within the camera image. YOLO bounding boxes, detector confidence, visual relevance scores, and temporal confirmation are not physical range measurements. They must never be converted directly into metres.

The immediate engineering objective is to introduce a hardware/model-independent metric-depth contract, then evaluate a real depth source on the reference handset. No spoken metric distance is enabled until the source and its error are validated.

## 2. Current baseline and explicit limitations

The Android MVP uses CameraX Preview and ImageAnalysis with RGBA_8888 frames, YOLO object detection, a 2D forward-corridor heuristic, and temporal obstacle confirmation.

Current outputs include object category and 2D bounding box, approximate left/centre/right position in the camera image, a heuristic indicating whether a detection overlaps the configured forward image corridor, and confirmation that an obstacle-like detection persisted across separate analysis frames.

Current outputs do not include camera-to-object depth in metres, reliable object approach velocity, a validated traversable free-space map, or ground-plane, stair-edge, curb, or drop-off guarantees.

The existing VisualDistanceEstimator depends on a user-provided reference object height and measured camera-to-object distance. It is a pinhole-camera/class-size-prior experiment, not an automatic depth sensor. It must not be presented as calibration-free metric ranging.

The PathAwareObstacleAnalyzer relevance score is a ranking heuristic only. The ObstacleStabilityTracker confirms temporal persistence only. Neither is a range sensor.

## 3. Reference device investigation

ADB inspection on the connected handset returned:

| Property | Observed value |
|---|---|
| Product model | RMX3834 |
| Android device identifier | RE5C9F |
| SoC / hardware | Unisoc T612 |
| Board platform | ums9230 |
| Android version | 13 |
| ABI | arm64-v8a |
| Reported total RAM | 3,862,528 kB (approximately 3.7 GiB) |
| Available RAM at inspection | 947,320 kB |
| GPU | ARM Mali-G57, OpenGL ES 3.2 |
| ARCore package | No `com.google.ar.core` package found in the package query |

The available-RAM value is a point-in-time observation, not a fixed application memory budget. It can change with foreground/background processes and thermal state.

The handset is identified as realme RMX3834 / Note 50 in project research. Google's public ARCore supported-device list does not return a match for RMX3834 or Note 50. The package query also did not find the ARCore package. ARCore Depth is therefore not currently available as an assumed capability. A supported-device/runtime check would still be required before any future ARCore integration.

Sources:
- Google ARCore supported devices: https://developers.google.com/ar/devices
- Google ARCore Depth API overview: https://developers.google.com/ar/develop/depth
- Google ARCore Session.isDepthModeSupported reference: https://developers.google.com/ar/reference/java/com/google/ar/core/Session
- realme security advisory mapping RMX3834 to Note 50 / C60: https://www.realme.com/global/legal/AndroidSecurityAdvisories

## 4. Candidate depth sources

### 4.1 ARCore Depth API

ARCore Depth uses depth-from-motion and may combine hardware depth sensors when available. A ToF sensor is not required for ARCore Depth support. Depth mode is disabled by default and must be enabled after checking Session.isDepthModeSupported(Config.DepthMode.AUTOMATIC).

Constraints:
- The public supported-device list does not currently identify RMX3834 as supported.
- The handset package query did not find `com.google.ar.core`.
- Depth quality depends on motion, texture, lighting, distance, and scene conditions.
- The current application owns camera capture through CameraX. ARCore also manages camera capture for its session. Integrating both pipelines is not a trivial dependency addition: the architecture must avoid competing camera ownership. A future integration may need to migrate the camera source to ARCore and feed ARCore CPU images to the detector, or use a specifically supported shared-camera design after technical validation.

Decision: do not select ARCore as the current handset's primary depth source.

### 4.2 Monocular depth model

A monocular model estimates relative or scale-ambiguous depth from one RGB image. Some models can produce metric-like outputs under their training assumptions, but generalization to this phone, camera, environment, and target objects must be demonstrated.

The reference device has a Unisoc T612, Mali-G57 GPU, arm64-v8a ABI, and approximately 3.7 GiB reported total RAM. These specifications favour evaluating compact models and CPU/NNAPI-compatible paths before considering a large model. GPU delegate support and actual inference performance must be measured on-device; OpenGL ES 3.2 alone does not prove that a particular ML delegate is supported.

Advantages:
- Does not require ARCore certification or a second camera.
- Can be integrated into the existing image pipeline as a separate inference adapter.
- Can potentially run on a broader range of Android devices.

Constraints:
- May be computationally expensive on the reference handset.
- Estimates can fail on reflective, transparent, textureless, dark, or unfamiliar surfaces.
- Absolute scale and calibration may not transfer to the target device.
- A plausible-looking depth map is not proof of metric accuracy.
- Concurrent YOLO and depth inference can increase memory pressure, heat, battery use, and frame latency.

Decision: evaluate lightweight candidates offline, then benchmark one candidate on the physical phone. Do not integrate model weights or announce metres until latency and error are measured on the target phone.

### 4.3 Camera geometry and known-size ranging

Camera intrinsics and known physical object dimensions can support range estimation for a known target under calibrated conditions. The current general-purpose object detector does not know the true physical dimensions of arbitrary chairs, people, bags, or vehicles.

Decision: do not use assumed class dimensions as a general-purpose distance solution.

### 4.4 Hardware depth sensors and stereo

A ToF sensor or calibrated stereo camera can provide more direct geometric evidence, but the reference handset's hardware must be inspected rather than inferred from its model family or camera count.

Decision: no ToF or stereo capability is assumed on RMX3834.

## 5. Implemented metric-depth adapter contract

The Android source now includes `MetricDepthFrame`, `DetectionDepthSampler`, `DetectionDepthSample`, and `DepthSampleQuality`.

The sampler:
- Accepts a depth frame explicitly expressed in metres and a timestamp for the corresponding YOLO detection frame.
- Rejects a depth frame if it is more than 100 ms away from the detection timestamp by default.
- Samples the inner 60% of the detection box to reduce boundary contamination.
- Ignores non-finite, non-positive, and out-of-range depth values (outside 0.10–20.0 m).
- Requires at least 45% valid sampled pixels.
- Reports median and interquartile range rather than relying on one pixel.
- Rejects regions with excessive depth spread instead of speaking a misleading point estimate.

This is an adapter and quality gate only. It does not generate depth, calibrate a relative depth map into metres, or establish RGB/depth registration. A future provider must prove metric units, coordinate alignment, and frame synchronization before its output can be passed to this sampler. It is not yet connected to the camera pipeline or speech path.

Unit tests cover valid metric samples, stale frames, invalid regions, low valid-pixel coverage, high-variance regions, and invalid-depth outliers.

## 6. Target architecture and depth contract

Depth must be a separate perception adapter. The navigation layer must not depend directly on a particular model or vendor SDK.

A future normalized depth result should contain source (ARCore, monocular model, stereo, or hardware sensor), frame timestamp, width and height, depth units, per-pixel depth or a documented region statistic, validity mask and confidence/quality where available, camera intrinsics and coordinate-space metadata, processing latency and device/backend metadata, and an explicit status of available, unsupported, temporarily unavailable, or invalid.

A detection-to-depth association must sample a robust region inside the detection box, reject invalid pixels, and report a distribution (for example median and interquartile range), not a single unchecked pixel. The sample must be aligned to the same camera frame and coordinate system as the detection.

## 7. Safety and user-facing output policy

Until a depth source is validated:
- Do not announce numeric distances.
- Do not infer distance from bounding-box size, detector confidence, or relevance score.
- Do not say that a path is safe because no obstacle was detected.
- Preserve conservative obstacle warnings and uncertainty.

After validation, user-facing range should initially use coarse, calibrated bands rather than false precision. Numeric metres require a documented error envelope across distance, object type, lighting, camera motion, and environment. Missing or low-quality depth must result in an explicit unknown range, not a guessed value.

Depth alone does not establish a safe route. Safe navigation also requires free-space and floor understanding, obstacle geometry, camera pose, motion context, and dedicated treatment of stairs, curbs, road edges, and drop-offs. The app must not replace a white cane, guide dog, or orientation-and-mobility training.

## 8. Validation plan

1. Record device model, Android build, ARCore package/service availability, camera IDs, camera characteristics, and sensor inventory.
2. Check ARCore installation and runtime Depth API support on the physical handset; do not rely on the public device list alone.
3. If ARCore Depth is unavailable, benchmark compact monocular depth candidates using the existing camera frames.
4. Use measured target distances and a repeatable test protocol. Record absolute error, relative error, invalid-pixel rate, latency, frame alignment, and thermal/battery impact.
5. Test multiple surfaces and objects, indoor/outdoor lighting, static and moving camera, and near/far ranges.
6. Define conservative quality gates and fail-closed behaviour before enabling spoken range.
7. Conduct supervised usability and safety evaluation; do not make collision-avoidance or safe-navigation claims based only on model metrics.

## 9. Next engineering action

Inspect the Android camera characteristics and sensor inventory on RMX3834, then shortlist compact depth models with an available Android runtime/export path and compatible licence. Select a candidate only after checking input/output semantics, metric-scale claims, model size, operator support, and expected RAM use alongside YOLO. The next implementation increment should add a provider behind the metric-depth contract and a device benchmark harness; spoken numeric distance remains disabled until measured validation passes.
