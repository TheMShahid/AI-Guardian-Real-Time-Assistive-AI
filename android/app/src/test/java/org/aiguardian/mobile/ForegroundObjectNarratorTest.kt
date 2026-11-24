package org.aiguardian.mobile

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundObjectNarratorTest {

    private fun detection(
        label: String,
        confidence: Float = 0.80f,
        left: Float = 300f,
        top: Float = 180f,
        right: Float = 700f,
        bottom: Float = 820f
    ) = Detection(
        classId = 0,
        label = label,
        confidence = confidence,
        boundingBox = RectF(left, top, right, bottom),
        frameWidth = 1000,
        frameHeight = 1000
    )

    @Test
    fun selectsProminentCentreObjectEvenWhenItIsNotInWalkingCorridor() {
        val selected = ForegroundObjectSelector.select(
            listOf(
                detection(
                    label = "book",
                    left = 420f,
                    top = 80f,
                    right = 580f,
                    bottom = 260f
                ),
                detection(
                    label = "chair",
                    left = 100f,
                    top = 200f,
                    right = 360f,
                    bottom = 650f
                )
            )
        )

        assertNotNull(selected)
        assertEquals("chair", selected?.label)
    }

    @Test
    fun ignoresLowConfidenceAndInvalidDetections() {
        val selected = ForegroundObjectSelector.select(
            listOf(
                detection("person", confidence = 0.39f),
                detection("chair", left = 400f, right = 400f)
            )
        )

        assertNull(selected)
    }

    @Test
    fun prefersCentralProminentObjectOverSmallEdgeObject() {
        val selected = ForegroundObjectSelector.select(
            listOf(
                detection(
                    label = "bottle",
                    confidence = 0.95f,
                    left = 0f,
                    top = 0f,
                    right = 90f,
                    bottom = 140f
                ),
                detection(
                    label = "laptop",
                    confidence = 0.78f,
                    left = 260f,
                    top = 180f,
                    right = 740f,
                    bottom = 820f
                )
            )
        )

        assertEquals("laptop", selected?.label)
        assertEquals("ahead", selected?.direction)
    }

    @Test
    fun trackerRequiresTwoFramesAndDoesNotRepeatSameObject() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val candidate = ForegroundObject("chair", "ahead", 0.8f)

        assertNull(tracker.observe(candidate, nowMs = 0L))
        val stable = tracker.observe(candidate, nowMs = 100L)
        assertNotNull(stable)

        tracker.markAnnounced(candidate, nowMs = 100L)
        assertNull(tracker.observe(candidate, nowMs = 200L))
        assertNull(tracker.observe(candidate, nowMs = 300L))
    }

    @Test
    fun trackerAnnouncesWhenProminentObjectChanges() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val chair = ForegroundObject("chair", "ahead", 0.8f)
        val person = ForegroundObject("person", "left", 0.9f)

        tracker.observe(chair, nowMs = 0L)
        val stableChair = tracker.observe(chair, nowMs = 100L)
        assertNotNull(stableChair)
        tracker.markAnnounced(chair, nowMs = 100L)

        assertNull(tracker.observe(person, nowMs = 200L))
        assertEquals("person", tracker.observe(person, nowMs = 300L)?.label)
    }
    @Test
    fun sameObjectCanBeAnnouncedAgainOnlyAfterMeaningfulAbsence() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val chair = ForegroundObject("chair", "ahead", 0.8f)

        tracker.observe(chair, nowMs = 0L)
        assertNotNull(tracker.observe(chair, nowMs = 100L))
        tracker.markAnnounced(chair, nowMs = 100L)

        assertNull(tracker.observe(null, nowMs = 200L))
        assertNull(tracker.observe(chair, nowMs = 6_000L))
        assertNotNull(tracker.observe(chair, nowMs = 6_100L))
    }

    @Test
    fun sameLabelChangingDirectionDoesNotRepeatAnnouncement() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val leftChair = ForegroundObject("chair", "left", 0.8f)
        val rightChair = ForegroundObject("chair", "right", 0.8f)

        tracker.observe(leftChair, nowMs = 0L)
        assertNotNull(tracker.observe(leftChair, nowMs = 100L))
        tracker.markAnnounced(leftChair, nowMs = 100L)

        assertNull(tracker.observe(rightChair, nowMs = 200L))
        assertNull(tracker.observe(rightChair, nowMs = 300L))
    }

    @Test
    fun selectedObjectCarriesNormalizedHeightForDistanceEstimation() {
        val selected = ForegroundObjectSelector.select(
            listOf(
                detection(
                    label = "chair",
                    top = 200f,
                    bottom = 700f
                )
            )
        )

        assertNotNull(selected)
        assertEquals(0.5f, selected!!.normalizedBoxHeight, 0.001f)
    }

    @Test
    fun changingToANewObjectClassMakesItEligibleForNarration() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val chair = ForegroundObject("chair", "ahead", 0.8f)
        val bottle = ForegroundObject("bottle", "ahead", 0.8f)

        tracker.observe(chair, nowMs = 0L)
        assertNotNull(tracker.observe(chair, nowMs = 100L))
        tracker.markAnnounced(chair, nowMs = 100L)

        assertNull(tracker.observe(bottle, nowMs = 200L))
        assertEquals("bottle", tracker.observe(bottle, nowMs = 300L)?.label)
    }

    @Test
    fun sameClassAtANewImagePositionIsTreatedAsNewVisualFocus() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val firstChair = ForegroundObject(
            label = "chair",
            direction = "ahead",
            score = 0.8f,
            normalizedBoxHeight = 0.35f,
            centerXRatio = 0.35f,
            centerYRatio = 0.55f,
            normalizedBoxWidth = 0.25f
        )
        val secondChair = firstChair.copy(
            direction = "right",
            centerXRatio = 0.82f
        )

        tracker.observe(firstChair, nowMs = 0L)
        assertNotNull(tracker.observe(firstChair, nowMs = 100L))
        tracker.markAnnounced(firstChair, nowMs = 100L)

        assertNull(tracker.observe(secondChair, nowMs = 200L))
        assertNotNull(tracker.observe(secondChair, nowMs = 300L))
    }

}
