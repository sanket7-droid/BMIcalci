package com.example.bmicalci.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bmicalci.bmi.BmiCalculator
import com.example.bmicalci.data.Profile
import com.example.bmicalci.data.Repository
import com.example.bmicalci.data.SessionRecord
import com.example.bmicalci.data.SetLog
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { WORK, REST }

data class ActiveState(
    val items: List<PlanItem>,
    val itemIndex: Int = 0,
    val setIndex: Int = 0,
    val reps: Int = 0,
    val phase: Phase = Phase.WORK,
    val setStartMs: Long,
    val restEndMs: Long = 0L,
    val nowMs: Long,
    val startedAtMs: Long,
    val logs: List<SetLog> = emptyList(),
    val ghostTarget: Int,
    val ghostIsReal: Boolean,
    val status: Boolean? = null, // true = ahead of ghost, false = behind, null = neck and neck
    val coach: String,
) {
    val item: PlanItem get() = items[itemIndex]
    val ghostReps: Int
        get() = ((nowMs - setStartMs - GET_READY_MS).coerceAtLeast(0L) / GHOST_MS_PER_REP).toInt().coerceAtMost(ghostTarget)
    val restSecondsLeft: Int get() = ((restEndMs - nowMs).coerceAtLeast(0L) / 1000L).toInt() + 1
    val getReady: Boolean get() = phase == Phase.WORK && nowMs - setStartMs < GET_READY_MS

    companion object {
        const val GET_READY_MS = 3_000L
        const val GHOST_MS_PER_REP = 2_200L
    }
}

data class WorkoutUiState(
    val hasProfile: Boolean = false,
    val plan: WorkoutPlan? = null,
    val fatigue: Map<Muscle, Float> = emptyMap(),
    val streak: Int = 0,
    val history: List<SessionRecord> = emptyList(),
    val active: ActiveState? = null,
    val summary: SessionRecord? = null,
    val coach: String = "",
)

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val active = MutableStateFlow<ActiveState?>(null)
    private val summary = MutableStateFlow<SessionRecord?>(null)
    private val finishCoach = MutableStateFlow("")
    private var ticker: Job? = null
    private var coachSeed = 0

    val state: StateFlow<WorkoutUiState> = combine(
        repo.profile, repo.sessions, active, summary, finishCoach,
    ) { profile, sessions, active, summary, finishCoach ->
        val fatigue = FatigueModel.fatigue(sessions, System.currentTimeMillis())
        val streak = Stats.streak(sessions)
        val day = LocalDate.now().dayOfYear
        WorkoutUiState(
            hasProfile = profile != null,
            plan = PlanGenerator.generate(profile, fatigue, day),
            fatigue = fatigue,
            streak = streak,
            history = sessions,
            active = active,
            summary = summary,
            coach = when {
                summary != null -> finishCoach
                profile == null -> Coach.line(CoachMoment.NEW_USER, day)
                streak > 0 -> Coach.line(CoachMoment.STREAK, day)
                else -> Coach.line(CoachMoment.START, day)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    fun start(plan: WorkoutPlan) {
        if (plan.items.isEmpty() || active.value != null) return
        summary.value = null
        viewModelScope.launch {
            val history = repo.sessions.first()
            val now = System.currentTimeMillis()
            val (target, real) = ghostFor(history, plan.items, 0, 0)
            active.value = ActiveState(
                items = plan.items,
                setStartMs = now,
                nowMs = now,
                startedAtMs = now,
                ghostTarget = target,
                ghostIsReal = real,
                coach = Coach.line(CoachMoment.START, coachSeed++),
            )
            startTicker(history)
        }
    }

    fun addRep() = active.update { a ->
        if (a == null || a.phase != Phase.WORK) a else a.copy(reps = a.reps + 1)
    }

    fun removeRep() = active.update { a ->
        if (a == null || a.phase != Phase.WORK) a else a.copy(reps = (a.reps - 1).coerceAtLeast(0))
    }

    fun finishSet() {
        val a = active.value ?: return
        if (a.phase != Phase.WORK) return
        val now = System.currentTimeMillis()
        val logs = if (a.reps > 0) a.logs + SetLog(a.item.exercise.id, a.reps) else a.logs
        val won = a.reps >= a.ghostTarget
        val lastItem = a.itemIndex == a.items.lastIndex
        val lastSet = a.setIndex == a.item.sets - 1
        if (lastItem && lastSet) {
            finishWorkout(a.copy(logs = logs, nowMs = now))
            return
        }
        active.value = a.copy(
            logs = logs,
            phase = Phase.REST,
            nowMs = now,
            restEndMs = now + REST_MS,
            coach = Coach.line(if (won) CoachMoment.SET_WON else CoachMoment.SET_LOST, coachSeed++),
        )
    }

    fun skipRest() {
        val a = active.value ?: return
        if (a.phase == Phase.REST) viewModelScope.launch { beginNextSet(a, repo.sessions.first()) }
    }

    fun quit() {
        ticker?.cancel()
        active.value = null
    }

    fun dismissSummary() {
        summary.value = null
    }

    private fun startTicker(history: List<SessionRecord>) {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (active.value != null) {
                delay(250)
                val a = active.value ?: break
                val now = System.currentTimeMillis()
                if (a.phase == Phase.REST && now >= a.restEndMs) {
                    beginNextSet(a, history)
                    continue
                }
                active.update { cur ->
                    if (cur == null) null else {
                        var next = cur.copy(nowMs = now)
                        if (cur.phase == Phase.WORK && !next.getReady) {
                            val status = when {
                                next.reps > next.ghostReps -> true
                                next.reps < next.ghostReps - 1 -> false
                                else -> null
                            }
                            if (status != cur.status && status != null) {
                                next = next.copy(
                                    status = status,
                                    coach = Coach.line(if (status) CoachMoment.AHEAD else CoachMoment.BEHIND, coachSeed++),
                                )
                            } else if (status == null && cur.status != null) {
                                next = next.copy(status = null)
                            }
                        }
                        next
                    }
                }
            }
        }
    }

    private fun beginNextSet(a: ActiveState, history: List<SessionRecord>) {
        val moreSets = a.setIndex < a.item.sets - 1
        val itemIndex = if (moreSets) a.itemIndex else a.itemIndex + 1
        val setIndex = if (moreSets) a.setIndex + 1 else 0
        val now = System.currentTimeMillis()
        val (target, real) = ghostFor(history, a.items, itemIndex, setIndex)
        active.value = a.copy(
            itemIndex = itemIndex,
            setIndex = setIndex,
            reps = 0,
            phase = Phase.WORK,
            setStartMs = now,
            nowMs = now,
            ghostTarget = target,
            ghostIsReal = real,
            status = null,
            coach = Coach.line(CoachMoment.START, coachSeed++),
        )
    }

    private fun ghostFor(history: List<SessionRecord>, items: List<PlanItem>, itemIndex: Int, setIndex: Int): Pair<Int, Boolean> {
        val item = items[itemIndex]
        val last = Stats.lastReps(history, item.exercise.id)
        val real = last.getOrNull(setIndex) ?: last.lastOrNull()
        return if (real != null) real to true else item.reps to false
    }

    private fun finishWorkout(a: ActiveState) {
        ticker?.cancel()
        viewModelScope.launch {
            val profile: Profile? = repo.profile.first()
            val kg = profile?.let { BmiCalculator.toKg(it.weight, it.isMetric) } ?: 70f
            val durationSec = ((a.nowMs - a.startedAtMs) / 1000L).toInt().coerceAtLeast(1)
            val record = SessionRecord(
                timestamp = a.nowMs,
                durationSec = durationSec,
                sets = a.logs,
                muscleLoad = FatigueModel.sessionLoad(a.logs),
                caloriesBurned = Stats.calories(a.logs, kg, durationSec),
            )
            if (a.logs.isNotEmpty()) repo.addSession(record)
            finishCoach.value = Coach.line(CoachMoment.FINISH, coachSeed++)
            summary.value = record
            active.value = null
        }
    }

    private companion object {
        const val REST_MS = 45_000L
    }
}
