package com.example.bmicalci.bmi

import kotlin.math.roundToInt

enum class BmiCategory(val label: String, val actionPlan: String) {
    UNDERWEIGHT(
        "Underweight",
        "Action Plan: Focus on nutrient-dense meals, progressive strength tracking, and healthy caloric surpluses.",
    ),
    HEALTHY(
        "Healthy",
        "Action Plan: Excellent balance! Maintain consistent physical activity and a well-proportioned clean diet.",
    ),
    OVERWEIGHT(
        "Overweight",
        "Action Plan: Consider monitoring portions, tracking cardiovascular metrics, and setting light calorie deficits.",
    ),
    OBESE(
        "Obese",
        "Action Plan: Prioritize routine low-impact cardio, balanced macro-tracking, and sustained metabolic care.",
    ),
}

/** Pure, unit-testable health maths. Inputs are in the user's chosen unit system. */
object BmiCalculator {
    private const val LBS_TO_KG = 0.453592f
    private const val IN_TO_CM = 2.54f
    private const val KG_TO_LBS = 2.20462f

    fun toKg(weight: Float, metric: Boolean) = if (metric) weight else weight * LBS_TO_KG
    fun toCm(height: Float, metric: Boolean) = if (metric) height else height * IN_TO_CM

    fun bmi(weight: Float, height: Float, metric: Boolean): Float {
        val value = if (metric) {
            val m = height / 100f
            weight / (m * m)
        } else {
            (weight / (height * height)) * 703f
        }
        return (value * 100f).roundToInt() / 100f
    }

    fun category(bmi: Float): BmiCategory = when {
        bmi < 18.5f -> BmiCategory.UNDERWEIGHT
        bmi < 25f -> BmiCategory.HEALTHY
        bmi < 30f -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    /** Mifflin-St Jeor */
    fun bmr(weight: Float, height: Float, age: Int, isMale: Boolean, metric: Boolean): Int {
        val kg = toKg(weight, metric)
        val cm = toCm(height, metric)
        val base = (10 * kg) + (6.25f * cm) - (5 * age)
        return (if (isMale) base + 5 else base - 161).toInt()
    }

    /** Waist and height must be in the same unit; the ratio is unit-free. */
    fun whtr(waist: Float, height: Float): Float = waist / height

    fun isWhtrHealthy(whtr: Float) = whtr < 0.50f

    /** Healthy BMI 18.5 - 24.9 range, returned in kg. */
    fun idealRangeKg(height: Float, metric: Boolean): Pair<Float, Float> {
        val m = toCm(height, metric) / 100f
        return 18.5f * m * m to 24.9f * m * m
    }

    fun kgToDisplay(kg: Float, metric: Boolean) = if (metric) kg else kg * KG_TO_LBS

    fun waterGoalMl(weight: Float, age: Int, metric: Boolean): Int {
        val kg = toKg(weight, metric)
        val baseline = if (age < 14) {
            when {
                kg <= 10f -> (kg * 100).toInt()
                kg <= 20f -> (1000 + (kg - 10) * 50).toInt()
                else -> (1500 + (kg - 20) * 20).toInt()
            }
        } else {
            (kg * 35).toInt()
        }
        return baseline + MOVEMENT_ML + CLIMATE_ML
    }

    const val MOVEMENT_ML = 600
    const val CLIMATE_ML = 500
    const val DEFAULT_WATER_GOAL_ML = 2000

    /** Returns an error message per field, or null when valid. */
    fun validate(weight: Float?, height: Float?, waist: Float?, age: Int?, metric: Boolean): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val (wMin, wMax) = if (metric) 10f to 500f else 22f to 1100f
        val (hMin, hMax) = if (metric) 50f to 260f else 20f to 103f
        if (weight == null || weight < wMin || weight > wMax) errors["weight"] = "Enter ${wMin.toInt()} - ${wMax.toInt()}"
        if (height == null || height < hMin || height > hMax) errors["height"] = "Enter ${hMin.toInt()} - ${hMax.toInt()}"
        if (waist == null || waist <= 0f || (height != null && waist > height)) errors["waist"] = "Enter a valid waist size"
        if (age == null || age < 5 || age > 120) errors["age"] = "Enter age 5 - 120"
        return errors
    }
}
