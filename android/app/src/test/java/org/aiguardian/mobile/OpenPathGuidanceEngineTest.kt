package org.aiguardian.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenPathGuidanceEngineTest {

    private fun evidence(
        left: Float = 0.90f,
        center: Float = 0.55f,
        right: Float = 0.60f,
        blockedLeft: Float = 0.05f,
        blockedCenter: Float = 0.05f,
        blockedRight: Float = 0.05f,
        ground: Float = 0.85f,
        confidence: Float = 0.90f
    ) = listOf(
        OpenPathGuidanceEngine.ZoneEvidence(
            OpenPathGuidanceEngine.Zone.FAR_LEFT, ground, confidence, blockedLeft
        ),
        OpenPathGuidanceEngine.ZoneEvidence(
            OpenPathGuidanceEngine.Zone.LEFT, ground, confidence, blockedLeft
        ),
        OpenPathGuidanceEngine.ZoneEvidence(
            OpenPathGuidanceEngine.Zone.CENTER, ground, confidence, blockedCenter
        ),
        OpenPathGuidanceEngine.ZoneEvidence(
            OpenPathGuidanceEngine.Zone.RIGHT, ground, confidence, blockedRight
        ),
        OpenPathGuidanceEngine.ZoneEvidence(
            OpenPathGuidanceEngine.Zone.FAR_RIGHT, ground, confidence, blockedRight
        )
    ).map { item ->
        when (item.zone) {
            OpenPathGuidanceEngine.Zone.FAR_LEFT ->
                item.copy(visibleGroundRatio = left)
            OpenPathGuidanceEngine.Zone.LEFT ->
                item.copy(visibleGroundRatio = left * 0.75f)
            OpenPathGuidanceEngine.Zone.CENTER ->
                item.copy(visibleGroundRatio = center)
            OpenPathGuidanceEngine.Zone.RIGHT ->
                item.copy(visibleGroundRatio = right)
            OpenPathGuidanceEngine.Zone.FAR_RIGHT ->
                item.copy(visibleGroundRatio = right * 0.75f)
        }
    }

    @Test
    fun guidesOnlyAfterThreeStableFrames() {
        val engine = OpenPathGuidanceEngine()
        assertEquals(
            OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE,
            engine.evaluate(evidence(), 1).status
        )
        assertEquals(
            OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE,
            engine.evaluate(evidence(), 2).status
        )
        val decision = engine.evaluate(evidence(), 3)
        assertEquals(OpenPathGuidanceEngine.Status.GUIDE_DIRECTION, decision.status)
        assertEquals(OpenPathGuidanceEngine.Zone.FAR_LEFT, decision.direction)
    }

    @Test
    fun missingGroundEvidenceNeverProducesDirection() {
        val engine = OpenPathGuidanceEngine()
        val weakEvidence = evidence().map { it.copy(visibleGroundRatio = 0.20f) }
        repeat(5) { frame ->
            assertEquals(
                OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE,
                engine.evaluate(weakEvidence, frame.toLong()).status
            )
        }
    }

    @Test
    fun allBlockedCorridorsProduceNoOpenRoute() {
        val engine = OpenPathGuidanceEngine()
        val blocked = evidence(
            blockedLeft = 0.80f,
            blockedCenter = 0.80f,
            blockedRight = 0.80f
        )
        assertEquals(
            OpenPathGuidanceEngine.Status.NO_COMPARATIVELY_OPEN_ROUTE,
            engine.evaluate(blocked, 1).status
        )
    }

    @Test
    fun incompleteZoneCoverageNeverProducesDirection() {
        val engine = OpenPathGuidanceEngine()
        val incomplete = evidence().dropLast(1)
        repeat(4) { frame ->
            assertEquals(
                OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE,
                engine.evaluate(incomplete, frame.toLong()).status
            )
        }
    }

    @Test
    fun similarScoresAreTreatedAsAmbiguous() {
        val engine = OpenPathGuidanceEngine()
        val balanced = evidence(left = 0.80f, center = 0.80f, right = 0.80f)
        repeat(4) { frame ->
            assertEquals(
                OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE,
                engine.evaluate(balanced, frame.toLong()).status
            )
        }
    }

    @Test
    fun sameFrameCannotSatisfyTemporalStability() {
        val engine = OpenPathGuidanceEngine()
        engine.evaluate(evidence(), 7)
        engine.evaluate(evidence(), 7)
        val decision = engine.evaluate(evidence(), 8)
        assertEquals(OpenPathGuidanceEngine.Status.NEED_MORE_EVIDENCE, decision.status)
        assertNull(decision.direction)
    }
}
