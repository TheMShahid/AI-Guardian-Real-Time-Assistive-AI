# AI Guardian — Depth Estimation and Distance Ranging Architecture

| Attribute | Value |
|---|---|
| Project | AI Guardian — Real-Time Assistive AI |
| Document ID | ARCH-DEPTH-001 |
| Version | 0.3 |
| Status | Device investigation complete; metric-depth sampler implemented; provider candidate selected for compatibility evaluation |
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

### 4.2 Metric3D v2 ViT-S — LiteRT candidate

A community-converted Metric3D v2 ViT-S LiteRT model is the leading candidate for a first on-device compatibility experiment. Its model card describes an absolute metric depth output in metres at 448×448, a 78 MB FP16 model, and Android use through the LiteRT CompiledModel GPU API. The documented input is ImageNet-normalized NCHW RGB; output is a 448×448 depth map in metres. The model card reports approximately 44 ms model inference on a Pixel 8a / Tensor G3, not on the RMX3834.

Important constraints:
- The published latency and accuracy are not transferable to the Unisoc T612 / Mali-G57 handset.
- The conversion is a community export, not an official upstream Android release. It must be independently validated against the upstream model and measured targets.
- The model card documents a canonical-camera focal-length correction (fx / 1000). Camera intrinsics therefore matter for accurate metric output; omitting them may leave systematic scale error.
- The converter author describes Mali-specific fixes to the resize/upsampling path, but that is not proof that LiteRT CompiledModel GPU compilation and execution will work on this exact Mali-G57 driver.
- A 78 MB model is only the weight size. Runtime activations, GPU buffers, YOLO, camera buffers, and app memory add to the actual footprint.
- The model card lists BSD-2-Clause for Metric3D and Apache-2.0 for the DINOv2 backbone; verify the exact model artifact and notices before redistribution.

Decision: test this candidate first only after camera-intrinsics and sensor inventory is recorded. Attempt GPU compilation on the physical phone, capture backend/errors and latency, and fall back to CPU only if measured performance and memory remain acceptable. Do not make Pixel 8a performance claims for RMX3834.

References:
- Metric3D v2 LiteRT model card and Android usage: https://huggingface.co/mlboydaisuke/Metric3D-v2-LiteRT
- LiteRT Android samples: https://github.com/google-ai-edge/litert-samples
- Upstream Metric3D: https://github.com/YvanYin/Metric3D

### 4.3 Depth Anything V2 Metric Small

The official Depth Anything V2 metric-depth implementation provides a 24.8M-parameter Small encoder with separate indoor (Hypersim, up to 20 m) and outdoor (Virtual KITTI 2, up to 80 m) variants. Its Python inference code returns depth in metres. This is a useful independent metric-model baseline, but the official repository documents PyTorch inference rather than a ready-to-run Android LiteRT package. Exporting and validating the model for Android is additional engineering work.

The ordinary Depth Anything V2 Small model and MiDaS Small LiteRT are relative-depth models; their output must not be treated as metres without a separately validated scale recovery method.

Decision: retain as a secondary candidate if Metric3D LiteRT cannot compile or its domain error is unacceptable.

References:
- Official metric-depth implementation: https://github.com/DepthAnything/Depth-Anything-V2/tree/main/metric_depth
- Metric Hypersim Small model: https://huggingface.co/depth-anything/Depth-Anything-V2-Metric-Hypersim-Small
- Metric VKITTI Small model: https://huggingface.co/depth-anything/Depth-Anything-V2-Metric-VKITTI-Small
- MiDaS Small LiteRT output semantics: https://huggingface.co/litert-community/MiDaS-small

### 4.4 Camera geometry and known-size ranging

Camera intrinsics and known physical object dimensions can support range estimation for a known target under calibrated conditions. The current general-purpose object detector does not know the true physical dimensions of arbitrary chairs, people, bags, or vehicles.

Decision: do not use assumed class dimensions as a general-purpose distance solution.

### 4.5 Hardware depth sensors and stereo

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
3. Attempt Metric3D LiteRT GPU compilation on the reference handset and record whether the backend supports all operators.
4. Use measured target distances and a repeatable test protocol. Record absolute error, relative error, invalid-pixel rate, latency, frame alignment, and thermal/battery impact.
5. Test multiple surfaces and objects, indoor/outdoor lighting, static and moving camera, and near/far ranges.
6. Define conservative quality gates and fail-closed behaviour before enabling spoken range.
7. Conduct supervised usability and safety evaluation; do not make collision-avoidance or safe-navigation claims based only on model metrics.

## 9. Next engineering action

Inspect camera IDs, focal lengths, sensor inventory, and GPU/runtime compatibility on RMX3834. Then create a small on-device Metric3D LiteRT smoke-test path, initially outside the live YOLO loop, so model compilation and latency can be measured without destabilizing camera narration. Only after that should depth inference be scheduled alongside YOLO.
