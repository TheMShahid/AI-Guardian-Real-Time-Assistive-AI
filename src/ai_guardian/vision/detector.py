"""YOLO-based object detection and persistent object tracking."""

from dataclasses import dataclass
from typing import List, Tuple


@dataclass(frozen=True)
class Detection:
    """A detected object and its location in the input frame."""

    label: str
    confidence: float
    box: Tuple[int, int, int, int]


@dataclass(frozen=True)
class TrackedDetection(Detection):
    """A detection associated with a persistent tracker ID."""

    track_id: int


class ObjectDetector:
    """Run YOLO inference and ByteTrack association on camera frames."""

    def __init__(
        self,
        model_name: str = "yolov8n.pt",
        confidence: float = 0.35,
        image_size: int = 416,
    ) -> None:
        if not 0.0 < confidence <= 1.0:
            raise ValueError("confidence must be greater than 0 and at most 1")
        if image_size < 320 or image_size > 1280:
            raise ValueError("image_size must be between 320 and 1280")

        # Lazy import keeps Streamlit's initial page load lighter.
        from ultralytics import YOLO

        self.model = YOLO(model_name)
        self.confidence = confidence
        self.image_size = image_size

        # Fuse Conv/BatchNorm layers where supported for faster inference.
        try:
            self.model.fuse()
        except (AttributeError, RuntimeError):
            pass

    @staticmethod
    def _validate_frame(frame) -> None:
        if frame is None or not hasattr(frame, "shape"):
            raise ValueError("A valid OpenCV image frame is required.")
        if len(frame.shape) != 3 or frame.shape[2] != 3:
            raise ValueError("Expected a colour frame with three channels.")

    def detect(self, frame) -> Tuple[object, List[Detection]]:
        """Return an annotated frame and detections without temporal tracking."""
        self._validate_frame(frame)
        result = self.model.predict(
            source=frame,
            conf=self.confidence,
            imgsz=self.image_size,
            max_det=50,
            verbose=False,
        )[0]

        detections: List[Detection] = []
        if result.boxes is not None:
            for box in result.boxes:
                class_id = int(box.cls[0].item())
                score = float(box.conf[0].item())
                x1, y1, x2, y2 = box.xyxy[0].cpu().tolist()
                detections.append(
                    Detection(
                        label=result.names[class_id],
                        confidence=score,
                        box=(int(x1), int(y1), int(x2), int(y2)),
                    )
                )

        return result.plot(), detections

    def track(self, frame) -> Tuple[object, List[TrackedDetection]]:
        """Detect objects and associate them across frames using ByteTrack."""
        self._validate_frame(frame)
        result = self.model.track(
            source=frame,
            persist=True,
            tracker="bytetrack.yaml",
            conf=self.confidence,
            imgsz=self.image_size,
            max_det=50,
            verbose=False,
        )[0]

        tracked: List[TrackedDetection] = []
        boxes = result.boxes
        if boxes is not None and boxes.id is not None:
            track_ids = boxes.id.int().cpu().tolist()
            class_ids = boxes.cls.int().cpu().tolist()
            scores = boxes.conf.cpu().tolist()
            coordinates = boxes.xyxy.int().cpu().tolist()

            for track_id, class_id, score, coords in zip(
                track_ids, class_ids, scores, coordinates
            ):
                x1, y1, x2, y2 = coords
                tracked.append(
                    TrackedDetection(
                        track_id=int(track_id),
                        label=result.names[int(class_id)],
                        confidence=float(score),
                        box=(int(x1), int(y1), int(x2), int(y2)),
                    )
                )

        return result.plot(), tracked
