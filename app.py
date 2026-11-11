"""AI Guardian: live YOLO detection with a Streamlit monitoring UI and speech."""

import sys
import threading
import time
from pathlib import Path

import av
import cv2
import streamlit as st
from streamlit_webrtc import WebRtcMode, VideoProcessorBase, webrtc_streamer

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.audio.announcer import build_announcement, text_to_speech_audio
from ai_guardian.vision.detector import ObjectDetector


st.set_page_config(page_title="AI Guardian", page_icon="👁️", layout="wide")
st.title("AI Guardian")
st.subheader("Real-Time Assistive Visual Intelligence")
st.write("Live camera detection, a live scene monitor, and controlled spoken announcements.")

st.warning(
    "Safety limitation: this is an experimental awareness aid. Detection can miss "
    "objects. Left/centre/right is image position, not physical distance or a safe "
    "path. Never rely on this prototype alone for mobility or road crossing."
)

with st.sidebar:
    st.header("Detection settings")
    confidence = st.slider(
        "Detection confidence",
        min_value=0.10,
        max_value=0.80,
        value=0.25,
        step=0.05,
        help="Lower values detect more candidates but can add false detections.",
    )
    speech_enabled = st.toggle("Enable live voice announcements", value=True)
    st.caption("Voice is rate-limited to avoid repeating the same scene continuously.")


@st.cache_resource
def load_detector(confidence_value: float):
    return ObjectDetector(confidence=confidence_value)


class LiveObjectDetector(VideoProcessorBase):
    """Thread-safe live processor with status data for the Streamlit UI."""

    def __init__(self):
        self.detector = ObjectDetector(confidence=confidence)
        self.lock = threading.Lock()
        self.frame_count = 0
        self.last_frame_time = 0.0
        self.last_labels = []
        self.last_announcement = ""
        self.announcement_id = 0
        self.last_announcement_time = 0.0
        self.last_signature = None
        self.error = ""

    def recv(self, frame):
        image = frame.to_ndarray(format="bgr24")
        try:
            annotated, detections = self.detector.detect(image)
            labels = [
                {
                    "label": item.label,
                    "confidence": round(item.confidence, 3),
                    "box": item.box,
                }
                for item in detections
            ]

            now = time.monotonic()
            signature = tuple(sorted(
                (item.label, "left" if (item.box[0] + item.box[2]) / 2 < image.shape[1] * 0.35
                 else "right" if (item.box[0] + item.box[2]) / 2 > image.shape[1] * 0.65
                 else "centre")
                for item in detections
            ))

            with self.lock:
                self.frame_count += 1
                self.last_frame_time = now
                self.last_labels = labels
                self.error = ""

                if (
                    speech_enabled
                    and detections
                    and signature != self.last_signature
                    and now - self.last_announcement_time >= 7.0
                ):
                    self.last_announcement = build_announcement(detections, image.shape[1])
                    self.announcement_id += 1
                    self.last_announcement_time = now
                    self.last_signature = signature

            return av.VideoFrame.from_ndarray(annotated, format="bgr24")

        except Exception as error:
            with self.lock:
                self.error = f"{type(error).__name__}: {error}"
            error_frame = image.copy()
            cv2.putText(
                error_frame,
                "AI Guardian detection error - see Live status below",
                (12, 30),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                (0, 0, 255),
                2,
            )
            return av.VideoFrame.from_ndarray(error_frame, format="bgr24")

    def get_status(self):
        with self.lock:
            return {
                "frame_count": self.frame_count,
                "last_frame_time": self.last_frame_time,
                "labels": list(self.last_labels),
                "announcement": self.last_announcement,
                "announcement_id": self.announcement_id,
                "error": self.error,
            }


st.markdown("## Feature 1 — Live camera object detection")
st.caption(
    "Press START below and allow browser camera access. Detection boxes should be "
    "drawn directly on the returning video stream."
)

ctx = webrtc_streamer(
    key="ai-guardian-live-camera-v2",
    mode=WebRtcMode.SENDRECV,
    video_processor_factory=LiveObjectDetector,
    media_stream_constraints={"video": True, "audio": False},
    async_processing=True,
    desired_playing_state=True,
)

st.markdown("### Live status")


@st.fragment(run_every="1s")
def live_status_panel():
    processor = ctx.video_processor

    if processor is None:
        st.info("Camera is not running yet. Press START in the video panel.")
        return

    status = processor.get_status()
    age = time.monotonic() - status["last_frame_time"] if status["last_frame_time"] else None

    metric1, metric2, metric3 = st.columns(3)
    metric1.metric("Processed frames", status["frame_count"])
    metric2.metric(
        "Camera processing",
        "Receiving frames" if age is not None and age < 3 else "Waiting for frames",
    )
    metric3.metric("Objects in latest frame", len(status["labels"]))

    if status["error"]:
        st.error("Detection pipeline error: " + status["error"])
    elif age is not None and age >= 3:
        st.warning("No new camera frame has arrived for 3 seconds. Check camera/WebRTC connection.")
    elif status["frame_count"] > 0:
        st.success("Live frames are reaching the YOLO detector.")

    if status["labels"]:
        st.dataframe(
            [
                {
                    "Object": item["label"],
                    "Confidence": f'{item["confidence"]:.0%}',
                    "Bounding box (x1, y1, x2, y2)": str(item["box"]),
                }
                for item in status["labels"]
            ],
            use_container_width=True,
            hide_index=True,
        )
    else:
        st.caption("No objects detected in the latest processed frame.")

    if speech_enabled and status["announcement_id"] > st.session_state.get(
        "last_live_audio_id", 0
    ):
        st.session_state["last_live_audio_id"] = status["announcement_id"]
        st.markdown("### Live voice announcement")
        st.write(status["announcement"])
        try:
            audio = text_to_speech_audio(status["announcement"])
            st.audio(audio, format="audio/wav", autoplay=True)
        except Exception as error:
            st.warning(f"Speech generation failed: {error}")
            st.caption("The detected scene is still available in the live status above.")


live_status_panel()

st.divider()
st.markdown("## Feature 2 — Capture and speak a scene")
st.caption(
    "Use this separate snapshot workflow when you want to request an immediate "
    "spoken summary. Live announcements above are automatic and rate-limited."
)

camera_image = st.camera_input("Capture a still image")
if camera_image is not None and st.button("Analyze captured image", type="primary"):
    try:
        import numpy as np
        from PIL import Image
        image = np.array(Image.open(camera_image).convert("RGB"))
        bgr_image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
        detector = load_detector(confidence)
        annotated, detections = detector.detect(bgr_image)
        announcement = build_announcement(detections, bgr_image.shape[1])

        left, right = st.columns(2)
        with left:
            st.image(image, caption="Captured image", use_container_width=True)
        with right:
            st.image(annotated, channels="BGR", caption="Detection result", use_container_width=True)

        st.write(announcement)
        audio = text_to_speech_audio(announcement)
        st.audio(audio, format="audio/wav")
    except Exception as error:
        st.error(f"Snapshot analysis failed: {error}")
        st.exception(error)
