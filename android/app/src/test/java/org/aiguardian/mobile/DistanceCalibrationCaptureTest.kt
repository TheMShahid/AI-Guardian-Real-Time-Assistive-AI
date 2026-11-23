package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class DistanceCalibrationCaptureTest {
    @Test
    fun convertsDetectionHeightToFrameRatio() {
        val sample = DistanceCalibrationCapture.fromDetection(
            label = "Person",
            boxHeightPixels = 900f,
            frameHeightPixels = 1800,
            capturedAtEpochMs = 1000L
        )
        assertNotNull(sample)
        assertEquals("person", sample!!.referenceLabel)
        assertEquals(0.5f, sample.normalizedBoxHeight, 0.0001f)
    }

    @Test
    fun rejectsInvalidFrameAndTinyBoxes() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", 100f, 0, 1000L
            )
        )
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", 10f, 1000, 1000L
            )
        )
    }

    @Test
    fun rejectsNonFiniteAndBlankCaptureData() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", Float.NaN, 1000, 1000L
            )
        )
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "   ", 500f, 1000, 1000L
            )
        )
    }

    @Test
    fun rejectsBoxLargerThanFrame() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "chair", 1200f, 1000, 1000L
            )
        )
    }
}
