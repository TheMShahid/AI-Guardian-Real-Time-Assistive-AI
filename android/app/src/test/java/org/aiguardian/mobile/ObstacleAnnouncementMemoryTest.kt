package org.aiguardian.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObstacleAnnouncementMemoryTest {

    @Test
    fun firstStableVisualEventCanBeAnnounced() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)

        assertTrue(memory.shouldAnnounce("chair|ahead", severity = 0, nowMs = 100L))
    }

    @Test
    fun continuousDetectionDoesNotRepeatAtSameSeverity() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L)

        memory.updateSeen(setOf("chair|ahead"), nowMs = 500L)
        memory.updateSeen(setOf("chair|ahead"), nowMs = 1_000L)

        assertFalse(memory.shouldAnnounce("chair|ahead", severity = 0, nowMs = 1_000L))
    }

    @Test
    fun higherSeverityCanBeAnnouncedOnceAfterEscalationCooldown() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("person|ahead"), nowMs = 100L)
        memory.markAnnounced("person|ahead", severity = 0, nowMs = 100L)

        assertFalse(memory.shouldAnnounce("person|ahead", severity = 2, nowMs = 1_000L))
        assertTrue(memory.shouldAnnounce("person|ahead", severity = 2, nowMs = 1_700L))
        memory.markAnnounced("person|ahead", severity = 2, nowMs = 1_700L)
        assertFalse(memory.shouldAnnounce("person|ahead", severity = 2, nowMs = 4_000L))
    }

    @Test
    fun eventIsRearmedAfterSufficientAbsence() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L)

        memory.updateSeen(emptySet(), nowMs = 200L)
        memory.updateSeen(setOf("chair|ahead"), nowMs = 5_200L)

        assertTrue(memory.shouldAnnounce("chair|ahead", severity = 0, nowMs = 5_200L))
    }

    @Test
    fun briefDetectionDropoutDoesNotRearmEvent() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L)

        memory.updateSeen(emptySet(), nowMs = 200L)
        memory.updateSeen(setOf("chair|ahead"), nowMs = 2_000L)

        assertFalse(memory.shouldAnnounce("chair|ahead", severity = 0, nowMs = 2_000L))
    }

    @Test
    fun resetAllowsCurrentVisualEventsToBeAnnouncedAgain() {
        val memory = ObstacleAnnouncementMemory()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L)

        memory.reset()
        memory.updateSeen(setOf("chair|ahead"), nowMs = 200L)

        assertTrue(memory.shouldAnnounce("chair|ahead", severity = 0, nowMs = 200L))
    }
    @Test
    fun materialDistanceReductionCanTriggerOneUsefulUpdate() {
        val memory = ObstacleAnnouncementMemory()
        val farther = VisualDistanceEstimate(
            minimumMeters = 2.0f,
            maximumMeters = 3.0f,
            minimumSteps = 3,
            maximumSteps = 5,
            quality = DistanceQuality.CALIBRATED_APPROXIMATE
        )
        val closer = VisualDistanceEstimate(
            minimumMeters = 1.0f,
            maximumMeters = 1.5f,
            minimumSteps = 2,
            maximumSteps = 3,
            quality = DistanceQuality.CALIBRATED_APPROXIMATE
        )

        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L, estimate = farther)
        memory.updateSeen(setOf("chair|ahead"), nowMs = 2_000L)

        assertTrue(
            memory.shouldAnnounce(
                "chair|ahead",
                severity = 0,
                nowMs = 2_000L,
                estimate = closer
            )
        )
    }

    @Test
    fun smallDistanceFluctuationDoesNotCauseRepeatAnnouncement() {
        val memory = ObstacleAnnouncementMemory()
        val farther = VisualDistanceEstimate(
            minimumMeters = 2.0f,
            maximumMeters = 3.0f,
            minimumSteps = 3,
            maximumSteps = 5,
            quality = DistanceQuality.CALIBRATED_APPROXIMATE
        )
        val slightlyCloser = VisualDistanceEstimate(
            minimumMeters = 1.7f,
            maximumMeters = 2.5f,
            minimumSteps = 3,
            maximumSteps = 4,
            quality = DistanceQuality.CALIBRATED_APPROXIMATE
        )

        memory.updateSeen(setOf("chair|ahead"), nowMs = 100L)
        memory.markAnnounced("chair|ahead", severity = 0, nowMs = 100L, estimate = farther)
        memory.updateSeen(setOf("chair|ahead"), nowMs = 2_000L)

        assertFalse(
            memory.shouldAnnounce(
                "chair|ahead",
                severity = 0,
                nowMs = 2_000L,
                estimate = slightlyCloser
            )
        )
    }

}
