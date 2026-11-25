package com.example.bmicalci.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bmicalci.data.SessionRecord
import com.example.bmicalci.ui.components.GlowButton
import com.example.bmicalci.ui.components.MetricTile
import com.example.bmicalci.ui.components.NeonCard
import com.example.bmicalci.ui.components.OutlineChipButton
import com.example.bmicalci.ui.components.SectionLabel
import com.example.bmicalci.ui.components.SegmentedToggle
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Space
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var showLibrary by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showMirror by rememberSaveable { mutableStateOf(false) }
    if (showLibrary) {
        ProgramsScreen(onBack = { showLibrary = false })
        return
    }
    if (showMirror) {
        com.example.bmicalci.musclemirror.MuscleMirrorScreen(onBack = { showMirror = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        val active = ui.active
        val summary = ui.summary
        when {
            active != null -> ActiveSessionCard(active, viewModel)
            summary != null -> SummaryCard(summary, ui.coach, onDone = viewModel::dismissSummary)
            else -> {
                CoachCard(ui.streak, ui.coach)
                com.example.bmicalci.ui.components.GlowButton("Programs, Exercises & Diet", onClick = { showLibrary = true })
                com.example.bmicalci.ui.components.GlowButton("Muscle Mirror (Live Camera)", onClick = { showMirror = true })
                ui.plan?.let { PlanCard(it, ui.hasProfile, onStart = { viewModel.start(it) }) }
                FatigueCard(ui.fatigue, ui.plan?.focus.orEmpty())
                HistoryCard(ui.history)
            }
        }
        Spacer(Modifier.height(Space.lg))
    }
}

@Composable
private fun CoachCard(streak: Int, coach: String) {
    NeonCard(borderColor = Neon.Border) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Neon.TextPrimary, modifier = Modifier.size(28.dp))
            Text(
                text = if (streak > 0) "$streak day streak" else "No streak yet",
                color = Neon.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text("Coach: $coach", color = Neon.TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(plan: WorkoutPlan, hasProfile: Boolean, onStart: () -> Unit) {
    NeonCard {
        SectionLabel("TODAY'S BODY-AWARE PLAN")
        Text(plan.intensity.label, color = Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.xs))
        Text(plan.why, color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth()) {
            MetricTile("Duration", "${plan.estMinutes} min", Modifier.weight(1f))
            MetricTile("Burn target", "${plan.targetCalories} kcal", Modifier.weight(1f), Neon.TextPrimary)
            MetricTile(
                "Of daily BMR",
                plan.bmrSharePercent?.let { "$it%" } ?: "---",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Space.md))
        plan.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, color = Neon.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.exercise.primary.label} - ${item.exercise.equipment}",
                        color = Neon.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text("${item.sets} x ${item.reps}", color = Neon.Cyan, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(Space.md))
        GlowButton(text = "Start Workout", onClick = onStart, enabled = plan.items.isNotEmpty())
        if (!hasProfile) {
            Text(
                "Tip: fill the BMI Calci tab for a plan made for your body.",
                color = Neon.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

@Composable
private fun FatigueCard(fatigue: Map<Muscle, Float>, focus: List<Muscle>) {
    var back by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val visible = Muscle.entries.filter { it in musclesOnSide(back) }

    NeonCard {
        SectionLabel("MUSCLE FATIGUE MAP", Neon.Green)
        Text(
            "Green = recovered, red = still sore. Today's plan trains the freshest muscles.",
            color = Neon.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.md))
        SegmentedToggle(listOf("Front", "Back"), if (back) 1 else 0, { back = it == 1; selected = null })
        Spacer(Modifier.height(Space.md))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BodyMap(fatigue, selected, back, onSelect = { selected = it })
        }
        Spacer(Modifier.height(Space.md))
        visible.forEach { muscle ->
            val f = fatigue[muscle] ?: 0f
            val recovered = ((1f - f) * 100).toInt()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { selected = muscle }
                    .background(if (muscle == selected) Neon.SurfaceHigh else Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle.label, color = Neon.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (muscle in focus) Text("TRAIN TODAY  ", color = Neon.Cyan, style = MaterialTheme.typography.labelMedium)
                    Text("$recovered% recovered", color = lerp(Neon.Green, Neon.Red, f), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { 1f - f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                    color = lerp(Neon.Green, Neon.Red, f),
                    trackColor = Neon.Border,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<SessionRecord>) {
    NeonCard {
        SectionLabel("RECENT SESSIONS", Neon.Purple)
        if (history.isEmpty()) {
            Text("No workouts yet. Your first one will appear here.", color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        } else {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            history.take(5).forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(s.timestamp)), color = Neon.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${s.sets.size} sets - ${s.sets.sumOf { it.reps }} reps - ${s.durationSec / 60} min",
                            color = Neon.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text("${s.caloriesBurned} kcal", color = Neon.TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(a: ActiveState, vm: WorkoutViewModel) {
    NeonCard(borderColor = Neon.Cyan.copy(alpha = 0.6f)) {
        val item = a.item
        SectionLabel("EXERCISE ${a.itemIndex + 1} OF ${a.items.size}")
        Text(item.exercise.name, color = Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Set ${a.setIndex + 1} of ${item.sets} - ${item.exercise.primary.label}",
            color = Neon.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.md))

        if (a.phase == Phase.REST) {
            Text(
                "REST",
                color = Neon.Blue,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "${a.restSecondsLeft}s",
                color = Neon.Blue,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.md))
            Text("Coach: ${a.coach}", color = Neon.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.md))
            GlowButton("Skip Rest", onClick = vm::skipRest)
        } else {
            val target = a.ghostTarget.coerceAtLeast(1)
            RaceBar(
                label = "You",
                reps = a.reps,
                target = target,
                color = if (a.status == false) Neon.Orange else Neon.Green,
            )
            Spacer(Modifier.height(Space.sm))
            RaceBar(
                label = if (a.ghostIsReal) "Ghost (last time)" else "Target",
                reps = a.ghostReps,
                target = target,
                color = Neon.Purple,
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = if (a.getReady) "GET READY..." else "${a.reps}",
                color = Neon.TextPrimary,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "reps (goal ${a.ghostTarget})",
                color = Neon.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.md))
            Text("Coach: ${a.coach}", color = Neon.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.md))
            GlowButton("+1 Rep", onClick = vm::addRep)
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                OutlineChipButton("-1", vm::removeRep, Neon.TextSecondary)
                OutlineChipButton("Set Done", vm::finishSet, Neon.Green, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.md))
        OutlineChipButton("End workout", vm::quit, Neon.Red, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RaceBar(label: String, reps: Int, target: Int, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$reps / $target", color = Neon.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (reps.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = Neon.Border,
        )
    }
}

@Composable
private fun SummaryCard(s: SessionRecord, coach: String, onDone: () -> Unit) {
    NeonCard(borderColor = Neon.Green.copy(alpha = 0.6f)) {
        SectionLabel("WORKOUT COMPLETE", Neon.Green)
        Row(Modifier.fillMaxWidth()) {
            MetricTile("Time", "${s.durationSec / 60}m ${s.durationSec % 60}s", Modifier.weight(1f))
            MetricTile("Sets", "${s.sets.size}", Modifier.weight(1f))
            MetricTile("Calories", "${s.caloriesBurned} kcal", Modifier.weight(1f), Neon.TextPrimary)
        }
        Spacer(Modifier.height(Space.md))
        Text("Coach: $coach", color = Neon.TextPrimary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Space.md))
        GlowButton("Done", onClick = onDone)
    }
}
