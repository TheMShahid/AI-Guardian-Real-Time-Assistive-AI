package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class SpeechLocalizerDistanceTest {
    @Test
    fun calibratedUrduAlertIncludesEstimatedRange() {
        val message = SpeechLocalizer.localizeObstacle(
            label = "chair",
            direction = "front-center",
            apparentProximity = "close-looking",
            language = "Urdu (Pakistan)",
            distanceText = "تقریباً 1.2 سے 2.2 میٹر، یعنی 2 سے 4 قدم"
        )
        assertTrue(message.contains("کرسی"))
        assertTrue(message.contains("اندازاً فاصلہ"))
        assertTrue(message.contains("2 سے 4 قدم"))
    }

    @Test
    fun uncalibratedEnglishAlertDoesNotInventDistance() {
        val message = SpeechLocalizer.localizeObstacle(
            label = "backpack",
            direction = "front-left",
            apparentProximity = "close-looking",
            language = "English (US)"
        )
        assertTrue(message.contains("Exact distance is unknown"))
        assertFalse(message.contains("metres"))
        assertFalse(message.contains("steps"))
    }

    @Test
    fun approachingWarningKeepsStopInstructionAndDistance() {
        val message = SpeechLocalizer.localizeObstacle(
            label = "person",
            direction = "front-right",
            apparentProximity = "close-looking",
            language = "English (US)",
            appearsToApproach = true,
            distanceText = "approximately 1.0 to 1.5 metres"
        )
        assertTrue(message.contains("getting closer"))
        assertTrue(message.contains("Estimated distance"))
        assertTrue(message.contains("Please stop"))
    }
}
