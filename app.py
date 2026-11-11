"""AI Guardian: accessible live visual awareness with improved speech feedback."""

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


st.set_page_config(
    page_title="AI Guardian | Visual Assistant",
    page_icon="👁️",
    layout="wide",
    initial_sidebar_state="expanded",
)

st.markdown(
    """
    <style>
    .block-container {max-width: 1440px; padding-top: 1.4rem; padding-bottom: 3rem;}
    h1 {font-size: 2.35rem !important; letter-spacing: -0.03em;}
    h2 {font-size: 1.45rem !important;}
    h3 {font-size: 1.12rem !important;}
    [data-testid="stMetric"] {
        background: rgba(120, 140, 160, 0.10);
        border: 1px solid rgba(120, 140, 160, 0.25);
        padding: 0.8rem 1rem; border-radius: 0.8rem;
    }
    [data-testid="stButton"] button {min-height: 2.8rem; font-weight: 600;}
    [data-testid="stAudio"] {width: 100%;}
    .ag-subtitle {font-size: 1.05rem; opacity: .82; margin-top: -.5rem;}
    .ag-note {font-size: .9rem; opacity: .78;}
    </style>
    """,
    unsafe_allow_html=True,
)

st.title("👁️ AI Guardian")
st.markdown(
    '<div class="ag-subtitle">Real-time visual awareness and voice assistance</div>',
    unsafe_allow_html=True,
)
st.write("Understand selected objects and their positions through your camera and spoken feedback.")

with st.sidebar:
    st.header("⚙️ Assistant settings")
    confidence = st.slider(
        "Detection sensitivity",
        min_value=0.10,
        max_value=0.80,
        value=0.25,
        step=0.05,
        help="Lower values may detect more objects but can increase false detections.",
    )
    speech_enabled = st.toggle("Live voice announcements", value=True)
    voice_labels = {
        "en-US-AriaNeural": "Natural English — US (Aria)",
        "en-GB-SoniaNeural": "Natural English — UK (Sonia)",
    }
    selected_voice = st.selectbox(
        "Speech voice",
        options=list(voice_labels.keys()),
        format_func=lambda voice: voice_labels[voice],
        index=0,
    )
    speech_rate = st.select_slider(
        "Speaking speed",
        options=["Slow", "Normal", "Fast"],
        value="Slow",
    )
    rate_value = {"Slow": "-12%", "Normal": "-5%", "Fast": "+5%"}[speech_rate]
    st.caption("Natural neural speech needs an internet connection. Offline system speech is used if it is unavailable.")

    if st.button("🔊 Test voice", use_container_width=True):
        test_text = "Hello. AI Guardian voice test. If you can hear this clearly, speech is working."
        try:
            audio, mime, engine_name = text_to_speech_audio(
                test_text, selected_voice, rate_value
            )
            st.session_state["test_audio"] = audio
            st.session_state["test_audio_mime"] = mime
            st.session_state["test_audio_engine"] = engine_name
            st.session_state["test_audio_error"] = ""
        except Exception as error:
            st.session_state["test_audio"] = None
            st.session_state["test_audio_error"] = str(error)

    if st.session_state.get("test_audio"):
        st.caption("Test voice: " + st.session_state.get("test_audio_engine", ""))
        st.audio(st.session_state["test_audio"], format=st.session_state["test_audio_mime"])
    if st.session_state.get("test_audio_error"):
        st.error(st.session_state["test_audio_error"])

    st.divider()
    st.caption("AI Guardian is an experimental awareness aid, not a certified mobility or navigation device.")

@st.cache_data(show_spinner=False, max_entries=64)
def render_speech(text: str, voice: str, rate: str):
    return text_to_speech_audio(text, voice, rate)


@st.cache_resource
def load_detector(confidence_value: float):
    return ObjectDetector(confidence=confidence_value)


class LiveObjectDetector(VideoProcessorBase):
    """Per-camera YOLO/ByteTrack processor with thread-safe status."""

    def __init__(self):
        self.detector = ObjectDetector(confidence=confidence)
        self.lock = threading.Lock()
        self.frame_count = 0
        self.last_frame_time = 0.0
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
            track_rows = []
            detections = []

            for item in tracked:
                x1, _, x2, _ = item.box
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

            with self.lock:
                self.frame_count += 1
                self.last_frame_time = now
                self.last_tracks = track_rows
                self.error = ""

                scene_changed = signature != self.last_signature
                cooldown_finished = now - self.last_announcement_time >= 8.0
                if speech_enabled and tracked and scene_changed and cooldown_finished:
                    moving = [row for row in track_rows if row["motion"] != "stationary"]
                    self.last_announcement = build_announcement(
                        detections, frame_width, moving
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
                "tracks": list(self.last_tracks),
                "announcement": self.last_announcement,
                "announcement_id": self.announcement_id,
                "error": self.error,
            }


st.divider()
st.header("Live camera")
st.caption("Select START, allow camera access, and keep the objects in view.")
ctx = webrtc_streamer(
    key="ai-guardian-live-camera-v4",
    mode=WebRtcMode.SENDRECV,
    video_processor_factory=LiveObjectDetector,
    media_stream_constraints={"video": True, "audio": False},
    async_processing=True,
    desired_playing_state=True,
)

st.header("Live assistant status")


@st.fragment(run_every="1s")
def live_status_panel():
    processor = ctx.video_processor
    if processor is None:
        st.info("Camera is stopped. Press START in the camera panel.")
        return

    status = processor.get_status()
    age = time.monotonic() - status["last_frame_time"] if status["last_frame_time"] else None
    columns = st.columns(4)
    columns[0].metric("Frames processed", status["frame_count"])
    columns[1].metric(
        "Camera", "Live" if age is not None and age < 3 else "Waiting"
    )
    columns[2].metric("Visible objects", len(status["tracks"]))
    columns[3].metric("Tracked IDs", len({row["track_id"] for row in status["tracks"]}))

    if status["error"]:
        st.error("Vision pipeline: " + status["error"])
    elif age is not None and age >= 3:
        st.warning("No new camera frames. Check camera permission and connection.")
    elif status["frame_count"] > 0:
        st.success("Camera frames are being processed.")

    if status["tracks"]:
        st.subheader("What the camera currently sees")
        st.dataframe(
            [
                {
                    "ID": row["track_id"],
                    "Object": row["label"].replace("_", " ").title(),
                    "Confidence": f'{row["confidence"]:.0%}',
                    "Image movement": row["motion"].title(),
                }
                for row in status["tracks"]
            ],
            use_container_width=True,
            hide_index=True,
        )
    else:
        st.info("No objects detected in the latest frame.")

    if speech_enabled and status["announcement_id"] > st.session_state.get(
        "last_live_audio_id", 0
    ):
        st.session_state["last_live_audio_id"] = status["announcement_id"]
        st.session_state["latest_spoken_text"] = status["announcement"]
        try:
            audio, mime, engine_name = render_speech(
                status["announcement"], selected_voice, rate_value
            )
            st.session_state["latest_audio"] = audio
            st.session_state["latest_audio_mime"] = mime
            st.session_state["latest_audio_engine"] = engine_name
            st.session_state["latest_audio_error"] = ""
        except Exception as error:
            st.session_state["latest_audio_error"] = str(error)

    if st.session_state.get("latest_spoken_text"):
        st.subheader("Latest spoken description")
        st.write(st.session_state["latest_spoken_text"])
        if st.session_state.get("latest_audio"):
            st.caption("Speech engine: " + st.session_state.get("latest_audio_engine", ""))
            st.audio(
                st.session_state["latest_audio"],
                format=st.session_state["latest_audio_mime"],
            )
            st.caption("Press Play to hear the full announcement. Browser autoplay is intentionally not relied upon.")
        elif st.session_state.get("latest_audio_error"):
            st.error("Speech generation failed: " + st.session_state["latest_audio_error"])


live_status_panel()

st.divider()
st.header("On-demand scene description")
st.caption("Capture a still image when you want a fresh spoken summary immediately.")
camera_image = st.camera_input("Capture scene")
if camera_image is not None and st.button("Analyze and describe scene", type="primary"):
    try:
        import numpy as np
        from PIL import Image

        image = np.array(Image.open(camera_image).convert("RGB"))
        bgr_image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
        detector = load_detector(confidence)
        annotated, detections = detector.detect(bgr_image)
        announcement = build_announcement(detections, bgr_image.shape[1])
        st.image(annotated, channels="BGR", caption="Detected scene", use_container_width=True)
        st.subheader("Scene description")
        st.write(announcement)
        audio, mime, engine_name = render_speech(announcement, selected_voice, rate_value)
        st.caption("Speech engine: " + engine_name)
        st.audio(audio, format=mime)
    except Exception as error:
        st.error("Scene analysis or speech failed: " + str(error))
