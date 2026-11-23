# Calibrated Visual Distance Estimation

Status: experimental engineering foundation; not yet enabled in live spoken guidance.

## Why calibration is required

A single RGB camera does not directly measure metric depth. Bounding-box size alone cannot tell whether a small box is a nearby backpack or a large object farther away. The estimator therefore refuses to return metres or step counts until a user has supplied a calibration.

## Model

The current prototype uses a pinhole-camera approximation:

`f / H = (h / H) * Z / S`

where:
- `f / H` is focal length normalized by frame height,
- `h / H` is the detected object's normalized box height,
- `Z` is a manually measured reference distance,
- `S` is the manually known physical height of the reference object.

For later detections:

`Z_est = (f / H) * S_class / (h / H)`

The implementation uses approximate class-height priors for selected COCO classes (person, chair, backpack, car, etc.). It returns a broad ±35% range, not a precise point value.

## Step-count conversion

Step bands are calculated only when a valid user stride length (0.20–1.50 m) is supplied. The range is rounded upward to whole steps. This is a rough walking-distance conversion, not a prediction of how many steps remain before collision. Actual steps vary with gait, terrain, pace, turns and obstacle geometry.

## Device and session calibration constraints

A calibration is valid only for the camera, zoom, resolution and image pipeline used to create it. Recalibration is required after camera/zoom changes or significant pipeline changes. The current implementation stores calibration as a value object; persistence, an accessible calibration workflow, live camera metadata validation and device-specific verification are still required before user-facing activation.

## Safety constraints

- Never speak an exact distance or exact step count from an uncalibrated RGB frame.
- Unsupported object classes produce no metric estimate.
- Bounding boxes may include background or only part of an object.
- Typical class heights vary substantially (especially people, chairs, bags and vehicles).
- The estimator must not be used as a collision detector or a guarantee that a route is clear.
- Before live use, validate against measured distances at multiple ranges, lighting conditions, camera orientations and object instances on the target phone.
- Do not test while walking near roads, stairs or other hazards. Conduct stationary indoor tests with a helper.

## Implementation

- `VisualDistanceEstimator.kt`: calibration, class-size priors, range estimation and optional stride conversion.
- `VisualDistanceEstimatorTest.kt`: geometry, uncalibrated refusal, unsupported classes and invalid stride tests.

The next engineering increment is a screen-reader-accessible calibration flow that captures a stable reference detection, asks the user to enter its measured distance and physical height, stores the camera-specific calibration, and exposes a clearly labelled experimental distance range in the live UI.
