"""AI Guardian Streamlit app: live detection plus spoken snapshot summaries."""

import sys
from pathlib import Path

import av
import cv2
import numpy as np
import streamlit as st
from PIL import Image
from streamlit_webrtc import WebRtcMode, VideoProcessorBase, webrtc_streamer

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.audio.announcer import build_announcement, text_to_speech_audio
from ai_guardian.vision.detector import ObjectDetector


st.set_page_config(page_title="AI Guardian", page_icon="👁️", layout="wide")
st.title("AI Guardian")
st.subheader("Real-Time Assistive Visual Intelligence")
st.write("Live camera object detection with an optional spoken scene summary.")

st.warning(
    "Assistive prototype only. Detection can miss objects. Image position is not "
    "physical distance or collision prediction. Never rely on this system alone "
    "for road crossing, navigation, or obstacle avoidance."
)


@st.cache_resource
def load_detector():
    return ObjectDetector(confidence=0.25)


class LiveObjectDetector(VideoProcessorBase):
    """Run YOLO on each incoming webcam frame and return annotated video."""

    def __init__(self):
        self.detector = load_detector()

    def recv(self, frame):
        image = frame.to_ndarray(format="bgr24")
        annotated, _ = self.detector.detect(image)
        return av.VideoFrame.from_ndarray(annotated, format="bgr24")


st.markdown("## Feature 1 — Live object detection")
st.caption(
    "Start the camera below. The video is processed continuously while the "
    "stream is running. The first start may take longer while YOLO loads."
)

webrtc_streamer(
    key="ai-guardian-live-camera",
    mode=WebRtcMode.SENDRECV,
    video_processor_factory=LiveObjectDetector,
    media_stream_constraints={"video": True, "audio": False},
    async_processing=True,
)

st.divider()
st.markdown("## Feature 2 — Voice-based scene announcement")
st.caption(
    "Capture a still image to hear a short spoken summary. Live video detection "
    "continues separately; repeated speech on every video frame is intentionally "
    "not enabled yet."
)

camera_image = st.camera_input("Capture an image for voice analysis")

if camera_image is not None and st.button(
    "Analyze image and speak", type="primary"
):
    try:
        with st.spinner("Analyzing image..."):
            image = Image.open(camera_image).convert("RGB")
            rgb_frame = np.array(image)
            bgr_frame = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2BGR)

            detector = load_detector()
            annotated_frame, detections = detector.detect(bgr_frame)
            announcement = build_announcement(detections, bgr_frame.shape[1])

        original_col, result_col = st.columns(2)
        with original_col:
            st.markdown("**Captured image**")
            st.image(rgb_frame, use_container_width=True)
        with result_col:
            st.markdown("**Detected objects**")
            st.image(annotated_frame, channels="BGR", use_container_width=True)

        if detections:
            st.markdown("**Detection results**")
            st.write([
                {
                    "Object": item.label,
                    "Confidence": f"{item.confidence:.0%}",
                    "Image position": (
                        "left"
                        if (item.box[0] + item.box[2]) / 2 < bgr_frame.shape[1] * 0.35
                        else "right"
                        if (item.box[0] + item.box[2]) / 2 > bgr_frame.shape[1] * 0.65
                        else "centre"
                    ),
                }
                for item in detections
            ])
        else:
            st.info("No familiar objects were detected in this captured image.")

        st.markdown("**Spoken announcement**")
        st.write(announcement)
        with st.spinner("Generating speech..."):
            audio_bytes = text_to_speech_audio(announcement)
        st.audio(audio_bytes, format="audio/wav")

    except Exception as error:
        st.error(f"Analysis failed: {error}")
        st.exception(error)
