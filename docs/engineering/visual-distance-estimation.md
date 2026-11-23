# Calibrated Visual Distance Estimation

Status: experimental; connected to live spoken obstacle announcements after explicit user calibration. Not validated for navigation or collision avoidance.

## Why calibration is required

A single RGB camera does not directly measure metric depth. Bounding-box size alone cannot distinguish a small nearby object from a large distant object. The estimator therefore refuses to return metres or steps until a camera-specific calibration and, for steps, a measured stride length are supplied.

## Model

The prototype uses a pinhole-camera approximation:

`f / H = (h / H) * Z / S`

where:
- `f / H` is focal length normalized by frame height,
- `h / H` is the detected object's normalized bounding-box height,
- `Z` is the manually measured reference distance,
- `S` is the manually measured physical height of the reference object.

For later detections:

`Z_est = (f / H) * S_class / (h / H)`

The estimator uses approximate class-height priors for selected COCO classes and returns a broad ±35% interval. This interval is an engineering heuristic, not a statistical confidence interval.

## Guided calibration workflow

1. Start the live camera and keep the phone at its normal camera zoom.
2. Ask a helper to position one supported, fully visible reference object at a measured distance from the camera.
3. Open the camera overflow menu and select **Capture stable distance calibration sample**. The app starts a short capture session and evaluates five consecutive detector frames (approximately two seconds at the current analysis cadence). It selects the highest-confidence supported detection in each frame and requires a consistent class, stable normalized bounding-box height (within 10% of the median), stable normalized center position, and no long frame gaps. The accepted normalized box height and center are medians across the sample window. Only geometry, class label and capture time are retained; no image is saved.
4. Keep the reference object stationary and fully visible until the voice confirmation. If it moves, changes apparent size substantially, disappears for too long, or a different class takes over, the sample window restarts.
5. Open Settings → Distance Estimation.
6. Confirm the captured label. Enter the reference object's actual measured physical height and the measured camera-to-object distance. The captured normalized box ratio is shown automatically.
7. Optionally enter a measured average stride length. Leave it blank if it has not been measured; step announcements remain disabled.
8. Save calibration. Samples older than five minutes are rejected and must be captured again.

The user must isolate one reference object during capture. If multiple supported objects are visible, the highest-confidence detection may not be the intended reference; verify the displayed class label before saving.

## Step-count conversion

Step bands are calculated only when a valid user stride length (0.20–1.50 m) is supplied. The range is rounded upward to whole steps. This is a rough walking-distance conversion, not a prediction of how many steps remain before collision. Actual steps vary with gait, terrain, pace, turns and obstacle geometry.

## Device and session constraints

A calibration is specific to the camera, zoom, resolution and image pipeline used to create it. Recalibration is required after camera/zoom changes or significant pipeline changes. Calibration and stride preferences are stored locally in SharedPreferences. The live obstacle voice pipeline appends a broad distance interval for supported classes; step ranges are appended only when a measured stride is available.

## Safety constraints

- Never speak a metric distance from an uncalibrated RGB frame.
- Unsupported object classes produce no metric estimate.
- Bounding boxes may include background or only part of an object.
- Typical class heights vary substantially, especially people, chairs, bags and vehicles.
- The ±35% range does not capture every source of error and must not be treated as a safety guarantee.
- Do not use the estimator as a collision detector or proof that a route is clear.
- Validate against measured distances at multiple ranges, lighting conditions, camera orientations and object instances on the target phone.
- Do not test while walking near roads, stairs or other hazards. Conduct stationary indoor tests with a helper.

## Implementation

- `VisualDistanceEstimator.kt`: calibration, class-size priors, range estimation and optional stride conversion.
- `DistanceCalibrationCapture.kt`: geometry-only live samples plus a five-frame stability tracker.
- `DistanceCalibrationCaptureTest.kt`: sample normalization, median aggregation, class consistency, motion, size stability and stale-frame-gap tests.
- `DistanceCalibrationStore.kt`: local persistence for calibration and stride settings.
- `DistanceSpeechFormatter.kt`: localized range and optional step-band formatting.
- `DistanceSpeechFormatterTest.kt`: localized formatting and refusal to speak unavailable measurements.
- `SpeechLocalizerDistanceTest.kt`: calibrated and uncalibrated obstacle voice wording.
