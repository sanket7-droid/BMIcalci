package com.example.bmicalci.workout

import com.example.bmicalci.bmi.BmiCalculator
import com.example.bmicalci.data.Profile
import com.example.bmicalci.data.SessionRecord
import com.example.bmicalci.data.SetLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** Muscle fatigue: each session adds load, which halves every [HALF_LIFE_HOURS]. */
object FatigueModel {
    const val HALF_LIFE_HOURS = 24.0
    private const val LOAD_PER_SET = 0.25f

    fun sessionLoad(sets: List<SetLog>): Map<String, Float> {
        val load = mutableMapOf<String, Float>()
        for (set in sets) {
            val ex = ExerciseCatalog.get(set.exerciseId) ?: continue
            val effort = (set.reps / ex.defaultReps.toFloat()).coerceIn(0.2f, 1.5f)
            for ((muscle, involvement) in ex.muscles) {
                load[muscle.name] = (load[muscle.name] ?: 0f) + LOAD_PER_SET * involvement * effort
            }
        }
        return load
    }

    /** 0 = fully fresh, 1 = completely fatigued. */
    fun fatigue(sessions: List<SessionRecord>, nowMs: Long): Map<Muscle, Float> =
        Muscle.entries.associateWith { muscle ->
            var total = 0.0
            for (s in sessions) {
                val load = s.muscleLoad[muscle.name] ?: continue
                val hours = ((nowMs - s.timestamp).coerceAtLeast(0L)) / 3_600_000.0
                total += load * 0.5.pow(hours / HALF_LIFE_HOURS)
            }
            total.toFloat().coerceIn(0f, 1f)
        }
}

data class PlanItem(val exercise: Exercise, val sets: Int, val reps: Int)

enum class Intensity(val label: String) {
    LOW_IMPACT("Low-impact fat burn"),
    BALANCED("Balanced strength + cardio"),
    STRENGTH("Strength & size"),
    RECOVERY("Active recovery"),
}

data class WorkoutPlan(
    val intensity: Intensity,
    val why: String,
    val items: List<PlanItem>,
    val focus: List<Muscle>,
    val estMinutes: Int,
    val targetCalories: Int,
    val bmrSharePercent: Int?,
)

/** Builds today's plan from the saved BMI profile and current muscle fatigue. */
object PlanGenerator {
    private const val SECONDS_PER_SET = 105 // ~45s work + ~60s rest

    fun generate(
        profile: Profile?,
        fatigue: Map<Muscle, Float>,
        dayOfYear: Int = LocalDate.now().dayOfYear,
    ): WorkoutPlan {
        val bmi = profile?.let { BmiCalculator.bmi(it.weight, it.height, it.isMetric) }
        val whtr = profile?.let { BmiCalculator.whtr(it.waist, it.height) }
        val kg = profile?.let { BmiCalculator.toKg(it.weight, it.isMetric) } ?: 70f
        val bmr = profile?.let { BmiCalculator.bmr(it.weight, it.height, it.age, it.isMale, it.isMetric) }
        val age = profile?.age ?: 25

        val freshest = Muscle.entries.sortedBy { fatigue[it] ?: 0f }
        val tired = (freshest.take(4).map { fatigue[it] ?: 0f }.average()) > 0.55

        val intensity = when {
            tired -> Intensity.RECOVERY
            bmi != null && bmi >= 30f -> Intensity.LOW_IMPACT
            bmi != null && bmi < 18.5f -> Intensity.STRENGTH
            else -> Intensity.BALANCED
        }

        val why = buildString {
            if (profile == null || bmi == null || whtr == null) {
                append("Calculate your BMI in the first tab to unlock a plan built around your body. Showing a balanced starter plan.")
            } else {
                append(String.format(Locale.US, "BMI %.1f", bmi))
                append(String.format(Locale.US, ", WHtR %.2f", whtr))
                append(" -> ")
                append(
                    when (intensity) {
                        Intensity.RECOVERY -> "your muscles are still recovering, so today is light and gentle."
                        Intensity.LOW_IMPACT -> "joint-friendly moves with steady cardio, no jumping."
                        Intensity.STRENGTH -> "more sets and fewer cardio bursts to help you build."
                        Intensity.BALANCED -> if (whtr >= 0.5f) "balanced strength plus a cardio finisher to trim the waist." else "balanced strength and cardio to maintain."
                    },
                )
            }
        }

        val muscleCount = if (intensity == Intensity.RECOVERY) 3 else if (intensity == Intensity.LOW_IMPACT) 3 else 4
        val sets = when (intensity) {
            Intensity.STRENGTH -> 4
            Intensity.RECOVERY -> 2
            else -> 3
        }
        val repScale = (if (age >= 50) 0.8f else 1f) * (if (intensity == Intensity.RECOVERY) 0.8f else 1f)

        val lowOnly = intensity == Intensity.LOW_IMPACT || intensity == Intensity.RECOVERY
        val focus = freshest.take(muscleCount)
        val items = mutableListOf<PlanItem>()
        for (muscle in focus) {
            val options = ExerciseCatalog.all.filter {
                !it.cardio && it.primary == muscle && (!lowOnly || it.lowImpact)
            }
            val pick = options.getOrNull(dayOfYear % options.size.coerceAtLeast(1)) ?: continue
            if (items.none { it.exercise.id == pick.id }) {
                items += PlanItem(pick, sets, (pick.defaultReps * repScale).roundToInt().coerceAtLeast(5))
            }
        }

        val wantsCardio = intensity == Intensity.LOW_IMPACT || intensity == Intensity.RECOVERY ||
            (intensity == Intensity.BALANCED && (whtr ?: 0f) >= 0.5f)
        if (wantsCardio) {
            val cardio = ExerciseCatalog.all.filter { it.cardio && (!lowOnly || it.lowImpact) }
            cardio.getOrNull(dayOfYear % cardio.size.coerceAtLeast(1))?.let {
                items += PlanItem(it, 2, (it.defaultReps * repScale).roundToInt())
            }
        }

        val totalSets = items.sumOf { it.sets }
        val minutes = (totalSets * SECONDS_PER_SET / 60f).roundToInt().coerceAtLeast(5)
        val avgMet = if (items.isEmpty()) 3.5f else items.map { it.exercise.met }.average().toFloat()
        val calories = (avgMet * kg * (minutes / 60f)).roundToInt()
        val share = bmr?.takeIf { it > 0 }?.let { (calories * 100f / it).roundToInt() }

        return WorkoutPlan(intensity, why, items, focus, minutes, calories, share)
    }
}

object Stats {
    fun streak(sessions: List<SessionRecord>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = sessions.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toSet()
        var cursor = if (today in days) today else today.minusDays(1)
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Reps of each set from the most recent session that contained [exerciseId] (the "ghost"). */
    fun lastReps(sessions: List<SessionRecord>, exerciseId: String): List<Int> =
        sessions.sortedByDescending { it.timestamp }
            .firstNotNullOfOrNull { s -> s.sets.filter { it.exerciseId == exerciseId }.map { it.reps }.takeIf { it.isNotEmpty() } }
            ?: emptyList()

    fun calories(sets: List<SetLog>, kg: Float, durationSec: Int): Int {
        val mets = sets.mapNotNull { ExerciseCatalog.get(it.exerciseId)?.met }
        if (mets.isEmpty()) return 0
        return (mets.average().toFloat() * kg * (durationSec / 3600f)).roundToInt()
    }
}

enum class CoachMoment { START, AHEAD, BEHIND, SET_WON, SET_LOST, REST, FINISH, STREAK, NEW_USER }

/** Hinglish motivation with a light roast. */
object Coach {
    private val lines: Map<CoachMoment, List<String>> = mapOf(
        CoachMoment.NEW_USER to listOf(
            "Pehle BMI nikaal le bhai, phir dekhte hain tujhe kitna pasina bahana hai.",
            "Welcome! Aaj se sofa ka contract cancel.",
        ),
        CoachMoment.START to listOf(
            "Chal shuru kar, bahane kal ke liye bachake rakh.",
            "Ghost tera hi purana version hai. Usse harana hai!",
            "Warm-up ho gaya? Nahi? Toh abhi hoja, time waste mat kar.",
        ),
        CoachMoment.AHEAD to listOf(
            "Bhai aag laga di! Ghost peeche reh gaya.",
            "Waah, aaj toh purane tujhe hara raha hai tu.",
            "Aise hi, speed mat tod!",
        ),
        CoachMoment.BEHIND to listOf(
            "Ghost aage nikal gaya, kya kar raha hai tu?",
            "Arre, kal wala tu aaj wale se tez tha. Sharam kar!",
            "Utha haath, ghost hass raha hai tujh par.",
        ),
        CoachMoment.SET_WON to listOf(
            "Set jeet liya! Purane tujhe dhool chata di.",
            "Ek aur set tere naam. Kya baat hai!",
        ),
        CoachMoment.SET_LOST to listOf(
            "Ghost jeet gaya is baar, par agla set tera hai.",
            "Koi na, thak gaya? Saans le, phir wapas aa.",
        ),
        CoachMoment.REST to listOf(
            "Paani pi le, 45 second ka rest hai.",
            "Saans gehri le, mobile mat chala.",
        ),
        CoachMoment.FINISH to listOf(
            "Workout khatam! Aaj ka pasina kal ki taakat banega.",
            "Shabash champ! Ab khana soch samajhke khaana.",
        ),
        CoachMoment.STREAK to listOf(
            "Streak zinda hai! Aaj skip kiya toh ghost tujhe roast karega.",
            "Lagataar din, discipline wala bando ka club mein swagat hai.",
        ),
    )

    fun line(moment: CoachMoment, seed: Int = 0): String {
        val options = lines.getValue(moment)
        return options[Math.floorMod(seed, options.size)]
    }
}
