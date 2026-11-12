"""Unit tests for long-form speech text segmentation."""

import sys
import unittest
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.audio.announcer import split_text_for_speech


class SpeechSegmentationTests(unittest.TestCase):
    def test_empty_text_is_rejected(self):
        with self.assertRaises(ValueError):
            split_text_for_speech("   ")

    def test_short_text_stays_in_one_segment(self):
        text = "A person is standing near the door."
        self.assertEqual(split_text_for_speech(text), [text])

    def test_long_text_is_split_within_limit(self):
        text = " ".join(
            ["A person is standing near the door."] * 20
        )
        segments = split_text_for_speech(text, max_chars=100)
        self.assertGreater(len(segments), 1)
        self.assertTrue(all(0 < len(part) <= 100 for part in segments))

    def test_urdu_and_pashto_punctuation_are_supported(self):
        text = "یہ ایک جملہ ہے۔ یہ دوسرا جملہ ہے۔ سلام! دا دویمه جمله ده."
        segments = split_text_for_speech(text, max_chars=100)
        self.assertGreaterEqual(len(segments), 1)
        self.assertTrue(all(part.strip() for part in segments))


if __name__ == "__main__":
    unittest.main()
