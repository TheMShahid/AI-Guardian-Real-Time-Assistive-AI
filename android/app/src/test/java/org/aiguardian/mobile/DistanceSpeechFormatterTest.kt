package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class DistanceSpeechFormatterTest {
    @Test
    fun formatsUrduRangeAndSteps() {
        val result = DistanceSpeechFormatter.format(
            VisualDistanceEstimate(1.24f, 2.16f, 2, 4, DistanceQuality.CALIBRATED_APPROXIMATE),
            "Urdu (Pakistan)"
        )
        assertNotNull(result)
        assertTrue(result!!.contains("1.2"))
        assertTrue(result.contains("2.2"))
        assertTrue(result.contains("2 سے 4 قدم"))
    }

    @Test
    fun formatsEnglishRangeWithoutSteps() {
        val result = DistanceSpeechFormatter.format(
            VisualDistanceEstimate(1.0f, 1.5f, null, null, DistanceQuality.CALIBRATED_APPROXIMATE),
            "English (US)"
        )
        assertTrue(result!!.contains("1.0 to 1.5 metres"))
        assertFalse(result.contains("steps"))
    }

    @Test
    fun unavailableEstimateProducesNoDistancePhrase() {
        val result = DistanceSpeechFormatter.format(
            VisualDistanceEstimate(null, null, null, null, DistanceQuality.NOT_CALIBRATED),
            "Urdu (Pakistan)"
        )
        assertNull(result)
    }
}
