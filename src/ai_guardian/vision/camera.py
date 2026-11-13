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


def _iter_mjpeg_response(response, stop_event, interval):
    """Decode complete multipart JPEG parts from one persistent HTTP response."""
    buffer = bytearray()
    last_yield = 0.0
    consecutive_bad_frames = 0

    for chunk in response.iter_content(chunk_size=512):
        if stop_event.is_set():
            return
        if not chunk:
            continue
        buffer.extend(chunk)

        while not stop_event.is_set():
            header_end = buffer.find(b"\\r\\n\\r\\n")
            separator_size = 4
            if header_end < 0:
                header_end = buffer.find(b"\\n\\n")
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
                start = buffer.find(b"\\xff\\xd8")
                end = buffer.find(b"\\xff\\xd9", start + 2) if start >= 0 else -1
                if start < 0 or end < 0:
                    if len(buffer) > 12 * 1024 * 1024:
                        del buffer[:-2]
                    break
                jpeg = bytes(buffer[start:end + 2])
                del buffer[:end + 2]

            if not jpeg.startswith(b"\\xff\\xd8") or not jpeg.endswith(b"\\xff\\xd9"):
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
        raise requests.ConnectionError("The MJPEG stream ended before the camera was stopped.")


def iter_camera_frames(camera_url: str, stop_event, target_fps: float = 5.0):
    """Yield continuous frames, reconnecting automatically after transient network failures."""
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

    retry_delay = 0.5
    while not stop_event.is_set():
        response = None
        try:
            # A bounded read timeout detects dead sockets; the outer loop then
            # reconnects instead of terminating the entire camera worker.
            response = requests.get(url, stream=True, timeout=(4, 8))
            response.raise_for_status()
            content_type = response.headers.get("Content-Type", "").lower()

            if "multipart" in content_type or url.rstrip("/").endswith("/video"):
                received_frame = False
                for frame in _iter_mjpeg_response(response, stop_event, interval):
                    received_frame = True
                    retry_delay = 0.5
                    yield frame
                if not stop_event.is_set() and not received_frame:
                    raise requests.ConnectionError("The MJPEG stream closed without sending a frame.")
            else:
                image_bytes = response.content
                frame = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
                if frame is None:
                    raise RuntimeError("The camera snapshot endpoint returned an invalid image.")
                retry_delay = 0.5
                yield frame
                stop_event.wait(interval)

        except requests.HTTPError:
            # Invalid URLs, authentication failures, and missing endpoints need
            # user correction; retrying them forever would hide the real issue.
            raise
        except (requests.RequestException, RuntimeError) as error:
            if stop_event.is_set():
                break
            # Retry transient disconnects/timeouts with bounded exponential
            # backoff. The camera worker remains alive while Wi-Fi recovers.
            stop_event.wait(retry_delay)
            retry_delay = min(retry_delay * 2, 5.0)
        finally:
            if response is not None:
                response.close()
