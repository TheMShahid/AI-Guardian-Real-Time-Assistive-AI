package org.aiguardian.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundDistanceUpdateTrackerTest {
    private val chair = ForegroundObject(
        label = "chair",
        direction = "ahead",
        score = 0.9f,
        normalizedBoxHeight = 0.3f,
        centerXRatio = 0.5f,
        centerYRatio = 0.5f,
        normalizedBoxWidth = 0.2f
    )

    private fun estimate(min: Float, max: Float, minSteps: Int?, maxSteps: Int?) =
        VisualDistanceEstimate(
            min, max, minSteps, maxSteps, DistanceQuality.CALIBRATED_APPROXIMATE
        )

    @Test
    fun materialDistanceReductionCanBeAnnounced() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, estimate(2f, 3f, 3, 5), 100L)

        assertTrue(
            tracker.shouldAnnounce(chair, estimate(1.2f, 1.8f, 2, 3), 2_200L)
        )
    }

    @Test
    fun smallDistanceFluctuationDoesNotTriggerUpdate() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, estimate(2f, 3f, 3, 5), 100L)

        assertFalse(
            tracker.shouldAnnounce(chair, estimate(1.7f, 2.5f, 3, 4), 2_200L)
        )
    }

    @Test
    fun twoStepReductionCanTriggerUpdate() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, estimate(2f, 3f, 5, 7), 100L)

        assertTrue(
            tracker.shouldAnnounce(chair, estimate(1.7f, 2.5f, 3, 5), 2_200L)
        )
    }

    @Test
    fun doesNotSpeakUncalibratedDistance() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, null, 100L)

        val uncalibrated = VisualDistanceEstimate(
            null, null, null, null, DistanceQuality.NOT_CALIBRATED
        )
        assertFalse(tracker.shouldAnnounce(chair, uncalibrated, 2_200L))
    }

    @Test
    fun differentForegroundKeyDoesNotReuseOldBaseline() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, estimate(2f, 3f, 3, 5), 100L)
        val person = chair.copy(label = "person")

        assertFalse(
            tracker.shouldAnnounce(person, estimate(1f, 1.5f, 1, 2), 2_200L)
        )
    }

    @Test
    fun cooldownPreventsRapidDistanceUpdates() {
        val tracker = ForegroundDistanceUpdateTracker()
        tracker.markAnnounced(chair, estimate(2f, 3f, 3, 5), 100L)

        assertFalse(
            tracker.shouldAnnounce(chair, estimate(1f, 1.5f, 1, 2), 1_000L)
        )
    }
}
