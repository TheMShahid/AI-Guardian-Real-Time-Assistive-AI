import base64
import io
import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from PIL import Image

from app.main import _require_api_key, _validate_and_encode_image


def make_png() -> bytes:
    output = io.BytesIO()
    Image.new("RGB", (32, 24), color=(30, 90, 120)).save(output, format="PNG")
    return output.getvalue()


class ImageValidationTests(unittest.TestCase):
    def test_valid_png_is_normalized_to_jpeg_data_url(self):
        data_url = _validate_and_encode_image(make_png(), "image/png")
        prefix, encoded = data_url.split(",", 1)
        self.assertEqual(prefix, "data:image/jpeg;base64")
        decoded = base64.b64decode(encoded)
        with Image.open(io.BytesIO(decoded)) as image:
            self.assertEqual(image.format, "JPEG")
            self.assertEqual(image.size, (32, 24))

    def test_rejects_empty_image(self):
        with self.assertRaises(HTTPException) as error:
            _validate_and_encode_image(b"", "image/png")
        self.assertEqual(error.exception.status_code, 400)

    def test_rejects_unsupported_media_type(self):
        with self.assertRaises(HTTPException) as error:
            _validate_and_encode_image(make_png(), "application/octet-stream")
        self.assertEqual(error.exception.status_code, 415)

    def test_rejects_malformed_image(self):
        with self.assertRaises(HTTPException) as error:
            _validate_and_encode_image(b"not-an-image", "image/png")
        self.assertEqual(error.exception.status_code, 400)


class AuthenticationTests(unittest.TestCase):
    def test_rejects_when_secret_is_not_configured(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(HTTPException) as error:
                _require_api_key("anything")
        self.assertEqual(error.exception.status_code, 503)

    def test_rejects_wrong_key(self):
        with patch.dict(os.environ, {"GUARDIAN_API_KEY": "correct-key"}):
            with self.assertRaises(HTTPException) as error:
                _require_api_key("wrong-key")
        self.assertEqual(error.exception.status_code, 401)

    def test_accepts_matching_key(self):
        with patch.dict(os.environ, {"GUARDIAN_API_KEY": "correct-key"}):
            _require_api_key("correct-key")


if __name__ == "__main__":
    unittest.main()
