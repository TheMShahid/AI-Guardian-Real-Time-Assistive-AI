"""Streamlit interface for AI Guardian's first two features."""

import sys
from pathlib import Path

import cv2
import numpy as np
import streamlit as st
from PIL import Image

PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from ai_guardian.audio.announcer import build_announcement, text_to_speech_audio
from ai_guardian.vision.detector import ObjectDetector


st.set_page_config(
    page_title="AI Guardian",
    page_icon="👁️",
    layout="wide",
)

st.title("AI Guardian")
st.subheader("Real-Time Assistive Visual Intelligence")
st.write(
    "Capture a camera image, detect familiar objects, and hear a short spoken "
    "description of their position in the image."
)

st.info(
    "Prototype limitation: this Streamlit version analyses a captured still image, "
    "not a continuous live video stream. Position means left, centre, or right "
    "within the image; it does not estimate distance or guarantee obstacle safety."
)


@st.cache_resource
def load_detector():
    return ObjectDetector()


camera_image = st.camera_input("Take a picture")
if camera_image is None:
    st.caption("Allow camera access, point the camera at a scene, and take a picture.")
    st.stop()

if st.button("Analyze scene and announce objects", type="primary"):
    try:
        with st.spinner("Loading AI model and analyzing the image..."):
            image = Image.open(camera_image).convert("RGB")
            rgb_frame = np.array(image)
            frame = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2BGR)

            detector = load_detector()
            annotated_frame, detections = detector.detect(frame)

            announcement = build_announcement(detections, frame.shape[1])

        left, right = st.columns(2)
        with left:
            st.markdown("#### Camera image")
            st.image(rgb_frame, use_container_width=True)
        with right:
            st.markdown("#### Detected objects")
            st.image(annotated_frame, channels="BGR", use_container_width=True)

        st.markdown("#### Detection results")
        if detections:
            st.write(
                [
                    {
                        "Object": item.label,
                        "Confidence": f"{item.confidence:.0%}",
                        "Image position": (
                            "left" if (item.box[0] + item.box[2]) / 2 < frame.shape[1] * 0.35
                            else "right" if (item.box[0] + item.box[2]) / 2 > frame.shape[1] * 0.65
                            else "centre"
                        ),
                    }
                    for item in detections
                ]
            )
        else:
            st.warning("No familiar objects were detected in this image.")

        st.markdown("#### Voice announcement")
        st.write(announcement)

        with st.spinner("Generating speech..."):
            audio_bytes = text_to_speech_audio(announcement)

        st.audio(audio_bytes, format="audio/wav", autoplay=True)

    except Exception as error:
        st.error(f"Could not analyze this image: {error}")
        st.exception(error)
