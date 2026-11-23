package org.aiguardian.mobile

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test

class ObstacleApproachTrackerTest {

    private fun obstacle(height: Float) = PrioritizedObstacle(
        detection = Detection(
            classId = 56,
            label = "chair",
            confidence = 0.90f,
            boundingBox = RectF(100f, 100f, 300f, 100f + height * 1000f),
            frameWidth = 1000,
            frameHeight = 1000
        ),
        direction = "front-center",
        relevanceScore = 0.8f,
        apparentProximity = "close-looking",
        normalizedHeight = height
    )

    @Test
    fun requiresSustainedGrowthAcrossDistinctFrames() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        assertFalse(key in tracker.update(listOf(obstacle(0.20f)), 1L, 100L))
        assertFalse(key in tracker.update(listOf(obstacle(0.24f)), 2L, 200L))
        assertTrue(key in tracker.update(listOf(obstacle(0.29f)), 3L, 300L))
    }

    @Test
    fun duplicateFrameDoesNotCountTwice() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(obstacle(0.20f)), 1L, 100L)
        tracker.update(listOf(obstacle(0.24f)), 2L, 200L)
        assertFalse(key in tracker.update(listOf(obstacle(0.24f)), 2L, 210L))
    }

    @Test
    fun shrinkingOrSmallJitterDoesNotTriggerApproach() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(obstacle(0.30f)), 1L, 100L)
        tracker.update(listOf(obstacle(0.31f)), 2L, 200L)
        assertFalse(key in tracker.update(listOf(obstacle(0.28f)), 3L, 300L))
    }

    @Test
    fun staleObservationIsDiscarded() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(obstacle(0.20f)), 1L, 100L)
        assertFalse(key in tracker.update(listOf(obstacle(0.24f)), 2L, 2_000L))
    }

    @Test
    fun resetClearsTrendHistory() {
        val tracker = ObstacleApproachTracker()
        val key = "chair|front-center"

        tracker.update(listOf(obstacle(0.20f)), 1L, 100L)
        tracker.update(listOf(obstacle(0.24f)), 2L, 200L)
        tracker.reset()

        assertFalse(key in tracker.update(listOf(obstacle(0.29f)), 3L, 300L))
    }
}
