from __future__ import annotations

import base64
import io
import os
import secrets
from functools import lru_cache
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from huggingface_hub import InferenceClient
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

APP_VERSION = "0.1.0"
MAX_IMAGE_BYTES = int(os.getenv("MAX_IMAGE_BYTES", str(8 * 1024 * 1024)))
INFERENCE_TIMEOUT_SECONDS = float(os.getenv("INFERENCE_TIMEOUT_SECONDS", "45"))
ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGE_SIDE = 4096

app = FastAPI(
    title="AI Guardian Cloud API",
    version=APP_VERSION,
    docs_url=None,
    redoc_url=None,
)


class SceneDescriptionResponse(BaseModel):
    request_id: str
    description: str
    model: str
    processing_mode: str = "cloud_vlm"


def _require_api_key(provided_key: str | None) -> None:
    expected = os.getenv("GUARDIAN_API_KEY")
    if not expected:
        raise HTTPException(
            status_code=503,
            detail="Cloud API authentication is not configured.",
        )
    if not provided_key or not secrets.compare_digest(provided_key, expected):
        raise HTTPException(status_code=401, detail="Invalid API key.")


def _validate_and_encode_image(raw: bytes, content_type: str | None) -> str:
    if not raw:
        raise HTTPException(status_code=400, detail="The uploaded image is empty.")
    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="The uploaded image exceeds the size limit.")
    if content_type not in ALLOWED_IMAGE_TYPES:
        raise HTTPException(status_code=415, detail="Use a JPEG, PNG, or WebP image.")

    try:
        with Image.open(io.BytesIO(raw)) as image:
            image.verify()
        with Image.open(io.BytesIO(raw)) as image:
            if image.width > MAX_IMAGE_SIDE or image.height > MAX_IMAGE_SIDE:
                raise HTTPException(status_code=413, detail="Image dimensions exceed the limit.")
            image = image.convert("RGB")
            output = io.BytesIO()
            image.save(output, format="JPEG", quality=85, optimize=True)
            encoded = base64.b64encode(output.getvalue()).decode("ascii")
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, ValueError):
        raise HTTPException(status_code=400, detail="The uploaded file is not a valid image.")

    return f"data:image/jpeg;base64,{encoded}"


@lru_cache(maxsize=1)
def _inference_client() -> InferenceClient:
    token = os.getenv("HF_TOKEN")
    if not token:
        raise RuntimeError("HF_TOKEN is not configured.")
    return InferenceClient(token=token, timeout=INFERENCE_TIMEOUT_SECONDS)


def _call_vlm(model: str, image_data_url: str) -> str:
    client = _inference_client()
    result: Any = client.chat_completion(
        model=model,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "Describe the visible scene for a blind or low-vision user "
                            "in one or two concise sentences. Mention only clearly visible "
                            "objects and their approximate positions in the image. Do not "
                            "infer physical distances, walking directions, or whether a "
                            "path is safe. State uncertainty rather than guessing."
                        ),
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": image_data_url},
                    },
                ],
            }
        ],
        max_tokens=120,
        temperature=0.2,
    )

    message = result.choices[0].message
    text = message.content
    if isinstance(text, list):
        text = " ".join(
            item.get("text", "") for item in text if isinstance(item, dict)
        )
    if not isinstance(text, str) or not text.strip():
        raise RuntimeError("The inference provider returned an empty description.")
    return text.strip()


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok", "service": "ai-guardian-cloud-api", "version": APP_VERSION}


@app.get("/readyz")
async def readyz() -> dict[str, Any]:
    model = os.getenv("GUARDIAN_VLM_MODEL")
    token_configured = bool(os.getenv("HF_TOKEN"))
    api_key_configured = bool(os.getenv("GUARDIAN_API_KEY"))
    ready = bool(model and token_configured and api_key_configured)
    return {
        "ready": ready,
        "model_configured": bool(model),
        "provider_token_configured": token_configured,
        "api_auth_configured": api_key_configured,
    }


@app.post("/v1/scene/describe", response_model=SceneDescriptionResponse)
async def describe_scene(
    image: UploadFile = File(...),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> SceneDescriptionResponse:
    _require_api_key(x_api_key)

    model = os.getenv("GUARDIAN_VLM_MODEL")
    if not model:
        raise HTTPException(status_code=503, detail="No cloud vision model is configured.")
    if not os.getenv("HF_TOKEN"):
        raise HTTPException(status_code=503, detail="The inference provider is not configured.")

    request_id = str(uuid4())
    image_bytes = await image.read(MAX_IMAGE_BYTES + 1)
    image_data_url = _validate_and_encode_image(image_bytes, image.content_type)

    try:
        description = await run_in_threadpool(_call_vlm, model, image_data_url)
    except TimeoutError:
        raise HTTPException(status_code=504, detail="Cloud scene analysis timed out.")
    except Exception:
        # Provider details and uploaded content are deliberately excluded from client errors.
        raise HTTPException(
            status_code=502,
            detail=f"Cloud scene analysis failed. Reference: {request_id}",
        )

    return SceneDescriptionResponse(
        request_id=request_id,
        description=description,
        model=model,
    )
