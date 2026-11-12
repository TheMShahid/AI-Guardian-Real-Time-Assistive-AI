"""Build concise, prioritized scene descriptions and generate speech audio."""

import asyncio
import re
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


def _position_phrase(position: str, language: str = "en") -> str:
    phrases = {
        "en": {
            "left": "on your left",
            "centre": "in the centre of your view",
            "right": "on your right",
        },
        "ur": {
            "left": "آپ کے بائیں جانب",
            "centre": "آپ کے سامنے درمیان میں",
            "right": "آپ کے دائیں جانب",
        },
        "ps": {
            "left": "ستاسو کیڼ لوري ته",
            "centre": "ستاسو د لید په منځ کې",
            "right": "ستاسو ښي لوري ته",
        },
    }
    return phrases.get(language, phrases["en"])[position]


_OBJECT_NAMES = {
    "ur": {
        "person": "شخص", "people": "لوگ", "car": "گاڑی", "truck": "ٹرک",
        "bus": "بس", "motorcycle": "موٹر سائیکل", "bicycle": "سائیکل",
        "traffic light": "ٹریفک لائٹ", "stop sign": "اسٹاپ کا نشان",
        "dog": "کتا", "cat": "بلی", "chair": "کرسی", "bottle": "بوتل",
        "cup": "کپ", "book": "کتاب", "cell phone": "موبائل فون",
        "laptop": "لیپ ٹاپ", "backpack": "بیگ", "handbag": "ہینڈ بیگ",
        "door": "دروازہ", "bench": "بینچ", "table": "میز",
    },
    "ps": {
        "person": "کس", "people": "خلک", "car": "موټر", "truck": "لارۍ",
        "bus": "بس", "motorcycle": "موټرسایکل", "bicycle": "بایسکل",
        "traffic light": "د ترافیک څراغ", "stop sign": "د تم کېدو نښه",
        "dog": "سپی", "cat": "پیشو", "chair": "څوکۍ", "bottle": "بوتل",
        "cup": "پیاله", "book": "کتاب", "cell phone": "موبایل",
        "laptop": "لېپټاپ", "backpack": "بکس", "handbag": "لاسي بکس",
        "door": "دروازه", "bench": "چوکی", "table": "مېز",
    },
}


def build_announcement(
    detections: List[Detection],
    frame_width: int,
    tracked_detections: Optional[Sequence[dict]] = None,
    language: str = "en",
) -> str:
    """Create a concise scene summary in English, Urdu, or Pashto.

    Object positions refer only to the camera image. They do not indicate
    physical distance, danger, or collision risk.
    """
    language = language if language in ("en", "ur", "ps") else "en"
    if not detections:
        return {
            "en": "I cannot identify any familiar objects in the camera view.",
            "ur": "کیمرے کی تصویر میں کوئی جانی پہچانی چیز نظر نہیں آئی۔",
            "ps": "په کمره کې کوم پېژندل شوی شی ونه پېژندل شو.",
        }[language]

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

    spoken_groups = ordered[:4]
    descriptions = []
    for (label, position), count in spoken_groups:
        translated_label = _OBJECT_NAMES.get(language, {}).get(label, label)
        if count > 1:
            translated_label = f"{count} {translated_label}"
        if language == "en":
            descriptions.append(f"{translated_label} {_position_phrase(position, language)}")
        elif language == "ur":
            descriptions.append(f"{_position_phrase(position, language)} {translated_label}")
        else:
            descriptions.append(f"{_position_phrase(position, language)} {translated_label}")

    if language == "ur":
        sentence = "میں دیکھ رہا ہوں: " + "، ".join(descriptions) + "۔"
    elif language == "ps":
        sentence = "زه وینم: " + "، ".join(descriptions) + "."
    elif len(descriptions) == 1:
        sentence = f"I can see {descriptions[0]}."
    elif len(descriptions) == 2:
        sentence = f"I can see {descriptions[0]} and {descriptions[1]}."
    else:
        sentence = "I can see " + ", ".join(descriptions[:-1]) + f", and {descriptions[-1]}."

    remaining = len(ordered) - len(spoken_groups)
    if remaining > 0:
        sentence += (
            f" مزید {remaining} اقسام کی چیزیں بھی نظر آ رہی ہیں۔" if language == "ur"
            else f" نور {remaining} ډوله شیان هم ښکاري." if language == "ps"
            else f" There are also {remaining} other object groups in view."
        )
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
        # The Windows/system fallback is often English-only. Do not silently
        # read Urdu or Pashto text with an unrelated English voice.
        if voice.startswith(("ur-", "ps-")):
            raise RuntimeError(
                f"Neural speech failed for voice '{voice}'. "
                "Urdu/Pashto require the online language-specific voice. "
                f"Details: {neural_error}"
            ) from neural_error
        try:
            audio = _offline_tts_audio(text.strip())
            return audio, "audio/wav", "Offline system voice (fallback)"
        except Exception as offline_error:
            raise RuntimeError(
                "Both speech engines failed. Neural voice error: "
                f"{neural_error}. Offline voice error: {offline_error}"
            ) from offline_error


def split_text_for_speech(text: str, max_chars: int = 280) -> List[str]:
    """Split long OCR text into sentence-aware, TTS-safe segments."""
    if not text or not text.strip():
        raise ValueError("Speech text cannot be empty.")
    if max_chars < 80:
        raise ValueError("max_chars must be at least 80.")

    normalized = re.sub(r"[ \\t]+", " ", text).strip()
    sentences = [
        part.strip()
        for part in re.split(r"(?<=[.!?؟۔])\\s+|\\n+", normalized)
        if part.strip()
    ]
    segments: List[str] = []
    current = ""

    for sentence in sentences:
        # Split unusually long sentences at word boundaries.
        pieces = []
        remaining = sentence
        while len(remaining) > max_chars:
            split_at = remaining.rfind(" ", 0, max_chars + 1)
            if split_at <= 0:
                split_at = max_chars
            pieces.append(remaining[:split_at].strip())
            remaining = remaining[split_at:].strip()
        if remaining:
            pieces.append(remaining)

        for piece in pieces:
            candidate = f"{current} {piece}".strip()
            if current and len(candidate) > max_chars:
                segments.append(current)
                current = piece
            else:
                current = candidate

    if current:
        segments.append(current)
    return segments


def text_to_speech_segments(
    text: str,
    voice: str = "en-US-AriaNeural",
    rate: str = "-5%",
    max_chars: int = 280,
) -> List[Tuple[bytes, str, str]]:
    """Generate independently playable audio segments for complete long-form reading.

    Each segment is generated in order. The UI can play them sequentially,
    avoiding oversized single TTS requests and truncated long-form playback.
    """
    text_segments = split_text_for_speech(text, max_chars=max_chars)
    audio_segments: List[Tuple[bytes, str, str]] = []
    for index, segment in enumerate(text_segments, start=1):
        try:
            audio_segments.append(text_to_speech_audio(segment, voice, rate))
        except Exception as error:
            raise RuntimeError(
                f"Speech generation failed at segment {index} of "
                f"{len(text_segments)}: {error}"
            ) from error
    return audio_segments
