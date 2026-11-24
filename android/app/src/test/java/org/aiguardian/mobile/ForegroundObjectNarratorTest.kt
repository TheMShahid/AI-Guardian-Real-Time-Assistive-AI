package org.aiguardian.mobile

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

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

        assertNull(tracker.observe(candidate))
        val stable = tracker.observe(candidate)
        assertNotNull(stable)

        tracker.markAnnounced("chair")
        assertNull(tracker.observe(candidate))
        assertNull(tracker.observe(candidate))
    }

    @Test
    fun trackerAnnouncesWhenProminentObjectChanges() {
        val tracker = ForegroundObjectAnnouncementTracker()
        val chair = ForegroundObject("chair", "ahead", 0.8f)
        val person = ForegroundObject("person", "left", 0.9f)

        tracker.observe(chair)
        val stableChair = tracker.observe(chair)
        assertNotNull(stableChair)
        tracker.markAnnounced("chair")

        assertNull(tracker.observe(person))
        assertEquals("person", tracker.observe(person)?.label)
    }
}
