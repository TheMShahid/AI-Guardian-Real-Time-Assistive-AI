"""Background processing for continuous network-camera visual awareness."""

import threading
import time

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
        self.detector = ObjectDetector(confidence=confidence)
        self.stop_event = threading.Event()
        self.lock = threading.Lock()
        self.thread = None
        self.latest_frame = None
        self.frame_count = 0
        self.inference_ms = 0.0
        self.last_description = "Waiting for the first camera frame..."
        self.error = ""
        self.running = False
        self.latest_audio = None
        self.latest_audio_mime = "audio/mpeg"
        self.audio_id = 0
        self._last_signature = None
        self._last_spoken_at = 0.0
        self._speech_future = None

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.running = True
        self.thread = threading.Thread(target=self._run, name="ai-guardian-ip-camera", daemon=True)
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self.running = False

    def _run(self):
        try:
            for frame in iter_camera_frames(self.url, self.stop_event, target_fps=5):
                if self.stop_event.is_set():
                    break
                started = time.perf_counter()
                annotated, tracked = self.detector.track(frame)
                latency = (time.perf_counter() - started) * 1000
                detections = [
                    Detection(label=item.label, confidence=item.confidence, box=item.box)
                    for item in tracked
                ]
                description = build_announcement(detections, frame.shape[1], language=self.language)
                signature = tuple(sorted((item.label, round(item.box[0] / max(1, frame.shape[1]) * 5)) for item in tracked))
                now = time.monotonic()

                with self.lock:
                    self.latest_frame = annotated
                    self.frame_count += 1
                    self.inference_ms = latency
                    self.last_description = description

                changed = signature != self._last_signature
                if (self.speech_enabled and tracked and changed and now - self._last_spoken_at >= 2.5):
                    self._last_signature = signature
                    self._last_spoken_at = now
                    self._speech_future = self.speech_executor.submit(
                        text_to_speech_audio, description, self.voice, self.rate
                    )
                if self._speech_future and self._speech_future.done():
                    try:
                        audio, mime, _engine = self._speech_future.result()
                        with self.lock:
                            self.latest_audio = audio
                            self.latest_audio_mime = mime
                            self.audio_id += 1
                    finally:
                        self._speech_future = None
        except Exception as error:
            with self.lock:
                self.error = f"{type(error).__name__}: {error}"
        finally:
            self.running = False

    def status(self):
        with self.lock:
            return {
                "frame": self.latest_frame.copy() if self.latest_frame is not None else None,
                "frames": self.frame_count,
                "inference_ms": self.inference_ms,
                "description": self.last_description,
                "error": self.error,
                "running": self.running,
                "audio": self.latest_audio,
                "audio_mime": self.latest_audio_mime,
                "audio_id": self.audio_id,
            }
