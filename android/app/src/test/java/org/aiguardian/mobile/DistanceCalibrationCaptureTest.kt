package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class DistanceCalibrationCaptureTest {
    private fun sample(
        ratio: Float = 0.5f,
        x: Float = 0.5f,
        y: Float = 0.5f,
        time: Long = 1_000L,
        label: String = "person"
    ) = DistanceCalibrationCapture(
        referenceLabel = label,
        normalizedBoxHeight = ratio,
        normalizedCenterX = x,
        normalizedCenterY = y,
        capturedAtEpochMs = time
    )

    @Test
    fun convertsDetectionGeometryToNormalizedFrameCoordinates() {
        val captured = DistanceCalibrationCapture.fromDetection(
            label = "Person",
            boxLeftPixels = 200f,
            boxTopPixels = 300f,
            boxRightPixels = 600f,
            boxBottomPixels = 1200f,
            frameWidthPixels = 1000,
            frameHeightPixels = 1800,
            capturedAtEpochMs = 1000L
        )
        assertNotNull(captured)
        assertEquals("person", captured!!.referenceLabel)
        assertEquals(0.5f, captured.normalizedBoxHeight, 0.0001f)
        assertEquals(0.4f, captured.normalizedCenterX, 0.0001f)
        assertEquals(750f / 1800f, captured.normalizedCenterY, 0.0001f)
    }

    @Test
    fun rejectsInvalidFrameAndTinyBoxes() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", 0f, 100f, 100f, 500f, 0, 1000, 1000L
            )
        )
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", 10f, 10f, 20f, 20f, 1000, 1000, 1000L
            )
        )
    }

    @Test
    fun rejectsNonFiniteAndBlankCaptureData() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "person", Float.NaN, 0f, 100f, 500f, 1000, 1000, 1000L
            )
        )
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "   ", 100f, 100f, 500f, 600f, 1000, 1000, 1000L
            )
        )
    }

    @Test
    fun rejectsBoxLargerThanFrame() {
        assertNull(
            DistanceCalibrationCapture.fromDetection(
                "chair", 100f, 100f, 1200f, 900f, 1000, 1000, 1000L
            )
        )
    }

    @Test
    fun trackerAcceptsMedianAfterFiveStableFrames() {
        val tracker = DistanceCalibrationCaptureTracker()
        tracker.start()
        val ratios = listOf(0.49f, 0.50f, 0.51f, 0.50f, 0.495f)
        var result: DistanceCalibrationCapture? = null

        ratios.forEachIndexed { index, ratio ->
            result = tracker.add(
                sample(ratio = ratio, time = 1_000L + index * 500L)
            )
            if (index < 4) assertNull(result)
        }

        assertNotNull(result)
        assertEquals(0.50f, result!!.normalizedBoxHeight, 0.0001f)
        assertFalse(tracker.isCollecting)
    }

    @Test
    fun trackerRejectsObjectSwitchAndRestartsWindow() {
        val tracker = DistanceCalibrationCaptureTracker()
        tracker.start()
        repeat(3) { index ->
            tracker.add(sample(time = 1_000L + index * 500L))
        }

        tracker.add(
            sample(label = "chair", ratio = 0.4f, time = 2_500L)
        )
        assertEquals(1, tracker.collectedSampleCount())

        var result: DistanceCalibrationCapture? = null
        repeat(4) { index ->
            result = tracker.add(
                sample(label = "chair", ratio = 0.4f, time = 3_000L + index * 500L)
            )
        }
        assertNotNull(result)
        assertEquals("chair", result!!.referenceLabel)
    }

    @Test
    fun trackerRejectsUnstableSizeAndMovingObject() {
        val sizeTracker = DistanceCalibrationCaptureTracker()
        sizeTracker.start()
        listOf(0.4f, 0.4f, 0.4f, 0.4f, 0.55f).forEachIndexed { index, ratio ->
            sizeTracker.add(sample(ratio = ratio, time = 1_000L + index * 500L))
        }
        assertTrue(sizeTracker.isCollecting)
        assertEquals(1, sizeTracker.collectedSampleCount())

        val movementTracker = DistanceCalibrationCaptureTracker()
        movementTracker.start()
        repeat(4) { index ->
            movementTracker.add(sample(time = 1_000L + index * 500L))
        }
        movementTracker.add(
            sample(x = 0.8f, time = 3_000L)
        )
        assertEquals(1, movementTracker.collectedSampleCount())
    }

    @Test
    fun trackerTimesOutAndCancelsIncompleteCapture() {
        val tracker = DistanceCalibrationCaptureTracker(maxCaptureDurationMs = 10_000L)
        tracker.start(5_000L)

        assertFalse(tracker.hasTimedOut(14_999L))
        assertTrue(tracker.hasTimedOut(15_000L))
        assertFalse(tracker.isCollecting)
        assertEquals(0, tracker.collectedSampleCount())
        assertFalse(tracker.hasTimedOut(16_000L))
    }

    @Test
    fun trackerRejectsSamplesArrivingAfterDeadline() {
        val tracker = DistanceCalibrationCaptureTracker(maxCaptureDurationMs = 2_000L)
        tracker.start(1_000L)

        val result = tracker.add(sample(time = 3_000L))

        assertNull(result)
        assertFalse(tracker.isCollecting)
        assertEquals(0, tracker.collectedSampleCount())
    }

    @Test
    fun trackerRestartsAfterStaleFrameGap() {
        val tracker = DistanceCalibrationCaptureTracker()
        tracker.start()
        repeat(3) { index ->
            tracker.add(sample(time = 1_000L + index * 500L))
        }
        tracker.add(sample(time = 5_000L))
        assertEquals(1, tracker.collectedSampleCount())
    }
}
