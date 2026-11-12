"""Unit tests for long-form speech text segmentation."""

import sys
import unittest
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.audio.announcer import build_announcement, split_text_for_speech
from ai_guardian.vision.detector import Detection, ObjectDetector


class _TensorLike:
    def __init__(self, values):
        self.values = values

    def int(self):
        return self

    def cpu(self):
        return self

    def tolist(self):
        return self.values


class _FakeBoxes:
    def __init__(self):
        self.id = None
        self.cls = _TensorLike([0])
        self.conf = _TensorLike([0.91])
        self.xyxy = _TensorLike([[10, 20, 100, 160]])

    def __len__(self):
        return 1


class _FakeResult:
    boxes = _FakeBoxes()
    names = {0: "person"}

    @staticmethod
    def plot():
        return "annotated-frame"


class _FakeModel:
    @staticmethod
    def track(**kwargs):
        return [_FakeResult()]


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

    def test_detection_is_preserved_when_tracker_has_no_ids_yet(self):
        detector = ObjectDetector.__new__(ObjectDetector)
        detector.confidence = 0.2
        detector.image_size = 416
        detector.model = _FakeModel()

        annotated, detections = detector.track(
            __import__("numpy").zeros((180, 240, 3), dtype="uint8")
        )

        self.assertEqual(annotated, "annotated-frame")
        self.assertEqual(len(detections), 1)
        self.assertEqual(detections[0].label, "person")
        self.assertIsNone(detections[0].track_id)

    def test_scene_announcement_includes_more_than_four_object_groups(self):
        labels = [
            "person", "car", "bicycle", "dog", "chair", "bottle",
            "book", "laptop", "backpack", "cup", "clock", "vase",
        ]
        detections = [
            Detection(label=label, confidence=0.8, box=(20 + i * 10, 20, 60 + i * 10, 80))
            for i, label in enumerate(labels)
        ]
        announcement = build_announcement(detections, frame_width=800)
        self.assertIn("person", announcement)
        self.assertIn("backpack", announcement)
        self.assertIn("2 other object groups", announcement)


if __name__ == "__main__":
    unittest.main()
