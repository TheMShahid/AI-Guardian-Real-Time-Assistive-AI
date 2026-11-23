package org.aiguardian.mobile

import org.junit.Assert.*
import org.junit.Test

class ObstacleApproachTrackerTest {

    private fun observation(height: Float) =
        ObstacleSizeObservation("chair|front-center", height)

    @Test
    fun requiresSustainedGrowthAcrossDistinctFrames() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        assertFalse(key in tracker.update(listOf(observation(0.20f)), 1L, 100L))
        assertFalse(key in tracker.update(listOf(observation(0.24f)), 2L, 200L))
        assertTrue(key in tracker.update(listOf(observation(0.29f)), 3L, 300L))
    }

    @Test
    fun duplicateFrameDoesNotCountTwice() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(observation(0.20f)), 1L, 100L)
        tracker.update(listOf(observation(0.24f)), 2L, 200L)
        assertFalse(key in tracker.update(listOf(observation(0.24f)), 2L, 210L))
    }

    @Test
    fun shrinkingOrSmallJitterDoesNotTriggerApproach() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(observation(0.30f)), 1L, 100L)
        tracker.update(listOf(observation(0.31f)), 2L, 200L)
        assertFalse(key in tracker.update(listOf(observation(0.28f)), 3L, 300L))
    }

    @Test
    fun staleObservationIsDiscarded() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(observation(0.20f)), 1L, 100L)
        assertFalse(key in tracker.update(listOf(observation(0.24f)), 2L, 2_000L))
    }

    @Test
    fun resetClearsTrendHistory() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(observation(0.20f)), 1L, 100L)
        tracker.update(listOf(observation(0.24f)), 2L, 200L)
        tracker.reset()

        assertFalse(key in tracker.update(listOf(observation(0.29f)), 3L, 300L))
    }
}
