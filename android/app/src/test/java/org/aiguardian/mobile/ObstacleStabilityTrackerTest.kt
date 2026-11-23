package org.aiguardian.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObstacleStabilityTrackerTest {

    @Test
    fun firstObservationIsNotStable() {
        val tracker = ObstacleStabilityTracker()

        val result = tracker.update(
            currentKeys = setOf("chair|front-left"),
            frameId = 1L,
            nowMs = 100L
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun secondDistinctFrameConfirmsObstacle() {
        val tracker = ObstacleStabilityTracker()

        tracker.update(setOf("chair|front-left"), 1L, 100L)
        val result = tracker.update(setOf("chair|front-left"), 2L, 600L)

        assertEquals(setOf("chair|front-left"), result)
    }

    @Test
    fun sameFrameCannotCountTwice() {
        val tracker = ObstacleStabilityTracker()

        tracker.update(setOf("chair|front-left"), 7L, 100L)
        val duplicateFrameResult =
            tracker.update(setOf("chair|front-left"), 7L, 200L)

        assertTrue(duplicateFrameResult.isEmpty())

        val nextFrameResult =
            tracker.update(setOf("chair|front-left"), 8L, 600L)

        assertEquals(setOf("chair|front-left"), nextFrameResult)
    }

    @Test
    fun observationAfterMaximumGapStartsAgain() {
        val tracker = ObstacleStabilityTracker()

        tracker.update(setOf("chair|front-left"), 1L, 100L)
        val result = tracker.update(
            currentKeys = setOf("chair|front-left"),
            frameId = 2L,
            nowMs = 1_601L
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun onlyCurrentlyObservedStableKeysAreReturned() {
        val tracker = ObstacleStabilityTracker()

        tracker.update(setOf("chair|front-left", "person|front-right"), 1L, 100L)
        tracker.update(setOf("chair|front-left", "person|front-right"), 2L, 600L)

        val result = tracker.update(
            currentKeys = setOf("chair|front-left"),
            frameId = 3L,
            nowMs = 1_100L
        )

        assertEquals(setOf("chair|front-left"), result)
    }

    @Test
    fun resetClearsAllObservationHistory() {
        val tracker = ObstacleStabilityTracker()

        tracker.update(setOf("chair|front-left"), 1L, 100L)
        tracker.update(setOf("chair|front-left"), 2L, 600L)
        tracker.reset()

        val result = tracker.update(
            currentKeys = setOf("chair|front-left"),
            frameId = 3L,
            nowMs = 700L
        )

        assertTrue(result.isEmpty())
    }
}
