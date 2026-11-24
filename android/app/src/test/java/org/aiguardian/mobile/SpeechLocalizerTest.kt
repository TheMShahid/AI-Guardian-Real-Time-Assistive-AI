package org.aiguardian.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLocalizerTest {

    @Test
    fun foregroundNarrationIncludesCalibratedMetresAndSteps() {
        val message = SpeechLocalizer.localize(
            label = "chair",
            direction = "ahead",
            language = "English (US)",
            distanceText = "approximately 1.0 to 1.5 metres, or approximately 2 to 3 steps"
        )

        assertTrue(message.contains("chair"))
        assertTrue(message.contains("1.0 to 1.5 metres"))
        assertTrue(message.contains("2 to 3 steps"))
    }

    @Test
    fun foregroundNarrationDoesNotInventDistanceWhenCalibrationIsUnavailable() {
        val message = SpeechLocalizer.localize(
            label = "chair",
            direction = "ahead",
            language = "English (US)"
        )

        assertTrue(message.contains("chair"))
        assertTrue(!message.contains("metres"))
        assertTrue(!message.contains("steps"))
    }
}
