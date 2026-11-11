"""Build concise, prioritized scene descriptions and generate speech audio."""

import asyncio
from collections import Counter
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import List, Optional, Sequence, Tuple

from ai_guardian.vision.detector import Detection


# These labels receive earlier mention because they are especially useful
# for general visual awareness. This is not a hazard classification.
_OBJECT_PRIORITY = {
    "person": 0,
    "car": 1,
    "truck": 1,
    "bus": 1,
    "motorcycle": 1,
    "bicycle": 2,
    "traffic light": 3,
    "stop sign": 3,
    "dog": 4,
    "cat": 4,
}


def _position(box, frame_width: int) -> str:
    x1, _, x2, _ = box
    relative_x = ((x1 + x2) / 2) / max(frame_width, 1)
    if relative_x < 0.35:
        return "left"
    if relative_x > 0.65:
        return "right"
    return "centre"


def _position_phrase(position: str) -> str:
    return {
        "left": "on your left",
        "centre": "in the centre of your view",
        "right": "on your right",
    }[position]


def build_announcement(
    detections: List[Detection],
    frame_width: int,
    tracked_detections: Optional[Sequence[dict]] = None,
) -> str:
    """Create a short spoken summary ordered by general awareness value.

    Positions describe only where objects appear in the camera image. They do
    not indicate physical distance, danger, or collision risk.
    """
    if not detections:
        return "I cannot identify any familiar objects in the camera view."

    grouped = Counter(
        (item.label.replace("_", " ").lower(), _position(item.box, frame_width))
        for item in detections
    )

    ordered = sorted(
        grouped.items(),
        key=lambda entry: (
            _OBJECT_PRIORITY.get(entry[0][0], 10),
            {"centre": 0, "left": 1, "right": 2}[entry[0][1]],
            entry[0][0],
        ),
    )

    # Keep spoken feedback concise. The visual dashboard still shows all detections.
    spoken_groups = ordered[:4]
    descriptions = []
    for (label, position), count in spoken_groups:
        object_name = label if count == 1 else (
            label if label.endswith("s") else label + "s"
        )
        if count > 1:
            object_name = f"{count} {object_name}"
        descriptions.append(f"{object_name} {_position_phrase(position)}")

    if len(descriptions) == 1:
        sentence = f"I can see {descriptions[0]}."
    elif len(descriptions) == 2:
        sentence = f"I can see {descriptions[0]} and {descriptions[1]}."
    else:
        sentence = (
            "I can see " + ", ".join(descriptions[:-1])
            + f", and {descriptions[-1]}."
        )

    remaining = len(ordered) - len(spoken_groups)
    if remaining > 0:
        sentence += f" There are also {remaining} other object groups in view."

    return sentence


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
