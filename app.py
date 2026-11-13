"""AI Guardian: accessible live visual awareness with improved speech feedback."""

import base64
import json
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import av
import cv2
import streamlit as st
import streamlit.components.v1 as components
from streamlit_webrtc import WebRtcMode, VideoProcessorBase, webrtc_streamer

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

# A single background worker keeps network-based speech generation off the UI thread.
SPEECH_EXECUTOR = ThreadPoolExecutor(max_workers=1, thread_name_prefix="ai-guardian-tts")
INFERENCE_INTERVAL_SECONDS = 0.20  # Target: 5 AI inference passes per second.

from ai_guardian.audio.announcer import (
    build_announcement,
    text_to_speech_audio,
    text_to_speech_segments,
)
from ai_guardian.vision.detector import Detection, ObjectDetector
from ai_guardian.vision.camera import capture_camera_frame
from ai_guardian.vision.network_stream import NetworkCameraWorker
from ai_guardian.vision.ocr import read_text_from_image


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
    [data-testid="stWebRtc"] {
        width: 100%; max-width: 100%; overflow: hidden;
        border: 1px solid rgba(120, 140, 160, .28);
        border-radius: 1rem; padding: .65rem;
        background: rgba(100, 120, 145, .055);
        box-shadow: 0 8px 28px rgba(0, 0, 0, .07);
    }
    [data-testid="stWebRtc"] video {
        width: 100% !important; max-height: 420px !important;
        object-fit: contain !important; border-radius: .7rem;
        background: #101722;
    }
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
        value=0.20,
        step=0.05,
        help="Lower values may reveal smaller/background objects but can increase false detections.",
    )
    speech_enabled = st.toggle("Automatic live voice", value=True, help="When enabled, new scene updates are converted to speech automatically.")
    language_labels = {
        "en": "English",
        "ur": "اردو (Urdu)",
        "ps": "پښتو (Pashto)",
    }
    assistant_language = st.selectbox(
        "Assistant language",
        options=list(language_labels.keys()),
        format_func=lambda code: language_labels[code],
        index=0,
        help="Changes spoken scene summaries and OCR reading language.",
    )
    voice_options = {
        "en": {
            "en-US-AriaNeural": "English — US (Aria)",
            "en-GB-SoniaNeural": "English — UK (Sonia)",
        },
        "ur": {
            "ur-PK-AsadNeural": "Urdu Pakistan — Asad",
            "ur-PK-UzmaNeural": "Urdu Pakistan — Uzma",
        },
        "ps": {
            "ps-AF-GulNawazNeural": "Pashto — Gul Nawaz",
        },
    }
    voice_labels = voice_options[assistant_language]
    selected_voice = st.selectbox(
        "Speech voice",
        options=list(voice_labels.keys()),
        format_func=lambda voice: voice_labels[voice],
        key=f"speech_voice_{assistant_language}",
    )
    speech_rate = st.select_slider(
        "Speaking speed",
        options=["Slow", "Normal", "Fast"],
        value="Fast",
    )
    rate_value = {"Slow": "-12%", "Normal": "-5%", "Fast": "+10%"}[speech_rate]
    st.caption("Fast is the default for quicker announcements. Urdu/Pashto neural voices require an internet connection.")

    if st.button("🔊 Test voice", use_container_width=True):
        test_text = {
            "en": "Hello. AI Guardian voice test. If you can hear this clearly, speech is working.",
            "ur": "السلام علیکم۔ یہ اے آئی گارڈین کی آواز کا امتحان ہے۔",
            "ps": "سلام. دا د اې آی ګارډین د غږ ازموینه ده.",
        }[assistant_language]
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

    st.caption("Bluetooth headset: pair it in your phone/computer Bluetooth settings, select it as the audio output, then use Test voice. The browser follows the operating system audio route; this app does not pair Bluetooth devices itself.")

    st.divider()
    st.caption("AI Guardian is an experimental awareness aid, not a certified mobility or navigation device.")

def capture_rtsp_frame(stream_url: str):
    """Capture one frame from a user-supplied local-network RTSP camera."""
    normalized_url = stream_url.strip()
    if not normalized_url.startswith(("rtsp://", "rtsps://")):
        raise ValueError("Enter a valid RTSP or RTSPS stream URL.")

    capture = cv2.VideoCapture(normalized_url, cv2.CAP_FFMPEG)
    try:
        if not capture.isOpened():
            raise RuntimeError(
                "Could not open the IP camera stream. Check the RTSP URL, "
                "camera credentials, and that this computer is on the same network."
            )

        frame = None
        for _ in range(5):
            success, candidate = capture.read()
            if success and candidate is not None:
                frame = candidate
                break
            time.sleep(0.2)

        if frame is None:
            raise RuntimeError(
                "The camera connected but did not provide a video frame. "
                "Check that the RTSP stream is active and supports H.264/H.265."
            )
        return frame
    finally:
        capture.release()


@st.cache_data(show_spinner=False, max_entries=64)
def render_speech(text: str, voice: str, rate: str):
    return text_to_speech_audio(text, voice, rate)


def render_audio_playlist(audio_segments, title: str = "Complete speech playback"):
    """Render accessible sequential playback for multi-part TTS audio."""
    data_uris = [
        "data:" + mime + ";base64," + base64.b64encode(audio).decode("ascii")
        for audio, mime, _engine in audio_segments
    ]
    clips_json = json.dumps(data_uris)
    playlist_html = """
    <div style="font-family:Arial,sans-serif;width:100%;">
      <audio id="ag-audio-player" controls preload="metadata"
             style="width:100%;" aria-label="Complete spoken text"></audio>
      <div style="display:flex;align-items:center;gap:12px;margin-top:8px;flex-wrap:wrap;">
        <button id="ag-replay-all" type="button"
          style="padding:9px 14px;border-radius:8px;border:1px solid #888;cursor:pointer;">
          Replay all
        </button>
        <span id="ag-playback-status" role="status" aria-live="polite">
          Press Play to hear the complete reading.
        </span>
      </div>
    </div>
    <script>
      const clips = __CLIPS__;
      const player = document.getElementById("ag-audio-player");
      const status = document.getElementById("ag-playback-status");
      const replay = document.getElementById("ag-replay-all");
      let current = 0;
      function playCurrent() {
        if (current >= clips.length) {
          status.textContent = "Reading complete.";
          return;
        }
        player.src = clips[current];
        player.load();
        status.textContent = "Playing part " + (current + 1) + " of " + clips.length;
        const attempt = player.play();
        if (attempt && attempt.catch) {
          attempt.catch(() => {
            status.textContent = "Press Play on the audio controls to start reading.";
          });
        }
      }
      player.addEventListener("ended", () => {
        current += 1;
        if (current < clips.length) playCurrent();
        else status.textContent = "Reading complete.";
      });
      replay.addEventListener("click", () => {
        current = 0;
        playCurrent();
      });
      if (clips.length) {
        player.src = clips[0];
        status.textContent = clips.length + " audio part(s) ready. Press Play to begin.";
      }
    </script>
    """.replace("__CLIPS__", clips_json)
    components.html(playlist_html, height=118, scrolling=False)


@st.cache_resource
def load_detector(confidence_value: float):
    return ObjectDetector(confidence=confidence_value)


class LiveObjectDetector(VideoProcessorBase):
    """Per-camera YOLO/ByteTrack processor with thread-safe status."""

    def __init__(self):
        model_load_started = time.perf_counter()
        self.detector = ObjectDetector(confidence=confidence)
        self.model_load_seconds = time.perf_counter() - model_load_started
        self.last_inference_ms = 0.0
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
        now = time.monotonic()

        # Throttle before converting the incoming frame to a NumPy array.
        # This avoids repeated colour-buffer conversions between AI passes.
        if (
            self.last_annotated_frame is not None
            and now - self.last_inference_time < INFERENCE_INTERVAL_SECONDS
        ):
            return av.VideoFrame.from_ndarray(
                self.last_annotated_frame, format="bgr24"
            )

        image = frame.to_ndarray(format="bgr24")
        try:
            # Set the throttle timestamp before inference so repeated model
            # errors cannot trigger an inference attempt on every video frame.
            self.last_inference_time = now
            inference_started = time.perf_counter()
            annotated, tracked = self.detector.track(image)
            self.last_inference_ms = (time.perf_counter() - inference_started) * 1000
            self.last_annotated_frame = annotated.copy()
            frame_width = image.shape[1]
            track_rows = []
            detections = []

            for item in tracked:
                x1, _, x2, _ = item.box
                centre_x = (x1 + x2) / 2
                image_position = (
                    "left" if centre_x < frame_width * 0.35
                    else "right" if centre_x > frame_width * 0.65
                    else "centre"
                )
                previous = (
                    self.previous_centres.get(item.track_id)
                    if item.track_id is not None else None
                )
                motion = "stationary"
                if previous is not None:
                    delta = centre_x - previous
                    threshold = max(8.0, frame_width * 0.015)
                    if delta <= -threshold:
                        motion = "moving left"
                    elif delta >= threshold:
                        motion = "moving right"

                # Only genuine ByteTrack IDs are used for temporal movement.
                if item.track_id is not None:
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
                    "position": image_position,
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

                # Reduce latency while filtering brief detector flicker.
                scene_stable = now - self.pending_signature_since >= 0.5
                scene_changed = signature != self.last_signature
                cooldown_finished = now - self.last_announcement_time >= 2.0
                periodic_refresh_due = now - self.last_announcement_time >= 5.0

                if (
                    speech_enabled
                    and tracked
                    and scene_stable
                    and (scene_changed or periodic_refresh_due)
                    and cooldown_finished
                ):
                    self.last_announcement = build_announcement(
                        detections, frame_width, language=assistant_language
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
                "inference_ms": self.last_inference_ms,
                "model_load_seconds": self.model_load_seconds,
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
st.caption("Start the camera and allow access. The preview displays AI-annotated video; object positions are relative to the camera image.")
# Keep the live preview visually focused instead of stretching it across the page.
_camera_left, camera_center, _camera_right = st.columns([1, 3, 1], gap="small")
with camera_center:
    ctx = webrtc_streamer(
        # A fresh component key resets any stale browser-side WebRTC session state.
        key="ai-guardian-live-camera-v6",
        # SENDRECV is required: the browser sends camera frames and receives
        # the server's annotated frames with YOLO boxes and labels.
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
    )

# Keep live playback immediately below the camera, not below the long status table.
if st.session_state.get("latest_audio"):
    st.subheader("🔊 Live voice announcement")
    st.audio(
        st.session_state["latest_audio"],
        format=st.session_state["latest_audio_mime"],
        autoplay=True,
    )
    st.caption("Pause, resume, or replay using the audio controls.")

st.header("Live assistant status")


@st.fragment(run_every="1s")
def live_status_panel():
    processor = ctx.video_processor
    if processor is None:
        st.info("Camera is stopped. Press START in the camera panel.")
        return

    status = processor.get_status()
    age = time.monotonic() - status["last_frame_time"] if status["last_frame_time"] else None
    columns = st.columns(5)
    columns[0].metric("AI passes", status["frame_count"])
    columns[1].metric(
        "Camera", "Live" if age is not None and age < 3 else "Waiting"
    )
    columns[2].metric("Visible objects", len(status["tracks"]))
    columns[3].metric("Tracked IDs", len({row["track_id"] for row in status["tracks"] if row["track_id"] is not None}))
    columns[4].metric("AI latency", f'{status["inference_ms"]:.0f} ms')
    st.caption(f'YOLO model initialization: {status["model_load_seconds"]:.1f} seconds')

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
                    "Image position": row["position"].title(),
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
    audio_ready = False
    speech_future = st.session_state.get("speech_future")
    if speech_future is not None and speech_future.done():
        try:
            audio, mime, engine_name = speech_future.result()
            st.session_state["latest_audio"] = audio
            st.session_state["latest_audio_mime"] = mime
            st.session_state["latest_audio_engine"] = engine_name
            st.session_state["latest_audio_error"] = ""
            audio_ready = True
        except Exception as error:
            st.session_state["latest_audio_error"] = str(error)
        finally:
            st.session_state["speech_future"] = None

    if st.session_state.get("latest_spoken_text"):
        st.subheader("Latest spoken description")
        st.write(st.session_state["latest_spoken_text"])
        if st.session_state.get("latest_audio_error"):
            st.error("Speech generation failed: " + st.session_state["latest_audio_error"])

    # Keep the audio player outside this two-second fragment. Otherwise each
    # status refresh can recreate the player and interrupt a long announcement.
    if audio_ready:
        st.rerun()


live_status_panel()


st.divider()
st.header("Wi-Fi IP camera — continuous live assistant")
st.caption(
    "Phone camera ko same Wi-Fi network par rakhein. IP Webcam ke liye /video (MJPEG) "
    "recommended hai; /shot.jpg polling aur RTSP bhi supported hain. Start ke baad "
    "frames continuously process honge, bilkul Live visual assistant ki tarah."
)

camera_stream_url = st.text_input(
    "Wi-Fi camera URL",
    placeholder="http://10.10.36.52:8080/video",
    type="password",
    help="IP Webcam example: http://PHONE-IP:8080/video. URL credentials may be masked here.",
    key="wifi_live_camera_url",
)
start_col, stop_col = st.columns(2)
with start_col:
    start_wifi_live = st.button(
        "Start Wi-Fi live assistant",
        type="primary",
        use_container_width=True,
        disabled=bool(st.session_state.get("wifi_camera_worker") and st.session_state.wifi_camera_worker.running),
    )
with stop_col:
    stop_wifi_live = st.button(
        "Stop Wi-Fi camera",
        use_container_width=True,
        disabled=not bool(st.session_state.get("wifi_camera_worker") and st.session_state.wifi_camera_worker.running),
    )

if start_wifi_live:
    if not camera_stream_url.strip():
        st.warning("Pehle Wi-Fi camera URL enter karein.")
    else:
        old_worker = st.session_state.get("wifi_camera_worker")
        if old_worker:
            old_worker.stop()
        worker = NetworkCameraWorker(
            url=camera_stream_url,
            confidence=confidence,
            language=assistant_language,
            voice=selected_voice,
            rate=rate_value,
            speech_enabled=speech_enabled,
            speech_executor=SPEECH_EXECUTOR,
        )
        st.session_state.wifi_camera_worker = worker
        worker.start()
        st.rerun()

if stop_wifi_live:
    worker = st.session_state.get("wifi_camera_worker")
    if worker:
        worker.stop()
    st.rerun()


@st.fragment(run_every="0.5s")
def wifi_live_panel():
    worker = st.session_state.get("wifi_camera_worker")
    if worker is None:
        st.info("Wi-Fi camera stopped. URL enter karke Start Wi-Fi live assistant press karein.")
        return
    status = worker.status()
    if status["frame"] is not None:
        st.image(
            status["frame"],
            channels="BGR",
            caption="Live Wi-Fi camera — AI annotated stream",
            width="stretch",
        )
        metrics = st.columns(3)
        metrics[0].metric("Processed frames", status["frames"])
        metrics[1].metric("AI latency", f'{status["inference_ms"]:.0f} ms')
        metrics[2].metric("Connection", "Live" if status["running"] else "Stopped")
        st.subheader("Live scene description")
        st.write(status["description"])
        if status["audio"] is not None and status["audio_id"] > st.session_state.get("wifi_last_audio_id", 0):
            st.session_state.wifi_last_audio_id = status["audio_id"]
            st.audio(status["audio"], format=status["audio_mime"], autoplay=True)
    elif status["running"]:
        st.info("Connecting to camera and loading the AI model. First startup can take a little longer.")
    if status["error"]:
        st.error("Wi-Fi camera pipeline: " + status["error"])
    elif not status["running"] and status["frames"] == 0:
        st.warning("Camera stopped before any frame was received. Check the URL and same-Wi-Fi connection.")


wifi_live_panel()


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
        announcement = build_announcement(
            detections, bgr_image.shape[1], language=assistant_language
        )
        st.image(annotated, channels="BGR", caption="Detected scene", use_container_width=True)
        st.subheader("Scene description")
        st.write(announcement)
        audio, mime, engine_name = render_speech(announcement, selected_voice, rate_value)
        st.caption("Speech engine: " + engine_name)
        st.audio(audio, format=mime)
    except Exception as error:
        st.error("Scene analysis or speech failed: " + str(error))

if camera_image is not None:
    st.subheader("Read visible text")
    st.caption("OCR works best with sharp, well-lit, printed text. Handwriting and decorative Nastaliq fonts may be less accurate.")
    if st.button("📖 Read text aloud", use_container_width=True):
        try:
            import html
            import numpy as np
            from PIL import Image

            ocr_rgb = np.array(Image.open(camera_image).convert("RGB"))
            extracted_text = read_text_from_image(ocr_rgb, assistant_language)
            if not extracted_text.strip():
                st.warning("No readable text was found. Try moving the camera closer and improving the lighting.")
            else:
                if assistant_language in ("ur", "ps"):
                    st.markdown(
                        '<div dir="rtl" style="text-align:right;line-height:2;font-size:1.15rem;">'
                        + html.escape(extracted_text).replace("\\n", "<br>")
                        + "</div>",
                        unsafe_allow_html=True,
                    )
                else:
                    st.text_area("Extracted text", extracted_text, height=180)
                with st.spinner("Preparing complete text reading in speech segments..."):
                    ocr_segments = text_to_speech_segments(
                        extracted_text,
                        selected_voice,
                        rate_value,
                        max_chars=280,
                    )
                engines = sorted({engine for _audio, _mime, engine in ocr_segments})
                st.caption(
                    f"Complete reading prepared: {len(ocr_segments)} audio part(s). "
                    f"Speech engine: {', '.join(engines)}"
                )
                render_audio_playlist(ocr_segments)
        except Exception as error:
            st.error("Text reading failed: " + str(error))
