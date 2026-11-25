package org.aiguardian.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnobservedSectorAnnouncementTrackerTest {
    @Test
    fun requiresSeveralDistinctFramesBeforeReportingSector() {
        val tracker = UnobservedSectorAnnouncementTracker(requiredObservations = 3)
        assertNull(tracker.observe(setOf("left"), 1L, 100L))
        assertNull(tracker.observe(setOf("left"), 2L, 300L))
        assertEquals("left", tracker.observe(setOf("left"), 3L, 500L))
    }

    @Test
    fun duplicateFrameDoesNotIncreaseObservationCount() {
        val tracker = UnobservedSectorAnnouncementTracker(requiredObservations = 2)
        assertNull(tracker.observe(setOf("right"), 7L, 100L))
        assertNull(tracker.observe(setOf("right"), 7L, 200L))
        assertEquals("right", tracker.observe(setOf("right"), 8L, 300L))
    }

    @Test
    fun sectorMustRemainUnobservedAcrossTheObservationWindow() {
        val tracker = UnobservedSectorAnnouncementTracker(requiredObservations = 3)
        assertNull(tracker.observe(setOf("left"), 1L, 100L))
        assertNull(tracker.observe(emptySet(), 2L, 300L))
        assertNull(tracker.observe(setOf("left"), 3L, 500L))
        assertNull(tracker.observe(setOf("left"), 4L, 700L))
        assertEquals("left", tracker.observe(setOf("left"), 5L, 900L))
    }

    @Test
    fun repeatAnnouncementIsRateLimited() {
        val tracker = UnobservedSectorAnnouncementTracker(
            requiredObservations = 1,
            repeatIntervalMs = 1_000L
        )
        assertEquals("left", tracker.observe(setOf("left"), 1L, 100L))
        tracker.markAnnounced("left", 100L)
        assertNull(tracker.observe(setOf("left"), 2L, 500L))
        assertEquals("left", tracker.observe(setOf("left"), 3L, 1_100L))
    }

    @Test
    fun unsupportedSectorNamesAreIgnored() {
        val tracker = UnobservedSectorAnnouncementTracker(requiredObservations = 1)
        assertNull(tracker.observe(setOf("behind", "floor"), 1L, 100L))
    }
}
