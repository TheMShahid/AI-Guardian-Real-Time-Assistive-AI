"""AI Guardian: accessible live visual awareness with improved speech feedback."""

import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import av
import cv2
import streamlit as st
from streamlit_webrtc import WebRtcMode, VideoProcessorBase, webrtc_streamer

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

# A single background worker keeps network-based speech generation off the UI thread.
SPEECH_EXECUTOR = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ai-guardian-tts")
INFERENCE_INTERVAL_SECONDS = 0.20  # Target: 5 AI inference passes per second.

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
    .block-container {max-width: 1480px; padding-top: 1.6rem; padding-bottom: 3.5rem;}
    h1 {font-size: 2.55rem !important; letter-spacing: -0.04em; font-weight: 750 !important;}
    h2 {font-size: 1.42rem !important; font-weight: 680 !important;}
    h3 {font-size: 1.12rem !important;}
    [data-testid="stMetric"] {
        background: rgba(120, 140, 160, 0.08);
        border: 1px solid rgba(120, 140, 160, 0.22);
        padding: 1rem 1.1rem; border-radius: 1rem;
        box-shadow: 0 3px 14px rgba(0,0,0,.035);
    }
    [data-testid="stMetricLabel"] {font-size: .82rem;}
    [data-testid="stMetricValue"] {font-weight: 720;}
    [data-testid="stVerticalBlockBorderWrapper"] {border-radius: 1rem;}
    [data-testid="stButton"] button {min-height: 2.8rem; font-weight: 600;}
    [data-testid="stAudio"] {width: 100%;}
    .ag-subtitle {font-size: 1.08rem; opacity: .82; margin-top: -.5rem;}
    .ag-note {font-size: .9rem; opacity: .78;}
    .ag-hero {padding: 1.15rem 1.35rem; border: 1px solid rgba(120,140,160,.24); border-radius: 1rem; background: linear-gradient(120deg, rgba(80,120,160,.10), rgba(80,160,140,.055)); margin: .8rem 0 1.3rem 0;}
    .ag-kicker {font-size:.76rem; font-weight:700; letter-spacing:.12em; text-transform:uppercase; opacity:.72;}
    .ag-hero-copy {font-size:.98rem; opacity:.86; margin-top:.35rem;}
    </style>
    """,
    unsafe_allow_html=True,
)

st.title("👁️ AI Guardian")
st.markdown(
    '<div class="ag-subtitle">Real-time visual awareness and voice assistance</div>',
    unsafe_allow_html=True,
)
st.markdown(
    '<div class="ag-hero"><div class="ag-kicker">Your real-time visual companion</div><div class="ag-hero-copy">AI Guardian detects familiar objects, tracks their image movement, and speaks concise scene updates while your camera is live.</div></div>',
    unsafe_allow_html=True,
)

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
    speech_enabled = st.toggle("Automatic live voice", value=True, help="When enabled, new scene updates are converted to speech automatically.")
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
    st.caption("Natural neural speech uses an internet connection. If unavailable, AI Guardian tries your computer’s offline voice.")

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
        self.pending_signature = None
        self.pending_signature_since = 0.0
        self.previous_centres = {}
        self.last_seen = {}
        self.last_inference_time = 0.0
        self.last_annotated_frame = None
        self.error = ""

    def recv(self, frame):
        image = frame.to_ndarray(format="bgr24")
        now = time.monotonic()

        # WebRTC can deliver more frames than a cloud CPU can infer on.
        # Reuse the most recent annotated frame between inference passes.
        if (
            self.last_annotated_frame is not None
            and now - self.last_inference_time < INFERENCE_INTERVAL_SECONDS
        ):
            return av.VideoFrame.from_ndarray(
                self.last_annotated_frame, format="bgr24"
            )

        try:
            annotated, tracked = self.detector.track(image)
            self.last_inference_time = now
            self.last_annotated_frame = annotated.copy()
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

                # A scene must remain stable briefly before it is announced.
                # This filters detector flicker and small frame-to-frame changes.
                if signature != self.pending_signature:
                    self.pending_signature = signature
                    self.pending_signature_since = now

                scene_stable = now - self.pending_signature_since >= 1.5
                scene_changed = signature != self.last_signature
                cooldown_finished = now - self.last_announcement_time >= 6.0

                if (
                    speech_enabled
                    and tracked
                    and scene_changed
                    and scene_stable
                    and cooldown_finished
                ):
                    self.last_announcement = build_announcement(
                        detections, frame_width
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


@st.cache_data(show_spinner=False, ttl=21600)
def fetch_cloudflare_ice_servers(turn_key_id: str, turn_key_api_token: str):
    """Request temporary TURN credentials from Cloudflare; never expose API secrets."""
    import requests

    endpoint = (
        "https://rtc.live.cloudflare.com/v1/turn/keys/"
        f"{turn_key_id}/credentials/generate-ice-servers"
    )
    response = requests.post(
        endpoint,
        headers={
            "Authorization": f"Bearer {turn_key_api_token}",
            "Content-Type": "application/json",
        },
        json={"ttl": 86400},
        timeout=8,
    )
    response.raise_for_status()
    payload = response.json()
    servers = payload.get("iceServers", [])
    # Port 53 is commonly blocked by browsers and some networks.
    for server in servers:
        urls = server.get("urls", [])
        if isinstance(urls, str):
            urls = [urls]
        server["urls"] = [
            url for url in urls
            if ":53" not in url
        ]
    return [server for server in servers if server.get("urls")]


def build_rtc_configuration():
    """Build ICE configuration using Cloudflare TURN when configured.

    Streamlit secrets:
      CF_TURN_KEY_ID = "..."
      CF_TURN_API_TOKEN = "..."

    The long-lived API token stays on the server. Temporary ICE credentials
    are passed to the browser and expire after 24 hours.
    """
    ice_servers = [{"urls": ["stun:stun.l.google.com:19302"]}]
    try:
        key_id = st.secrets.get("CF_TURN_KEY_ID", "")
        api_token = st.secrets.get("CF_TURN_API_TOKEN", "")
        if key_id and api_token:
            try:
                turn_servers = fetch_cloudflare_ice_servers(key_id, api_token)
                ice_servers.extend(turn_servers)
            except Exception as error:
                # Keep STUN available if TURN credential generation is unavailable.
                st.warning(
                    "TURN relay credentials could not be refreshed. "
                    "Trying the camera connection with STUN only. "
                    f"Details: {type(error).__name__}"
                )
        else:
            st.info(
                "TURN relay is not configured yet. Camera streaming will try "
                "the STUN connection; some networks may require TURN."
            )
    except Exception:
        # Local development may not have a Streamlit secrets file.
        pass
    return {"iceServers": ice_servers}


st.divider()
st.header("Live visual assistant")
st.caption("Start the camera once and allow access. Automatic voice is enabled by default. Cloud camera streaming uses WebRTC; a STUN server is configured, while restrictive networks may require TURN credentials.")
ctx = webrtc_streamer(
    key="ai-guardian-live-camera-v4",
    mode=WebRtcMode.SENDRECV,
    video_processor_factory=LiveObjectDetector,
    media_stream_constraints={
        "video": {
            "width": {"ideal": 640},
            "height": {"ideal": 480},
            "frameRate": {"ideal": 15, "max": 20},
        },
        "audio": False,
    },
    # STUN is required for browser-to-cloud WebRTC connectivity.
    # Some restrictive networks may additionally require TURN credentials.
    rtc_configuration=build_rtc_configuration(),
    async_processing=True,
    desired_playing_state=False,
)

st.header("Live assistant status")


@st.fragment(run_every="2s")
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
        st.session_state["latest_audio"] = None
        st.session_state["latest_audio_error"] = ""
        st.session_state["speech_future"] = SPEECH_EXECUTOR.submit(
            text_to_speech_audio,
            status["announcement"],
            selected_voice,
            rate_value,
        )

    # Poll the worker without blocking the Streamlit fragment.
    speech_future = st.session_state.get("speech_future")
    if speech_future is not None and speech_future.done():
        try:
            audio, mime, engine_name = speech_future.result()
            st.session_state["latest_audio"] = audio
            st.session_state["latest_audio_mime"] = mime
            st.session_state["latest_audio_engine"] = engine_name
            st.session_state["latest_audio_error"] = ""
        except Exception as error:
            st.session_state["latest_audio_error"] = str(error)
        finally:
            st.session_state["speech_future"] = None

    if st.session_state.get("latest_spoken_text"):
        st.subheader("Latest spoken description")
        st.write(st.session_state["latest_spoken_text"])
        if st.session_state.get("latest_audio"):
            st.caption("Speech engine: " + st.session_state.get("latest_audio_engine", ""))
            st.audio(
                st.session_state["latest_audio"],
                format=st.session_state["latest_audio_mime"],
                autoplay=True,
            )
            st.caption("Speech is generated for stable scene changes, not every video frame. Browser autoplay restrictions may require one initial Play action.")
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
