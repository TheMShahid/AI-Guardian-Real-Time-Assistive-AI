"""Create concise spoken summaries of detected objects."""

from pathlib import Path
from tempfile import TemporaryDirectory
from typing import List

import pyttsx3

from ai_guardian.vision.detector import Detection


def build_announcement(detections: List[Detection], frame_width: int) -> str:
    """Describe object labels and horizontal image position without guessing distance."""
    if not detections:
        return "I could not identify any familiar objects. Please try pointing the camera again."

    descriptions = []
    for item in detections:
        x1, _, x2, _ = item.box
        center_x = (x1 + x2) / 2
        relative_x = center_x / max(frame_width, 1)

        if relative_x < 0.35:
            position = "on the left"
        elif relative_x > 0.65:
            position = "on the right"
        else:
            position = "near the center"

        descriptions.append(f"{item.label} {position}")

    # Keep the first few detections to make announcements short and usable.
    descriptions = descriptions[:5]
    if len(descriptions) == 1:
        return f"I can see {descriptions[0]}."

    return "I can see " + ", ".join(descriptions[:-1]) + f", and {descriptions[-1]}."


def text_to_speech_audio(text: str) -> bytes:
    """Render speech to a WAV byte string using the computer's installed voice."""
    with TemporaryDirectory() as temp_dir:
        audio_path = Path(temp_dir) / "announcement.wav"
        engine = pyttsx3.init()
        engine.setProperty("rate", 165)
        engine.save_to_file(text, str(audio_path))
        engine.runAndWait()
        engine.stop()

        if not audio_path.exists() or audio_path.stat().st_size == 0:
            raise RuntimeError(
                "Text-to-speech did not produce audio. Check that a system voice is installed."
            )

        return audio_path.read_bytes()
