"""Tests for HTTP camera frame capture adapters."""

import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import numpy as np

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.vision.camera import capture_camera_frame


class CameraCaptureTests(unittest.TestCase):
    @patch("ai_guardian.vision.camera.cv2.imdecode")
    @patch("ai_guardian.vision.camera.requests.get")
    def test_http_snapshot_endpoint_decodes_jpeg(self, mock_get, mock_decode):
        response = Mock()
        response.headers = {"Content-Type": "image/jpeg"}
        response.content = b"fake-jpeg"
        mock_get.return_value = response
        expected = np.zeros((10, 10, 3), dtype=np.uint8)
        mock_decode.return_value = expected

        frame = capture_camera_frame("http://192.168.1.10:8080/shot.jpg")

        self.assertIs(frame, expected)
        mock_get.assert_called_once()
        response.close.assert_called_once()

    @patch("ai_guardian.vision.camera.cv2.imdecode")
    @patch("ai_guardian.vision.camera.requests.get")
    def test_http_mjpeg_endpoint_extracts_first_jpeg(self, mock_get, mock_decode):
        response = Mock()
        response.headers = {"Content-Type": "multipart/x-mixed-replace; boundary=frame"}
        response.iter_content.return_value = [b"noise\\xff\\xd8jpeg-frame\\xff\\xd9tail"]
        mock_get.return_value = response
        expected = np.zeros((10, 10, 3), dtype=np.uint8)
        mock_decode.return_value = expected

        frame = capture_camera_frame("http://192.168.1.10:8080/video")

        self.assertIs(frame, expected)
        response.close.assert_called_once()

    def test_invalid_scheme_is_rejected(self):
        with self.assertRaises(ValueError):
            capture_camera_frame("ftp://192.168.1.10/video")


if __name__ == "__main__":
    unittest.main()
