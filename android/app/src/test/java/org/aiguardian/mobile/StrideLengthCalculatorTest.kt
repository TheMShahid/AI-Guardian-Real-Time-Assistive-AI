package org.aiguardian.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrideLengthCalculatorTest {

    @Test
    fun calculatesAverageStrideFromMeasuredDistanceAndSteps() {
        val stride = StrideLengthCalculator.calculate(
            walkedDistanceMeters = 10f,
            stepCount = 14
        )

        assertEquals(10f / 14f, stride!!, 0.0001f)
    }

    @Test
    fun rejectsNonFiniteOrOutOfRangeDistance() {
        assertNull(StrideLengthCalculator.calculate(Float.NaN, 12))
        assertNull(StrideLengthCalculator.calculate(0.5f, 12))
        assertNull(StrideLengthCalculator.calculate(101f, 100))
    }

    @Test
    fun rejectsInvalidStepCounts() {
        assertNull(StrideLengthCalculator.calculate(10f, 0))
        assertNull(StrideLengthCalculator.calculate(10f, 1_001))
    }

    @Test
    fun rejectsPhysiologicallyUnsupportedCalculatedStride() {
        assertNull(StrideLengthCalculator.calculate(1f, 20))
        assertNull(StrideLengthCalculator.calculate(10f, 5))
    }
}
