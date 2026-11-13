"""Camera-source adapters for local-network IP cameras."""

import time

import cv2
import numpy as np
import requests


def capture_camera_frame(camera_url: str):
    """Capture one BGR frame from RTSP/RTSPS or an HTTP JPEG/MJPEG endpoint."""
    url = camera_url.strip()
    if url.startswith(("rtsp://", "rtsps://")):
        capture = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
        try:
            if not capture.isOpened():
                raise RuntimeError(
                    "Could not open the RTSP stream. Check the URL, credentials, "
                    "and camera RTSP settings."
                )
            for _ in range(5):
                success, frame = capture.read()
                if success and frame is not None:
                    return frame
                time.sleep(0.2)
            raise RuntimeError("RTSP connected, but no video frame was received.")
        finally:
            capture.release()

    if not url.startswith(("http://", "https://")):
        raise ValueError("Enter an RTSP/RTSPS URL or an HTTP/HTTPS camera image/video URL.")

    response = requests.get(url, stream=True, timeout=(5, 15))
    try:
        response.raise_for_status()
        content_type = response.headers.get("Content-Type", "").lower()

        # A still-image endpoint such as IP Webcam /shot.jpg.
        if "image/" in content_type and "multipart" not in content_type:
            image_bytes = response.content
            frame = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
            if frame is None:
                raise RuntimeError("The HTTP endpoint responded, but its image could not be decoded.")
            return frame

        # MJPEG endpoint such as IP Webcam /video: extract the first JPEG frame.
        buffer = bytearray()
        for chunk in response.iter_content(chunk_size=4096):
            if not chunk:
                continue
            buffer.extend(chunk)
            start = buffer.find(b"\xff\xd8")
            end = buffer.find(b"\xff\xd9", start + 2) if start >= 0 else -1
            if start >= 0 and end >= 0:
                jpeg_bytes = bytes(buffer[start:end + 2])
                frame = cv2.imdecode(
                    np.frombuffer(jpeg_bytes, dtype=np.uint8), cv2.IMREAD_COLOR
                )
                if frame is not None:
                    return frame
                raise RuntimeError("The HTTP MJPEG stream returned an invalid JPEG frame.")
            if len(buffer) > 8 * 1024 * 1024:
                raise RuntimeError("No complete JPEG frame received within the 8 MB read limit.")

        raise RuntimeError(
            "The HTTP camera endpoint connected but did not provide a JPEG frame. "
            "Try the camera's /shot.jpg snapshot URL or /video MJPEG URL."
        )
    finally:
        response.close()
