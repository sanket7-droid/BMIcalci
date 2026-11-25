package com.example.bmicalci.bmi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bmicalci.data.Profile
import com.example.bmicalci.data.Repository
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BmiForm(
    val weight: String = "",
    val height: String = "",
    val waist: String = "",
    val age: String = "",
    val isMetric: Boolean = true,
    val isMale: Boolean = true,
)

data class BmiResult(
    val bmi: Float,
    val category: BmiCategory,
    val bmr: Int,
    val whtr: Float,
    val whtrHealthy: Boolean,
    val idealRange: String,
)

data class BmiUiState(
    val form: BmiForm = BmiForm(),
    val errors: Map<String, String> = emptyMap(),
    val result: BmiResult? = null,
    val waterMl: Int = 0,
    val waterGoalMl: Int = BmiCalculator.DEFAULT_WATER_GOAL_ML,
)

class BmiViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    private val form = MutableStateFlow(BmiForm())
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<BmiUiState> = combine(
        form, errors, repo.profile, repo.waterByDay,
    ) { form, errors, profile, water ->
        BmiUiState(
            form = form,
            errors = errors,
            result = profile?.let(::computeResult),
            waterMl = water[LocalDate.now().toString()] ?: 0,
            waterGoalMl = profile?.let { BmiCalculator.waterGoalMl(it.weight, it.age, it.isMetric) }
                ?: BmiCalculator.DEFAULT_WATER_GOAL_ML,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BmiUiState())

    init {
        // Restore the last saved measurements into the form once.
        viewModelScope.launch {
            repo.profile.first()?.let { p ->
                form.value = BmiForm(
                    weight = fmt(p.weight), height = fmt(p.height), waist = fmt(p.waist),
                    age = p.age.toString(), isMetric = p.isMetric, isMale = p.isMale,
                )
            }
        }
    }

    fun onWeight(v: String) = form.update { it.copy(weight = v) }.also { clearError("weight") }
    fun onHeight(v: String) = form.update { it.copy(height = v) }.also { clearError("height") }
    fun onWaist(v: String) = form.update { it.copy(waist = v) }.also { clearError("waist") }
    fun onAge(v: String) = form.update { it.copy(age = v) }.also { clearError("age") }
    fun onGender(isMale: Boolean) = form.update { it.copy(isMale = isMale) }

    /** Switching units converts the numbers already typed so nothing is silently misread. */
    fun onUnit(metric: Boolean) {
        val f = form.value
        if (f.isMetric == metric) return
        fun conv(s: String, factor: Float) = s.toFloatOrNull()?.let { fmt(it * factor) } ?: s
        val wf = if (metric) 1f / 2.20462f else 2.20462f
        val lf = if (metric) 2.54f else 1f / 2.54f
        form.value = f.copy(
            isMetric = metric,
            weight = conv(f.weight, wf),
            height = conv(f.height, lf),
            waist = conv(f.waist, lf),
        )
        errors.value = emptyMap()
    }

    fun calculate() {
        val f = form.value
        val weight = f.weight.toFloatOrNull()
        val height = f.height.toFloatOrNull()
        val waist = f.waist.toFloatOrNull()
        val age = f.age.toIntOrNull()
        val found = BmiCalculator.validate(weight, height, waist, age, f.isMetric)
        errors.value = found
        if (found.isNotEmpty()) return
        viewModelScope.launch {
            repo.saveProfile(Profile(weight!!, height!!, waist!!, age!!, f.isMale, f.isMetric))
        }
    }

    fun logWater(ml: Int = 250) {
        viewModelScope.launch { repo.addWater(ml) }
    }

    private fun clearError(key: String) = errors.update { it - key }

    private fun computeResult(p: Profile): BmiResult {
        val bmi = BmiCalculator.bmi(p.weight, p.height, p.isMetric)
        val whtr = BmiCalculator.whtr(p.waist, p.height)
        val (minKg, maxKg) = BmiCalculator.idealRangeKg(p.height, p.isMetric)
        val unit = if (p.isMetric) "kg" else "lbs"
        val ideal = String.format(
            Locale.US, "%.1f - %.1f %s",
            BmiCalculator.kgToDisplay(minKg, p.isMetric), BmiCalculator.kgToDisplay(maxKg, p.isMetric), unit,
        )
        return BmiResult(
            bmi = bmi,
            category = BmiCalculator.category(bmi),
            bmr = BmiCalculator.bmr(p.weight, p.height, p.age, p.isMale, p.isMetric),
            whtr = whtr,
            whtrHealthy = BmiCalculator.isWhtrHealthy(whtr),
            idealRange = ideal,
        )
    }

    private fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
}
