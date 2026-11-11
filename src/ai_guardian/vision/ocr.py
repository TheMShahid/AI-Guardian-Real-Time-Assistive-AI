"""Printed-text OCR for English, Urdu, and Pashto camera snapshots."""

from __future__ import annotations

import cv2
import numpy as np


_TESSERACT_LANGUAGES = {
    "en": "eng",
    "ur": "urd",
    "ps": "pus",
}


def read_text_from_image(image_rgb: np.ndarray, language: str = "en") -> str:
    """Extract printed text from an RGB image using installed Tesseract data.

    Tesseract itself and the selected traineddata file must be installed on
    the host. OCR is best-effort and may be inaccurate for handwriting,
    decorative fonts, blur, glare, and complex Nastaliq layouts.
    """
    if language not in _TESSERACT_LANGUAGES:
        raise ValueError("Unsupported OCR language. Choose English, Urdu, or Pashto.")
    if image_rgb is None or image_rgb.size == 0:
        raise ValueError("The captured image is empty.")

    try:
        import pytesseract
    except ImportError as error:
        raise RuntimeError(
            "The Python OCR package is missing. Install project requirements."
        ) from error

    gray = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2GRAY)
    height, width = gray.shape[:2]
    if width < 1400:
        scale = min(2.0, 1400 / max(width, 1))
        gray = cv2.resize(
            gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC
        )
    processed = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY, 31, 11,
    )

    try:
        text = pytesseract.image_to_string(
            processed,
            lang=_TESSERACT_LANGUAGES[language],
            config="--oem 1 --psm 6",
        )
    except pytesseract.pytesseract.TesseractNotFoundError as error:
        raise RuntimeError(
            "Tesseract OCR is not installed or is not on PATH. Install Tesseract 5 and restart the terminal."
        ) from error
    except pytesseract.TesseractError as error:
        raise RuntimeError(
            f"Tesseract could not load language data '{_TESSERACT_LANGUAGES[language]}'. "
            "Install the matching language pack (eng, urd, or pus)."
        ) from error

    return "\n".join(line.strip() for line in text.splitlines() if line.strip())
