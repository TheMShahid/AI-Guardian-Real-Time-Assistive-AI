package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class DetectionDepthSamplerTest {

    @Test
    fun returnsRobustMetricMedianForAlignedDetectionRegion() {
        val frame = MetricDepthFrame(10, 10, 1_000_000_000L, FloatArray(100) { 2.4f })

        val sample = DetectionDepthSampler.sample(
            frame, 1_000_000_000L, 0.1f, 0.1f, 0.9f, 0.9f
        )

        assertTrue(sample.isUsable)
        assertEquals(2.4f, sample.medianMeters!!, 0.001f)
        assertEquals(2.4f, sample.lowerQuartileMeters!!, 0.001f)
        assertEquals(2.4f, sample.upperQuartileMeters!!, 0.001f)
        assertEquals(1f, sample.validPixelRatio, 0.001f)
    }

    @Test
    fun rejectsDepthFrameThatIsNotTemporallyAligned() {
        val frame = MetricDepthFrame(8, 8, 1_000_000_000L, FloatArray(64) { 1.5f })

        val sample = DetectionDepthSampler.sample(
            frame, 1_500_000_000L, 0.1f, 0.1f, 0.9f, 0.9f
        )

        assertEquals(DepthSampleQuality.STALE_FRAME, sample.quality)
        assertNull(sample.medianMeters)
    }

    @Test
    fun rejectsRegionWithMostlyInvalidDepthPixels() {
        val frame = MetricDepthFrame(10, 10, 10L, FloatArray(100) { Float.NaN })

        val sample = DetectionDepthSampler.sample(
            frame, 10L, 0.1f, 0.1f, 0.9f, 0.9f
        )

        assertEquals(DepthSampleQuality.LOW_VALID_COVERAGE, sample.quality)
        assertFalse(sample.isUsable)
    }

    @Test
    fun flagsMixedDepthRegionInsteadOfReportingUnqualifiedDistance() {
        val values = FloatArray(100) { index ->
            if (index % 2 == 0) 1.0f else 5.0f
        }
        val frame = MetricDepthFrame(10, 10, 10L, values)

        val sample = DetectionDepthSampler.sample(
            frame, 10L, 0.1f, 0.1f, 0.9f, 0.9f
        )

        assertEquals(DepthSampleQuality.HIGH_VARIANCE, sample.quality)
        assertFalse(sample.isUsable)
    }

    @Test
    fun rejectsInvalidDetectionBox() {
        val frame = MetricDepthFrame(10, 10, 10L, FloatArray(100) { 2f })

        val sample = DetectionDepthSampler.sample(
            frame, 10L, 0.8f, 0.1f, 0.2f, 0.9f
        )

        assertEquals(DepthSampleQuality.INVALID_REGION, sample.quality)
        assertNull(sample.medianMeters)
    }

    @Test
    fun ignoresInvalidDepthOutliersWhenCoverageRemainsSufficient() {
        val values = FloatArray(100) { 2.0f }
        values[44] = Float.NaN
        values[45] = Float.POSITIVE_INFINITY
        val frame = MetricDepthFrame(10, 10, 10L, values)

        val sample = DetectionDepthSampler.sample(
            frame, 10L, 0.1f, 0.1f, 0.9f, 0.9f
        )

        assertTrue(sample.isUsable)
        assertEquals(2.0f, sample.medianMeters!!, 0.001f)
    }
}
