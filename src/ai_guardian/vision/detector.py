"""YOLO-based object detection for live camera frames."""

from dataclasses import dataclass
from typing import List, Tuple

import cv2
from ultralytics import YOLO


@dataclass(frozen=True)
class Detection:
    """A detected object and its location in the input frame."""

    label: str
    confidence: float
    box: Tuple[int, int, int, int]


class ObjectDetector:
    """Run YOLO inference on individual OpenCV frames."""

    def __init__(
        self,
        model_name: str = "yolov8n.pt",
        confidence: float = 0.35,
        image_size: int = 640,
    ) -> None:
        if not 0.0 < confidence <= 1.0:
            raise ValueError("confidence must be greater than 0 and at most 1")

        self.model = YOLO(model_name)
        self.confidence = confidence
        self.image_size = image_size

    def detect(self, frame) -> Tuple[object, List[Detection]]:
        """Return an annotated frame and structured detections."""
        if frame is None or not hasattr(frame, "shape"):
            raise ValueError("A valid OpenCV image frame is required.")

        if len(frame.shape) != 3 or frame.shape[2] != 3:
            raise ValueError("Expected a colour frame with three channels.")

        result = self.model.predict(
            source=frame,
            conf=self.confidence,
            imgsz=self.image_size,
            verbose=False,
        )[0]

        detections: List[Detection] = []

        if result.boxes is not None:
            for box in result.boxes:
                class_id = int(box.cls[0].item())
                confidence = float(box.conf[0].item())
                x1, y1, x2, y2 = box.xyxy[0].cpu().tolist()

                detections.append(
                    Detection(
                        label=result.names[class_id],
                        confidence=confidence,
                        box=(int(x1), int(y1), int(x2), int(y2)),
                    )
                )

        annotated_frame = result.plot()
        return annotated_frame, detections
