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


def iter_camera_frames(camera_url: str, stop_event, target_fps: float = 5.0):
    """Yield continuous BGR frames from RTSP, HTTP MJPEG, or HTTP JPEG snapshots."""
    url = camera_url.strip()
    interval = 1.0 / max(1.0, target_fps)

    if url.startswith(("rtsp://", "rtsps://")):
        capture = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
        try:
            if not capture.isOpened():
                raise RuntimeError("Could not open RTSP stream. Check URL, credentials, and camera settings.")
            failures = 0
            while not stop_event.is_set():
                ok, frame = capture.read()
                if ok and frame is not None:
                    failures = 0
                    yield frame
                    stop_event.wait(interval)
                else:
                    failures += 1
                    if failures >= 20:
                        raise RuntimeError("RTSP stream stopped delivering frames.")
                    stop_event.wait(0.1)
        finally:
            capture.release()
        return

    if not url.startswith(("http://", "https://")):
        raise ValueError("Use an RTSP/RTSPS URL or an HTTP/HTTPS camera URL.")

    # IP Webcam /video is a multipart MJPEG stream. Keep one HTTP connection open.
    response = requests.get(url, stream=True, timeout=(5, 20))
    try:
        response.raise_for_status()
        content_type = response.headers.get("Content-Type", "").lower()
        if "multipart" in content_type or url.rstrip("/").endswith("/video"):
            buffer = bytearray()
            last_yield = 0.0
            consecutive_bad_frames = 0
            for chunk in response.iter_content(chunk_size=1024):
                if stop_event.is_set():
                    break
                if not chunk:
                    continue
                buffer.extend(chunk)
                while True:
                    # Prefer MIME part boundaries and Content-Length when the
                    # camera supplies them. This prevents cutting a JPEG at a
                    # marker-like byte sequence in the multipart stream.
                    header_end = buffer.find(b"\r\n\r\n")
                    separator_size = 4
                    if header_end < 0:
                        header_end = buffer.find(b"\n\n")
                        separator_size = 2

                    jpeg = None
                    if header_end >= 0:
                        headers = bytes(buffer[:header_end]).lower()
                        length_value = None
                        for header_line in headers.splitlines():
                            if header_line.startswith(b"content-length:"):
                                try:
                                    length_value = int(header_line.split(b":", 1)[1].strip())
                                except ValueError:
                                    length_value = None
                                break
                        if length_value is not None and length_value > 0:
                            body_start = header_end + separator_size
                            body_end = body_start + length_value
                            if len(buffer) < body_end:
                                break
                            jpeg = bytes(buffer[body_start:body_end])
                            del buffer[:body_end]
                    if jpeg is None:
                        # Compatibility fallback for MJPEG servers that omit
                        # Content-Length. Keep partial JPEG data until EOI.
                        start = buffer.find(b"\xff\xd8")
                        end = buffer.find(b"\xff\xd9", start + 2) if start >= 0 else -1
                        if start < 0 or end < 0:
                            if len(buffer) > 12 * 1024 * 1024:
                                del buffer[:-2]
                            break
                        jpeg = bytes(buffer[start:end + 2])
                        del buffer[:end + 2]

                    if not jpeg.startswith(b"\xff\xd8") or not jpeg.endswith(b"\xff\xd9"):
                        consecutive_bad_frames += 1
                        if consecutive_bad_frames >= 30:
                            raise RuntimeError("The MJPEG camera repeatedly sent incomplete JPEG frames.")
                        continue

                    now = time.monotonic()
                    if now - last_yield < interval:
                        continue
                    frame = cv2.imdecode(np.frombuffer(jpeg, dtype=np.uint8), cv2.IMREAD_COLOR)
                    if frame is None:
                        consecutive_bad_frames += 1
                        if consecutive_bad_frames >= 30:
                            raise RuntimeError("The MJPEG camera repeatedly sent JPEG frames that could not be decoded.")
                        continue
                    consecutive_bad_frames = 0
                    last_yield = now
                    yield frame
            if not stop_event.is_set():
                raise RuntimeError("The MJPEG connection ended. Check that the phone camera server is still running.")
        else:
            # Release the initial probe response before polling /shot.jpg.
            response.close()
            # A /shot.jpg endpoint is polled at a modest rate.
            while not stop_event.is_set():
                try:
                    shot = requests.get(url, timeout=(3, 5))
                    shot.raise_for_status()
                    frame = cv2.imdecode(np.frombuffer(shot.content, dtype=np.uint8), cv2.IMREAD_COLOR)
                    shot.close()
                    if frame is None:
                        raise RuntimeError("The camera snapshot endpoint returned an invalid image.")
                    yield frame
                except requests.RequestException as error:
                    raise RuntimeError(f"Could not read the camera snapshot: {error}") from error
                stop_event.wait(interval)
    finally:
        response.close()
