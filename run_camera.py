"""Start AI Guardian's first live-camera object detection feature."""

import argparse
import sys
from pathlib import Path

import cv2

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.vision.detector import ObjectDetector  # noqa: E402


def parse_args():
    parser = argparse.ArgumentParser(
        description="AI Guardian live camera object detection"
    )
    parser.add_argument("--camera", type=int, default=0, help="Camera device index")
    parser.add_argument(
        "--confidence",
        type=float,
        default=0.35,
        help="Minimum detection confidence (0 to 1)",
    )
    parser.add_argument(
        "--model",
        default="yolov8n.pt",
        help="Ultralytics model name or local model path",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    detector = ObjectDetector(
        model_name=args.model,
        confidence=args.confidence,
    )

    camera = cv2.VideoCapture(args.camera)
    if not camera.isOpened():
        raise RuntimeError(
            f"Could not open camera {args.camera}. Check the camera connection "
            "or try another --camera index."
        )

    print("AI Guardian camera is running. Press 'q' to quit.")

    try:
        while True:
            success, frame = camera.read()
            if not success:
                print("Warning: camera frame could not be read.")
                break

            annotated_frame, detections = detector.detect(frame)

            print(
                f"Detected {len(detections)} object(s): "
                + ", ".join(
                    f"{item.label} ({item.confidence:.2f})"
                    for item in detections
                ),
                end="\r",
                flush=True,
            )

            cv2.imshow("AI Guardian - Live Object Detection", annotated_frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break
    finally:
        camera.release()
        cv2.destroyAllWindows()
        print("\nCamera stopped.")


if __name__ == "__main__":
    main()
