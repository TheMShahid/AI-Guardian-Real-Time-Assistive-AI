"""AI Guardian: live detection, object tracking, and spoken scene awareness."""

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
from ai_guardian.vision.detector import Detection, ObjectDetector


st.set_page_config(page_title="AI Guardian", page_icon="👁️", layout="wide")
st.title("AI Guardian")
st.subheader("Real-Time Assistive Visual Intelligence")
st.write("Live object detection, object tracking, movement awareness, and spoken feedback.")

st.warning(
    "Safety limitation: this is an experimental awareness aid. Detection and tracking "
    "can fail. Left/right movement describes motion in the camera image, not physical "
    "distance, approach, collision risk, or a verified safe path. Never rely on this "
    "prototype alone for mobility or road crossing."
)

with st.sidebar:
    st.header("Detection settings")
    confidence = st.slider(
        "Detection confidence", min_value=0.10, max_value=0.80,
        value=0.25, step=0.05,
        help="Lower values detect more candidates but can add false detections.",
    )
    speech_enabled = st.toggle("Enable live voice announcements", value=True)
    st.caption("Announcements include all detected categories and are rate-limited.")


@st.cache_resource
def load_detector(confidence_value: float):
    return ObjectDetector(confidence=confidence_value)


class LiveObjectDetector(VideoProcessorBase):
    """Per-camera YOLO/ByteTrack processor with thread-safe UI status."""

    def __init__(self):
        # A separate detector/tracker belongs to this WebRTC stream.
        self.detector = ObjectDetector(confidence=confidence)
        self.lock = threading.Lock()
        self.frame_count = 0
        self.last_frame_time = 0.0
        self.last_labels = []
        self.last_tracks = []
        self.last_announcement = ""
        self.announcement_id = 0
        self.last_announcement_time = 0.0
        self.last_signature = None
        self.previous_centres = {}
        self.last_seen = {}
        self.error = ""

    def recv(self, frame):
        image = frame.to_ndarray(format="bgr24")
        try:
            annotated, tracked = self.detector.track(image)
            now = time.monotonic()
            frame_width = image.shape[1]
            current_ids = set()
            track_rows = []
            detections = []

            for item in tracked:
                x1, y1, x2, y2 = item.box
                centre_x = (x1 + x2) / 2
                previous = self.previous_centres.get(item.track_id)
                motion = "stationary"
                if previous is not None:
                    delta = centre_x - previous
                    threshold = max(8.0, frame_width * 0.015)
                    if delta <= -threshold:
                        motion = "moving left"
                    elif delta >= threshold:
                        motion = "moving right"

                self.previous_centres[item.track_id] = centre_x
                self.last_seen[item.track_id] = now
                current_ids.add(item.track_id)

                detections.append(
                    Detection(label=item.label, confidence=item.confidence, box=item.box)
                )
                track_rows.append({
                    "track_id": item.track_id,
                    "label": item.label,
                    "confidence": round(item.confidence, 3),
                    "box": item.box,
                    "motion": motion,
                })

            # Discard stale tracker history to keep memory bounded.
            stale_ids = [
                track_id for track_id, seen_at in self.last_seen.items()
                if now - seen_at > 3.0
            ]
            for track_id in stale_ids:
                self.last_seen.pop(track_id, None)
                self.previous_centres.pop(track_id, None)

            signature = tuple(sorted(
                (item.label, "left" if (item.box[0] + item.box[2]) / 2 < frame_width * 0.35
                 else "right" if (item.box[0] + item.box[2]) / 2 > frame_width * 0.65
                 else "centre")
                for item in tracked
            ))
            moving_tracks = [row for row in track_rows if row["motion"] != "stationary"]

            with self.lock:
                self.frame_count += 1
                self.last_frame_time = now
                self.last_labels = [
                    {"label": item.label, "confidence": round(item.confidence, 3), "box": item.box}
                    for item in detections
                ]
                self.last_tracks = track_rows
                self.error = ""

                scene_changed = signature != self.last_signature
                cooldown_finished = now - self.last_announcement_time >= 8.0
                if speech_enabled and tracked and scene_changed and cooldown_finished:
                    self.last_announcement = build_announcement(
                        detections, frame_width, moving_tracks
                    )
                    self.announcement_id += 1
                    self.last_announcement_time = now
                    self.last_signature = signature

            return av.VideoFrame.from_ndarray(annotated, format="bgr24")

        except Exception as error:
            with self.lock:
                self.error = f"{type(error).__name__}: {error}"
            error_frame = image.copy()
            cv2.putText(
                error_frame, "AI Guardian error - see Live status",
                (12, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2,
            )
            return av.VideoFrame.from_ndarray(error_frame, format="bgr24")

    def get_status(self):
        with self.lock:
            return {
                "frame_count": self.frame_count,
                "last_frame_time": self.last_frame_time,
                "labels": list(self.last_labels),
                "tracks": list(self.last_tracks),
                "announcement": self.last_announcement,
                "announcement_id": self.announcement_id,
                "error": self.error,
            }


st.markdown("## Feature 1 — Live camera object detection")
st.caption("Press START and allow camera access. YOLO bounding boxes are drawn on the live stream.")

ctx = webrtc_streamer(
    key="ai-guardian-live-camera-v3",
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

    metric1, metric2, metric3, metric4 = st.columns(4)
    metric1.metric("Processed frames", status["frame_count"])
    metric2.metric(
        "Camera processing",
        "Receiving frames" if age is not None and age < 3 else "Waiting for frames",
    )
    metric3.metric("Objects", len(status["labels"]))
    metric4.metric("Tracked IDs", len(status["tracks"]))

    if status["error"]:
        st.error("Detection/tracking pipeline error: " + status["error"])
    elif age is not None and age >= 3:
        st.warning("No new camera frame for 3 seconds. Check camera/WebRTC connection.")
    elif status["frame_count"] > 0:
        st.success("Live frames are reaching YOLO and ByteTrack.")

    if status["tracks"]:
        st.markdown("#### Object tracking")
        st.dataframe(
            [
                {
                    "Track ID": row["track_id"],
                    "Object": row["label"],
                    "Confidence": f'{row["confidence"]:.0%}',
                    "Image movement": row["motion"],
                    "Bounding box": str(row["box"]),
                }
                for row in status["tracks"]
            ],
            use_container_width=True,
            hide_index=True,
        )
    else:
        st.caption("No track IDs yet. Keep the object visible for a few frames.")

    if speech_enabled and status["announcement_id"] > st.session_state.get(
        "last_live_audio_id", 0
    ):
        st.session_state["last_live_audio_id"] = status["announcement_id"]
        st.markdown("#### Spoken scene summary")
        st.write(status["announcement"])
        try:
            audio = text_to_speech_audio(status["announcement"])
            st.audio(audio, format="audio/wav", autoplay=True)
            st.caption("If audio does not start automatically, press Play. Some browsers block autoplay.")
        except Exception as error:
            st.warning(f"Speech generation failed: {error}")


live_status_panel()

st.divider()
st.markdown("## Feature 2 — Capture and speak a scene")
st.caption("Use this separate snapshot workflow for an on-demand spoken summary.")

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
