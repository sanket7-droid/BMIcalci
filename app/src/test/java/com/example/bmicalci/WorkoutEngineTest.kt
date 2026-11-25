package com.example.bmicalci

import com.example.bmicalci.data.Profile
import com.example.bmicalci.data.SessionRecord
import com.example.bmicalci.data.SetLog
import com.example.bmicalci.workout.ExerciseCatalog
import com.example.bmicalci.workout.FatigueModel
import com.example.bmicalci.workout.Intensity
import com.example.bmicalci.workout.Muscle
import com.example.bmicalci.workout.PlanGenerator
import com.example.bmicalci.workout.Stats
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {
    private val hour = 3_600_000L

    private fun session(ts: Long, vararg sets: SetLog) =
        SessionRecord(ts, 600, sets.toList(), FatigueModel.sessionLoad(sets.toList()), 100)

    @Test fun fatigueHalvesEvery24Hours() {
        val s = session(0L, SetLog("pushup", 12), SetLog("pushup", 12), SetLog("pushup", 12))
        val now = FatigueModel.fatigue(listOf(s), 0L)[Muscle.CHEST]!!
        val later = FatigueModel.fatigue(listOf(s), 24 * hour)[Muscle.CHEST]!!
        assertTrue(now > 0.5f)
        assertEquals(now / 2f, later, 0.01f)
        assertEquals(0f, FatigueModel.fatigue(listOf(s), 0L)[Muscle.CALVES]!!, 0.0001f)
    }

    @Test fun planAvoidsFatiguedMuscles() {
        val chestSession = session(0L, *Array(4) { SetLog("pushup", 12) })
        val fatigue = FatigueModel.fatigue(listOf(chestSession), 1 * hour)
        val plan = PlanGenerator.generate(null, fatigue, dayOfYear = 10)
        assertTrue(Muscle.CHEST !in plan.focus)
    }

    @Test fun obeseProfileGetsLowImpactOnly() {
        val profile = Profile(weight = 110f, height = 170f, waist = 110f, age = 35)
        val plan = PlanGenerator.generate(profile, emptyMap(), dayOfYear = 3)
        assertEquals(Intensity.LOW_IMPACT, plan.intensity)
        assertTrue(plan.items.all { it.exercise.lowImpact })
        assertTrue(plan.items.isNotEmpty())
    }

    @Test fun underweightProfileGetsStrengthWithMoreSets() {
        val profile = Profile(weight = 48f, height = 175f, waist = 65f, age = 22)
        val plan = PlanGenerator.generate(profile, emptyMap(), dayOfYear = 3)
        assertEquals(Intensity.STRENGTH, plan.intensity)
        assertTrue(plan.items.any { it.sets == 4 })
    }

    @Test fun tiredBodyGetsRecoveryDay() {
        val fatigue = Muscle.entries.associateWith { 0.9f }
        assertEquals(Intensity.RECOVERY, PlanGenerator.generate(null, fatigue, 5).intensity)
    }

    @Test fun ghostUsesMostRecentSession() {
        val old = session(1000L, SetLog("squat", 10), SetLog("squat", 9))
        val recent = session(2000L, SetLog("squat", 15), SetLog("squat", 14), SetLog("pushup", 8))
        assertEquals(listOf(15, 14), Stats.lastReps(listOf(old, recent), "squat"))
        assertTrue(Stats.lastReps(listOf(old, recent), "plank").isEmpty())
    }

    @Test fun streakCountsConsecutiveDays() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 5, 10)
        fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + hour
        val sessions = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
            .map { session(at(it), SetLog("squat", 10)) }
        assertEquals(3, Stats.streak(sessions, today, zone))
        // Streak survives until the end of today if yesterday was trained.
        assertEquals(2, Stats.streak(sessions.drop(1), today, zone))
        assertEquals(0, Stats.streak(sessions.drop(3), today, zone))
    }

    @Test fun catalogIsConsistent() {
        assertTrue(ExerciseCatalog.all.map { it.id }.toSet().size == ExerciseCatalog.all.size)
        Muscle.entries.forEach { m -> assertTrue("No exercise for $m", ExerciseCatalog.all.any { !it.cardio && it.primary == m }) }
    }
}
