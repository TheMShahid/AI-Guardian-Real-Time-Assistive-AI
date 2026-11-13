"""Background processing for continuous network-camera visual awareness."""

import threading
import time
from itertools import chain

from ai_guardian.audio.announcer import build_announcement, text_to_speech_audio
from ai_guardian.vision.camera import iter_camera_frames
from ai_guardian.vision.detector import Detection, ObjectDetector


class NetworkCameraWorker:
    """Reads network-camera frames, runs YOLO, and generates speech off the UI thread."""

    def __init__(self, url, confidence, language, voice, rate, speech_enabled, speech_executor):
        self.url = url
        self.language = language
        self.voice = voice
        self.rate = rate
        self.speech_enabled = speech_enabled
        self.speech_executor = speech_executor
        self.confidence = confidence
        self.detector = None
        self.stop_event = threading.Event()
        self.lock = threading.Lock()
        self.thread = None
        self.latest_frame = None
        self.frame_count = 0
        self.inference_ms = 0.0
        self.model_load_seconds = 0.0
        self.camera_connected = False
        self.last_description = "Connecting to the Wi-Fi camera..."
        self.error = ""
        self.running = False
        self.latest_audio = None
        self.latest_audio_mime = "audio/mpeg"
        self.audio_id = 0
        self._last_signature = None
        self._last_spoken_at = 0.0
        self._speech_future = None
        self._speech_retry_after = 0.0
        self.speech_error = ""

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.stop_event.clear()
        self.running = True
        self.thread = threading.Thread(target=self._run, name="ai-guardian-ip-camera", daemon=True)
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self.running = False

    def _run(self):
        frame_iterator = None
        try:
            # Connect and display the first camera frame BEFORE loading YOLO.
            # This removes model startup time from the user's perceived camera
            # connection time and gives immediate visual feedback.
            frame_iterator = iter_camera_frames(self.url, self.stop_event, target_fps=3)
            first_frame = next(frame_iterator)
            with self.lock:
                self.latest_frame = first_frame.copy()
                self.camera_connected = True
                self.last_description = "Camera connected. Loading AI object detector..."

            if self.stop_event.is_set():
                return

            model_started = time.perf_counter()
            self.detector = ObjectDetector(confidence=self.confidence, image_size=320)
            with self.lock:
                self.model_load_seconds = time.perf_counter() - model_started

            for frame in chain((first_frame,), frame_iterator):
                if self.stop_event.is_set():
                    break

                started = time.perf_counter()
                annotated, tracked = self.detector.track(frame)
                latency = (time.perf_counter() - started) * 1000
                detections = [
                    Detection(label=item.label, confidence=item.confidence, box=item.box)
                    for item in tracked
                ]
                description = build_announcement(
                    detections, frame.shape[1], language=self.language
                )

                # Build a stable scene signature from object type, broad image
                # region, and count. This avoids announcements caused by box jitter.
                scene_groups = {}
                for item in tracked:
                    centre_x = (item.box[0] + item.box[2]) / 2
                    region = "left" if centre_x < frame.shape[1] * 0.35 else (
                        "right" if centre_x > frame.shape[1] * 0.65 else "centre"
                    )
                    key = (item.label.lower().replace("_", " "), region)
                    scene_groups[key] = scene_groups.get(key, 0) + 1
                signature = tuple(sorted(
                    (label, region, count)
                    for (label, region), count in scene_groups.items()
                ))
                now = time.monotonic()

                with self.lock:
                    # Always keep the newest annotated frame; no frame queue is
                    # allowed to build up behind slow inference or speech.
                    self.latest_frame = annotated
                    self.frame_count += 1
                    self.inference_ms = latency
                    self.last_description = description

                changed = signature != self._last_signature
                periodic_recap_due = now - self._last_spoken_at >= 12.0
                retry_due = now >= self._speech_retry_after
                should_speak = (
                    self.speech_enabled
                    and bool(tracked)
                    and (changed or periodic_recap_due or bool(self.speech_error))
                    and retry_due
                    and self._speech_future is None
                )
                if should_speak:
                    self._last_signature = signature
                    self._last_spoken_at = now
                    self._speech_future = self.speech_executor.submit(
                        text_to_speech_audio, description, self.voice, self.rate
                    )

                # Poll speech asynchronously. A TTS outage must never stop the
                # camera loop, and a failed scene can be retried after cooldown.
                if self._speech_future and self._speech_future.done():
                    try:
                        audio, mime, _engine = self._speech_future.result()
                        with self.lock:
                            self.latest_audio = audio
                            self.latest_audio_mime = mime
                            self.audio_id += 1
                            self.speech_error = ""
                        self._speech_retry_after = 0.0
                    except Exception as speech_error:
                        with self.lock:
                            self.speech_error = f"{type(speech_error).__name__}: {speech_error}"
                        self._speech_retry_after = time.monotonic() + 8.0
                    finally:
                        self._speech_future = None
        except StopIteration:
            if not self.stop_event.is_set():
                with self.lock:
                    self.error = "The camera connected but did not provide a video frame."
        except Exception as error:
            with self.lock:
                self.error = f"{type(error).__name__}: {error}"
        finally:
            if frame_iterator is not None:
                close = getattr(frame_iterator, "close", None)
                if close:
                    close()
            self.running = False

    def status(self):
        with self.lock:
            return {
                "frame": self.latest_frame.copy() if self.latest_frame is not None else None,
                "frames": self.frame_count,
                "inference_ms": self.inference_ms,
                "model_load_seconds": self.model_load_seconds,
                "camera_connected": self.camera_connected,
                "description": self.last_description,
                "error": self.error,
                "running": self.running,
                "audio": self.latest_audio,
                "audio_mime": self.latest_audio_mime,
                "audio_id": self.audio_id,
                "speech_error": self.speech_error,
            }
