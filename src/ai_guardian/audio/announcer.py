"""Build listener-friendly scene descriptions and generate natural speech audio."""

import asyncio
from collections import Counter
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import List, Optional, Sequence, Tuple

from ai_guardian.vision.detector import Detection


def _position(box, frame_width: int) -> str:
    x1, _, x2, _ = box
    relative_x = ((x1 + x2) / 2) / max(frame_width, 1)
    if relative_x < 0.35:
        return "left"
    if relative_x > 0.65:
        return "right"
    return "centre"


def build_announcement(
    detections: List[Detection],
    frame_width: int,
    tracked_detections: Optional[Sequence[dict]] = None,
) -> str:
    """Create a short, structured spoken summary without a fixed object limit."""
    if not detections:
        return "I cannot identify any familiar objects in the camera view."

    grouped = Counter(
        (item.label.replace("_", " "), _position(item.box, frame_width))
        for item in detections
    )
    parts = []
    for (label, position), count in sorted(grouped.items()):
        if count == 1:
            parts.append(f"{label} to the {position}")
        else:
            plural = label if label.endswith("s") else label + "s"
            parts.append(f"{count} {plural} to the {position}")

    if len(parts) == 1:
        summary = f"I can see {parts[0]}."
    elif len(parts) == 2:
        summary = f"I can see {parts[0]} and {parts[1]}."
    else:
        summary = "I can see " + ", ".join(parts[:-1]) + f", and {parts[-1]}."

    moving = []
    seen_tracks = set()
    for item in tracked_detections or ():
        motion = item.get("motion", "stationary")
        track_id = item.get("track_id")
        if motion in ("moving left", "moving right") and track_id not in seen_tracks:
            seen_tracks.add(track_id)
            moving.append(f"a {item['label']} is moving {motion.split()[-1]} across the camera view")

    if moving:
        summary += " Movement in the camera view: " + "; ".join(moving) + "."
    return summary


def _edge_tts_audio(text: str, voice: str, rate: str) -> bytes:
    """Generate natural neural speech using Microsoft's online Edge TTS service."""
    import edge_tts

    async def generate() -> bytes:
        communicate = edge_tts.Communicate(text, voice, rate=rate)
        chunks = []
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                chunks.append(chunk["data"])
        return b"".join(chunks)

    return asyncio.run(generate())


def _offline_tts_audio(text: str) -> bytes:
    """Fallback to the locally installed system voice."""
    import pyttsx3

    with TemporaryDirectory() as temp_dir:
        audio_path = Path(temp_dir) / "announcement.wav"
        engine = pyttsx3.init()
        engine.setProperty("rate", 155)
        engine.setProperty("volume", 1.0)
        engine.save_to_file(text, str(audio_path))
        engine.runAndWait()
        engine.stop()

        if not audio_path.exists() or audio_path.stat().st_size == 0:
            raise RuntimeError("The offline speech engine did not create an audio file.")
        return audio_path.read_bytes()


def text_to_speech_audio(
    text: str,
    voice: str = "en-US-AriaNeural",
    rate: str = "-5%",
) -> Tuple[bytes, str, str]:
    """Return audio bytes, MIME type, and engine label.

    Neural speech is attempted first. If internet/service access fails, the
    installed offline voice is used automatically.
    """
    if not text or not text.strip():
        raise ValueError("Speech text cannot be empty.")

    try:
        audio = _edge_tts_audio(text.strip(), voice, rate)
        if audio:
            return audio, "audio/mpeg", "Natural neural voice"
        raise RuntimeError("The neural speech service returned empty audio.")
    except Exception as neural_error:
        try:
            audio = _offline_tts_audio(text.strip())
            return audio, "audio/wav", "Offline system voice (fallback)"
        except Exception as offline_error:
            raise RuntimeError(
                "Both speech engines failed. Neural voice error: "
                f"{neural_error}. Offline voice error: {offline_error}"
            ) from offline_error
