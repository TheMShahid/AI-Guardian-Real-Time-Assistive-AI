# Cloud VLM Scene Understanding

## Status

The target architecture is cloud-first for all large vision-language models (VLMs). The Android app must not download or bundle VLM weights. The FastAPI backend is the trusted inference gateway and routes requests to a configured provider/model.

The repository already contains:
- A FastAPI endpoint at `POST /v1/scene/describe`.
- Hugging Face Inference Providers integration in `backend/app/main.py`.
- Server-side provider credentials through `HF_TOKEN`.
- Server-side model selection through `GUARDIAN_VLM_MODEL`.
- API-key authentication through `GUARDIAN_API_KEY`.
- Image validation, JPEG normalization, upload-size limits, request IDs, timeout handling, and stateless processing.
- An Android HTTPS multipart client in `CloudVlmSceneAnalyzer.kt`.

The Android cloud client is an initial transport component. It is not yet wired into the camera screen or user settings. Backend deployment and Android endpoint/client authentication configuration are still required before end-to-end cloud inference works.

## Target architecture

- Android CameraX captures a snapshot only after an explicit scene-description action.
- Local YOLO remains active for continuous object detection and immediate obstacle alerts.
- The Android app compresses and uploads one JPEG snapshot to the AI Guardian FastAPI backend over HTTPS.
- The backend authenticates the client, validates the image, and invokes the configured cloud VLM provider.
- The backend returns a concise description and request/model metadata.
- Android local Text-to-Speech speaks the response in the selected language.
- VLM inference is on-demand or rate-limited, never one request per camera frame.
- Network calls never block or suspend local YOLO obstacle alerts.

## Model routing

The app must not contain provider-specific model IDs, Hugging Face tokens, OpenAI keys, or other provider secrets.

The backend owns provider credentials and model selection. Configure a model only after checking:
- Image-chat / vision input support through the selected inference provider.
- Provider availability and account entitlement.
- Model licence and permitted use.
- Maximum image dimensions, token limits, timeout, rate limits, and cost.
- Expected response latency and reliability.

Changing the configured backend model must not require a new Android APK, provided the response contract remains compatible.

## Android transport

`CloudVlmSceneAnalyzer` sends a JPEG multipart request to `/v1/scene/describe`, sets `X-API-Key`, requires HTTPS, enforces the current 8 MiB client upload ceiling, parses the JSON response, and surfaces HTTP failures without exposing provider credentials.

The client currently accepts the backend URL and client API key through constructor arguments. A follow-up integration must provide a secure configuration mechanism and connect it to the camera screen. Do not hardcode secrets into Kotlin source, Gradle BuildConfig fields, the APK, or Git.

The current shared API-key prototype is suitable only for controlled development. A production mobile deployment should use per-user/device authentication or short-lived tokens, rate limiting, abuse controls, key rotation, and auditable consent. A static shared secret embedded in an APK can be extracted.

## Backend configuration

See `backend/README.md`.

Required server-side configuration:
- `HF_TOKEN`
- `GUARDIAN_VLM_MODEL`
- `GUARDIAN_API_KEY`

The backend should be deployed to a stable HTTPS endpoint. Store secrets in the hosting provider's secret manager, never in Git.

## Privacy and consent

- Ask for explicit user consent before the first cloud image upload and provide an always-accessible cloud analysis control.
- Do not upload continuous video or background frames by default.
- Explain that the selected camera snapshot is transmitted to a remote service for inference.
- The backend currently processes images in memory and does not write them to disk.
- Do not log image bytes, base64 payloads, or image contents.
- A deployment must disclose the hosting provider, inference provider, retention policies, and applicable privacy terms.
- Users must be able to disable cloud analysis.
- If the network is unavailable, preserve local YOLO and local TTS behavior; do not make obstacle alerts wait for cloud inference.

## Separation from metric distance

A VLM scene description is not a metric depth sensor. It may describe objects and relative spatial layout, but it must not invent distances in metres.

Metric distance requires a separate depth-estimation service/provider with a model that explicitly produces metric depth, plus camera/input metadata, calibration assumptions, validation data, and a measured error envelope. Do not parse natural-language VLM output as a distance measurement.

Until a metric-depth endpoint is implemented and validated:
- Keep numeric distance announcements disabled.
- Do not infer distance from YOLO box size or VLM wording.
- Do not describe any route as safe.
- Keep immediate obstacle alerts local and independent of cloud availability.

## Local VLM deprecation

The previous Android implementation downloaded `SmolVLM2-500M.litertlm` (approximately 361 MB) into app-private storage and executed it with LiteRT-LM. This path conflicts with the cloud-first target and should be removed from the normal app flow once cloud endpoint configuration and consent UI are integrated and tested.

Do not delete the legacy local classes or model asset-management code until the cloud path has passed end-to-end tests and a deliberate migration commit removes the old UI and unused dependencies.

## Validation checklist

1. Deploy the FastAPI backend to HTTPS and configure provider secrets in hosting settings.
2. Verify `/healthz` and `/readyz`.
3. Verify authentication rejects missing and invalid client credentials.
4. Verify invalid image types, oversized uploads, malformed images, provider timeouts, and provider failures.
5. Wire the Android cloud client to user-configured backend endpoint and secure client authentication.
6. Obtain consent before uploading a camera snapshot.
7. Verify cloud scene description works over Wi-Fi and mobile data.
8. Verify local YOLO alerts continue while a cloud request is pending, fails, or times out.
9. Verify response language, speech queue priority, and cancellation when the camera session ends.
10. Confirm uploaded images are not retained or logged by the backend or hosting/inference provider beyond disclosed policies.
11. Remove the local 361 MB VLM download workflow only after the cloud path is verified.

## Safety constraints

Cloud VLM output is informational, not a mobility instruction or guarantee. Models can hallucinate, omit hazards, misidentify objects, and misunderstand spatial relationships. Keep spoken descriptions concise and uncertain when evidence is weak.

The system must not replace a white cane, guide dog, or orientation-and-mobility training.
