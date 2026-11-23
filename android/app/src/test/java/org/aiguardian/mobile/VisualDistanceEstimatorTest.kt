package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class VisualDistanceEstimatorTest {

    @Test
    fun calibrationUsesKnownReferenceGeometry() {
        val calibration = VisualDistanceEstimator.calibrate(
            referenceLabel = "person",
            normalizedBoxHeight = 0.50f,
            knownHeightMeters = 1.70f,
            measuredDistanceMeters = 2.0f,
            nowEpochMs = 100L
        )

        assertNotNull(calibration)
        assertEquals(0.5882f, calibration!!.focalRatio, 0.001f)
        assertEquals("person", calibration.referenceLabel)
    }

    @Test
    fun estimateReturnsRangeAndStepBandWhenCalibrated() {
        val calibration = VisualDistanceEstimator.calibrate(
            referenceLabel = "person",
            normalizedBoxHeight = 0.50f,
            knownHeightMeters = 1.70f,
            measuredDistanceMeters = 2.0f,
            nowEpochMs = 100L
        )!!

        val estimate = VisualDistanceEstimator.estimate(
            label = "chair",
            normalizedBoxHeight = 0.25f,
            calibration = calibration,
            strideLengthMeters = 0.70f
        )

        assertEquals(DistanceQuality.CALIBRATED_APPROXIMATE, estimate.quality)
        assertNotNull(estimate.minimumMeters)
        assertNotNull(estimate.maximumMeters)
        assertTrue(estimate.minimumMeters!! < estimate.maximumMeters!!)
        assertNotNull(estimate.minimumSteps)
        assertNotNull(estimate.maximumSteps)
        assertTrue(estimate.minimumSteps!! <= estimate.maximumSteps!!)
    }

    @Test
    fun estimateNeverInventsRangeWithoutCalibration() {
        val estimate = VisualDistanceEstimator.estimate(
            label = "backpack",
            normalizedBoxHeight = 0.20f,
            calibration = null,
            strideLengthMeters = 0.70f
        )

        assertEquals(DistanceQuality.NOT_CALIBRATED, estimate.quality)
        assertNull(estimate.minimumMeters)
        assertNull(estimate.maximumMeters)
        assertNull(estimate.minimumSteps)
        assertNull(estimate.maximumSteps)
    }

    @Test
    fun unsupportedLabelsDoNotReceiveFabricatedDistance() {
        val calibration = VisualDistanceEstimator.calibrate(
            referenceLabel = "person",
            normalizedBoxHeight = 0.50f,
            knownHeightMeters = 1.70f,
            measuredDistanceMeters = 2.0f,
            nowEpochMs = 100L
        )!!

        val estimate = VisualDistanceEstimator.estimate(
            label = "traffic light",
            normalizedBoxHeight = 0.25f,
            calibration = calibration
        )

        assertEquals(DistanceQuality.UNSUPPORTED_OBJECT, estimate.quality)
        assertNull(estimate.minimumMeters)
    }

    @Test
    fun invalidCalibrationInputsAreRejected() {
        assertNull(
            VisualDistanceEstimator.calibrate(
                referenceLabel = "chair",
                normalizedBoxHeight = 0f,
                knownHeightMeters = 0.9f,
                measuredDistanceMeters = 2f,
                nowEpochMs = 1L
            )
        )
    }

    @Test
    fun invalidStrideDoesNotProduceStepCount() {
        val calibration = VisualDistanceEstimator.calibrate(
            referenceLabel = "person",
            normalizedBoxHeight = 0.50f,
            knownHeightMeters = 1.70f,
            measuredDistanceMeters = 2.0f,
            nowEpochMs = 100L
        )!!

        val estimate = VisualDistanceEstimator.estimate(
            label = "person",
            normalizedBoxHeight = 0.40f,
            calibration = calibration,
            strideLengthMeters = 0.01f
        )

        assertEquals(DistanceQuality.CALIBRATED_APPROXIMATE, estimate.quality)
        assertNull(estimate.minimumSteps)
        assertNull(estimate.maximumSteps)
    }
}
