# AI Guardian Cloud API

A small, stateless FastAPI service for optional, user-requested cloud scene understanding. The Android app remains responsible for camera capture, local object detection, immediate alerts, and offline behavior.

## Endpoints

- `GET /healthz` — liveness check; does not call an inference provider.
- `GET /readyz` — configuration readiness.
- `POST /v1/scene/describe` — accepts one JPEG/PNG/WebP image as multipart field `image` and returns a concise scene description.

## Configuration

| Variable | Required | Purpose |
|---|---:|---|
| `HF_TOKEN` | For inference | Hugging Face token with permission to use the selected inference provider/model |
| `GUARDIAN_VLM_MODEL` | For inference | Explicitly selected vision-language model supported by the account's Inference Providers |
| `GUARDIAN_API_KEY` | Production | Shared API key expected in `X-API-Key`; do not commit it |
| `MAX_IMAGE_BYTES` | No | Upload limit; defaults to 8 MiB |
| `INFERENCE_TIMEOUT_SECONDS` | No | Provider timeout; defaults to 45 seconds |

The service intentionally does not guess a model ID. The Android LiteRT-LM `.litertlm` asset is an Android runtime artifact and is not loaded by this Python service. Configure a cloud model only after confirming its image-chat support, license, provider availability, and cost.

## Local run

From this directory:

```bash
python -m venv .venv
# Activate the virtual environment, then:
pip install -r requirements.txt
export HF_TOKEN=...
export GUARDIAN_VLM_MODEL=...
export GUARDIAN_API_KEY=...
uvicorn app.main:app --host 0.0.0.0 --port 7860
```

On Windows PowerShell, use `$env:NAME="value"` instead of `export`.

## Hugging Face Docker Space

Create a **Docker** Space and point it at this directory (or configure the repository root build context as appropriate). Add secrets in the Space settings, not in Git:

- `HF_TOKEN`
- `GUARDIAN_VLM_MODEL`
- `GUARDIAN_API_KEY`

The container listens on port 7860. CPU/GPU hardware and provider availability affect latency and cost. No cloud deployment is performed by a repository commit.

## Privacy and safety

- The API is stateless and does not write uploaded images to disk.
- The caller must obtain explicit user consent before uploading a camera frame.
- Do not send continuous video or background frames by default.
- Scene descriptions are informational, not mobility instructions.
- This endpoint does not calculate metric distance, certify a clear path, or replace a white cane, guide dog, or orientation-and-mobility training.
- Production deployment must use HTTPS and a strong API key. Rotate keys if exposed.
