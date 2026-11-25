package com.example.bmicalci

import com.example.bmicalci.bmi.BmiCalculator
import com.example.bmicalci.bmi.BmiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmiCalculatorTest {
    @Test fun metricBmi() = assertEquals(22.86f, BmiCalculator.bmi(70f, 175f, true), 0.01f)

    @Test fun imperialBmi() = assertEquals(22.86f, BmiCalculator.bmi(154.32f, 68.9f, false), 0.05f)

    @Test fun categoryBoundariesHaveNoGaps() {
        assertEquals(BmiCategory.UNDERWEIGHT, BmiCalculator.category(18.49f))
        assertEquals(BmiCategory.HEALTHY, BmiCalculator.category(18.5f))
        assertEquals(BmiCategory.HEALTHY, BmiCalculator.category(24.99f))
        // The old XML app treated 24.995..24.999 as "Obese"; now it is the boundary of Overweight.
        assertEquals(BmiCategory.OVERWEIGHT, BmiCalculator.category(25.0f))
        assertEquals(BmiCategory.OVERWEIGHT, BmiCalculator.category(29.99f))
        assertEquals(BmiCategory.OBESE, BmiCalculator.category(30.0f))
    }

    @Test fun bmrMaleAndFemale() {
        // 10*70 + 6.25*175 - 5*30 = 1643.75
        assertEquals(1648, BmiCalculator.bmr(70f, 175f, 30, true, true))
        assertEquals(1482, BmiCalculator.bmr(70f, 175f, 30, false, true))
    }

    @Test fun whtrThreshold() {
        assertTrue(BmiCalculator.isWhtrHealthy(BmiCalculator.whtr(80f, 175f)))
        assertTrue(!BmiCalculator.isWhtrHealthy(BmiCalculator.whtr(95f, 175f)))
    }

    @Test fun idealRange() {
        val (min, max) = BmiCalculator.idealRangeKg(175f, true)
        assertEquals(56.7f, min, 0.1f)
        assertEquals(76.3f, max, 0.1f)
    }

    @Test fun waterGoal() {
        assertEquals(70 * 35 + 600 + 500, BmiCalculator.waterGoalMl(70f, 30, true))
        // child under 14, 25 kg: 1500 + 5*20 = 1600 baseline
        assertEquals(1600 + 600 + 500, BmiCalculator.waterGoalMl(25f, 10, true))
    }

    @Test fun validationRejectsZeroAndMissing() {
        val errors = BmiCalculator.validate(0f, null, 80f, 200, true)
        assertTrue("weight" in errors && "height" in errors && "age" in errors)
        assertTrue(BmiCalculator.validate(70f, 175f, 80f, 30, true).isEmpty())
    }
}
