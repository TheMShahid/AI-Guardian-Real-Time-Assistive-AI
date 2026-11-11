"""Build complete, concise scene descriptions and render speech audio."""

from collections import Counter
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import List, Optional, Sequence

import pyttsx3

from ai_guardian.vision.detector import Detection, TrackedDetection


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
    """Describe every detected object category and useful tracked movement.

    Repeated objects are grouped by label and image position; no fixed five-object
    truncation is applied. Position and movement refer only to the camera image.
    """
    if not detections:
        return "I cannot identify any familiar objects in the current camera view."

    grouped = Counter(
        (item.label, _position(item.box, frame_width)) for item in detections
    )
    descriptions = []
    for (label, position), count in sorted(grouped.items()):
        noun = label.replace("_", " ")
        if count == 1:
            descriptions.append(f"{noun} on the {position}")
        else:
            plural = noun if noun.endswith("s") else noun + "s"
            descriptions.append(f"{count} {plural} on the {position}")

    if len(descriptions) == 1:
        scene = "I can see " + descriptions[0] + "."
    else:
        scene = "I can see " + "; ".join(descriptions) + "."

    moving = []
    for item in tracked_detections or ():
        motion = item.get("motion", "stationary")
        if motion in ("moving left", "moving right"):
            moving.append(
                f'{item["label"]} number {item["track_id"]} is moving {motion.split()[-1]} in the camera view'
            )

    if moving:
        scene += " Movement: " + "; ".join(moving) + "."
    return scene


def text_to_speech_audio(text: str) -> bytes:
    """Render the complete announcement to WAV using the installed system voice."""
    if not text or not text.strip():
        raise ValueError("Speech text cannot be empty.")

    with TemporaryDirectory() as temp_dir:
        audio_path = Path(temp_dir) / "announcement.wav"
        engine = pyttsx3.init()
        engine.setProperty("rate", 145)
        engine.setProperty("volume", 1.0)
        engine.save_to_file(text, str(audio_path))
        engine.runAndWait()
        engine.stop()

        if not audio_path.exists() or audio_path.stat().st_size == 0:
            raise RuntimeError(
                "Text-to-speech did not produce audio. Check the installed system voice."
            )
        return audio_path.read_bytes()
