package org.aiguardian.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptySceneAnnouncementTrackerTest {
    @Test
    fun waitsForFourDistinctEmptyFrames() {
        val tracker = EmptySceneAnnouncementTracker()
        tracker.observe(false, frameId = 1L, nowMs = 100L)
        tracker.observe(false, frameId = 2L, nowMs = 300L)
        tracker.observe(false, frameId = 3L, nowMs = 500L)
        assertFalse(tracker.shouldAnnounce(500L))

        tracker.observe(false, frameId = 4L, nowMs = 700L)
        assertTrue(tracker.shouldAnnounce(700L))
    }

    @Test
    fun duplicateFrameDoesNotCountTwice() {
        val tracker = EmptySceneAnnouncementTracker(requiredEmptyFrames = 2)
        tracker.observe(false, frameId = 7L, nowMs = 100L)
        tracker.observe(false, frameId = 7L, nowMs = 200L)
        assertFalse(tracker.shouldAnnounce(200L))
    }

    @Test
    fun detectionsResetTheEmptyEpisode() {
        val tracker = EmptySceneAnnouncementTracker(requiredEmptyFrames = 2)
        tracker.observe(false, 1L, 100L)
        tracker.observe(true, 2L, 200L)
        tracker.observe(false, 3L, 300L)
        assertFalse(tracker.shouldAnnounce(300L))
        tracker.observe(false, 4L, 400L)
        assertTrue(tracker.shouldAnnounce(400L))
    }

    @Test
    fun emptyFeedbackRepeatsOnlyAfterCooldown() {
        val tracker = EmptySceneAnnouncementTracker(requiredEmptyFrames = 1)
        tracker.observe(false, 1L, 100L)
        assertTrue(tracker.shouldAnnounce(100L))
        tracker.markAnnounced(100L)
        assertFalse(tracker.shouldAnnounce(14_999L))
        assertTrue(tracker.shouldAnnounce(15_100L))
    }

    @Test
    fun longFrameGapRequiresFreshStableObservations() {
        val tracker = EmptySceneAnnouncementTracker(requiredEmptyFrames = 2)
        tracker.observe(false, 1L, 100L)
        tracker.observe(false, 2L, 2_000L)
        assertFalse(tracker.shouldAnnounce(2_000L))
        tracker.observe(false, 3L, 2_200L)
        assertTrue(tracker.shouldAnnounce(2_200L))
    }
}
