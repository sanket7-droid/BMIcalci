package com.example.bmicalci.workout

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bmicalci.ui.components.GlowButton
import com.example.bmicalci.ui.components.NeonCard
import com.example.bmicalci.ui.components.OutlineChipButton
import com.example.bmicalci.ui.components.SegmentedToggle
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Space
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Backend URL. On a phone run: adb reverse tcp:3000 tcp:3000 (emulator: use 10.0.2.2). */
private const val BASE = "http://localhost:3000"
private val json = Json { ignoreUnknownKeys = true }

@Serializable data class ApiPlanItem(val exerciseId: String, val name: String, val group: String, val image: String = "", val sets: Int, val reps: String, val restSec: Int, val video: String? = null)
@Serializable data class PlanDay(val day: Int, val title: String, val exercises: List<ApiPlanItem>)
@Serializable data class Program(val place: String, val goal: String, val level: String, val daysPerWeek: Int, val plan: List<PlanDay>)
@Serializable data class ExerciseDetail(val id: String, val name: String, val equipment: String, val primary: List<String>, val steps: List<String>, val images: List<String>, val video: String? = null)
@Serializable data class Meal(val slot: String, val name: String, val detail: String, val kcal: Int, val protein: Int)
@Serializable data class DietNotes(val pre: String, val post: String, val water: String)
@Serializable data class Diet(val type: String, val goal: String, val targetKcal: Int? = null, val meals: List<Meal>, val notes: DietNotes)

private suspend inline fun <reified T> get(path: String): Result<T> = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 8000
        try { json.decodeFromString<T>(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }
}

private val labels = mapOf(
    "home" to "Home", "gym" to "Gym",
    "fat_loss" to "Fat loss", "muscle_gain" to "Muscle gain", "fitness" to "General fitness",
    "beginner" to "Beginner", "intermediate" to "Intermediate",
)

// ---------------------------------------------------------------- root

@Composable
fun ProgramsScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var place by rememberSaveable { mutableStateOf("home") }
    var goal by rememberSaveable { mutableStateOf("fitness") }
    var level by rememberSaveable { mutableStateOf("beginner") }
    var days by rememberSaveable { mutableStateOf("3") }

    openExercise?.let {
        BackHandler { openExercise = null }
        ExerciseDetailScreen(it, onBack = { openExercise = null })
        return
    }
    BackHandler { if (tab == 0 && step > 0) step-- else onBack() }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.md)) {
        Row(Modifier.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            OutlineChipButton("Back", onClick = { if (tab == 0 && step > 0) step-- else onBack() })
            Box(Modifier.weight(1f)) { SegmentedToggle(listOf("Programs", "Diet"), tab, { tab = it }) }
        }
        if (tab == 1) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DietTab() }
        } else {
            if (step < 4) StepProgress(step)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(280)) { it * dir / 3 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it * dir / 3 } + fadeOut(tween(150)))
                },
                label = "wizard",
            ) { s ->
                when (s) {
                    0 -> StepScreen("Where do you train?", "Pick your setup. Plans use only the equipment you have.") {
                        BigOption("Home", "Bodyweight, dumbbells, bands", Icons.Filled.Home, place == "home") { place = "home"; step = 1 }
                        BigOption("Gym", "Barbells, machines, cables", Icons.Filled.FitnessCenter, place == "gym") { place = "gym"; step = 1 }
                    }
                    1 -> StepScreen("What is your goal?", "This sets sets, reps and rest times.") {
                        TextOption("Fat loss", "Higher reps, short rest", goal == "fat_loss") { goal = "fat_loss"; step = 2 }
                        TextOption("Muscle gain", "Heavier sets, longer rest", goal == "muscle_gain") { goal = "muscle_gain"; step = 2 }
                        TextOption("General fitness", "Balanced strength and stamina", goal == "fitness") { goal = "fitness"; step = 2 }
                    }
                    2 -> StepScreen("Your experience level", "Beginner plans avoid advanced movements.") {
                        TextOption("Beginner", "New or returning after a break", level == "beginner") { level = "beginner"; step = 3 }
                        TextOption("Intermediate", "Training regularly for 6+ months", level == "intermediate") { level = "intermediate"; step = 3 }
                    }
                    3 -> StepScreen("Days per week", "3 = full body, 4 = upper/lower, 5-6 = push/pull/legs.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            listOf("3", "4", "5", "6").forEach { d ->
                                Box(Modifier.weight(1f)) { DayChoice(d, days == d) { days = d } }
                            }
                        }
                        Spacer(Modifier.height(Space.md))
                        GlowButton("Build my plan", onClick = { step = 4 })
                    }
                    else -> PlanView(place, goal, level, days, onExercise = { openExercise = it })
                }
            }
        }
    }
}

@Composable
private fun StepProgress(step: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = Space.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            val c by animateColorAsState(if (i <= step) Neon.Accent else Neon.Border, tween(250), label = "p")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
        }
    }
}

@Composable
private fun StepScreen(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(title, color = Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.xs))
        content()
    }
}

@Composable
private fun BigOption(title: String, sub: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, tween(220), label = "scale")
    val border by animateColorAsState(if (selected) Neon.Accent else Neon.Border, tween(220), label = "border")
    Row(
        Modifier.fillMaxWidth().height(132.dp).scale(scale)
            .clip(RoundedCornerShape(20.dp)).background(Neon.Surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(icon, null, tint = Neon.TextPrimary, modifier = Modifier.size(44.dp))
        Column {
            Text(title, color = Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(sub, color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TextOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Neon.Accent else Neon.Border, tween(220), label = "border")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Neon.Surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(Space.md),
    ) {
        Text(title, color = Neon.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(sub, color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DayChoice(d: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Neon.Accent else Neon.Surface)
            .border(BorderStroke(1.dp, if (selected) Neon.Accent else Neon.Border), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(d, color = if (selected) Neon.OnAccent else Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium) }
}

// ---------------------------------------------------------------- plan: day tabs + swipeable cards

@Composable
private fun PlanView(place: String, goal: String, level: String, days: String, onExercise: (String) -> Unit) {
    var program by remember { mutableStateOf<Result<Program>?>(null) }
    LaunchedEffect(place, goal, level, days) { program = get<Program>("/programs?place=$place&goal=$goal&level=$level&days=$days") }
    val r = program
    when {
        r == null -> Text("Building your plan...", color = Neon.TextSecondary)
        r.isFailure -> NeonCard { Text("Cannot reach server. Start the backend and run: adb reverse tcp:3000 tcp:3000", color = Neon.TextSecondary) }
        else -> {
            val p = r.getOrThrow()
            var dayIdx by rememberSaveable { mutableIntStateOf(0) }
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    "${labels[place]} / ${labels[goal]} / ${labels[level]} / ${p.daysPerWeek} days",
                    color = Neon.TextSecondary, style = MaterialTheme.typography.labelMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    itemsIndexed(p.plan) { i, d ->
                        OutlineChipButton("Day ${d.day}  ${d.title}", onClick = { dayIdx = i }, selected = i == dayIdx)
                    }
                }
                val day = p.plan[dayIdx.coerceIn(0, p.plan.lastIndex)]
                Text("${day.exercises.size} exercises - swipe", color = Neon.TextMuted, style = MaterialTheme.typography.labelMedium)
                val pager = rememberPagerState(pageCount = { day.exercises.size })
                LaunchedEffect(dayIdx) { pager.scrollToPage(0) }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(end = 56.dp),
                    pageSpacing = Space.md,
                    modifier = Modifier.fillMaxWidth(),
                ) { page -> ExerciseCard(day.exercises[page], page + 1, day.exercises.size, isActive = pager.currentPage == page) { onExercise(day.exercises[page].exerciseId) } }
            }
        }
    }
}

@Composable
private fun ExerciseCard(ex: ApiPlanItem, index: Int, total: Int, isActive: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.Surface)
            .border(BorderStroke(1.dp, Neon.Border), RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (ex.video != null && isActive) VideoDemo(BASE + ex.video, Modifier.fillMaxSize())
            else if (ex.image.isNotBlank()) AsyncImage(ex.image, ex.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("$index / $total  ${ex.group.uppercase()}", color = Neon.TextMuted, style = MaterialTheme.typography.labelMedium)
            Text(ex.name, color = Neon.TextPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text("${ex.sets} sets x ${ex.reps} reps   |   rest ${ex.restSec}s", color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            if (ex.video != null) Text("Video demo", color = Neon.Cyan, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------------------------------------------------------------- diet

@Composable
private fun DietTab() {
    var type by rememberSaveable { mutableStateOf("veg") }
    var goal by rememberSaveable { mutableStateOf("fitness") }
    var diet by remember { mutableStateOf<Result<Diet>?>(null) }
    LaunchedEffect(type, goal) { diet = get<Diet>("/diet?type=$type&goal=$goal") }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SegmentedToggle(listOf("Veg", "Non-veg"), if (type == "veg") 0 else 1, { type = if (it == 0) "veg" else "nonveg" })
        SegmentedToggle(listOf("Fat loss", "Muscle", "Fitness"), listOf("fat_loss", "muscle_gain", "fitness").indexOf(goal), { goal = listOf("fat_loss", "muscle_gain", "fitness")[it] })
        val r = diet
        when {
            r == null -> Text("Loading...", color = Neon.TextSecondary)
            r.isFailure -> NeonCard { Text("Cannot reach server (adb reverse tcp:3000 tcp:3000).", color = Neon.TextSecondary) }
            else -> {
                val d = r.getOrThrow()
                val order = listOf("breakfast", "pre", "lunch", "snack", "post", "dinner")
                d.meals.sortedBy { order.indexOf(it.slot) }.forEach { m ->
                    NeonCard {
                        Column {
                            val title = when (m.slot) { "pre" -> "PRE-WORKOUT"; "post" -> "POST-WORKOUT"; else -> m.slot.uppercase() }
                            Text(title, color = if (m.slot == "pre" || m.slot == "post") Neon.Green else Neon.Cyan, style = MaterialTheme.typography.labelLarge)
                            Text(m.name, color = Neon.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(m.detail, color = Neon.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("${m.kcal} kcal  |  ${m.protein} g protein", color = Neon.TextMuted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                NeonCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text("Before: " + d.notes.pre, color = Neon.TextSecondary)
                        Text("After: " + d.notes.post, color = Neon.TextSecondary)
                        Text(d.notes.water, color = Neon.TextSecondary)
                        Text("General guidance, not medical advice.", color = Neon.TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
    }
}

// ---------------------------------------------------------------- exercise detail

@Composable
private fun ExerciseDetailScreen(id: String, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<Result<ExerciseDetail>?>(null) }
    LaunchedEffect(id) { detail = get<ExerciseDetail>("/exercises/$id") }
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS } }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }
    LaunchedEffect(ready) { if (ready) tts.language = Locale("hi", "IN") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        OutlineChipButton("Back", onClick = onBack)
        val r = detail
        if (r == null) Text("Loading...", color = Neon.TextSecondary)
        else if (r.isFailure) Text("Cannot reach server.", color = Neon.TextSecondary)
        else {
            val d = r.getOrThrow()
            var frame by remember { mutableIntStateOf(0) }
            LaunchedEffect(d.id) { while (true) { delay(900); frame = (frame + 1) % d.images.size.coerceAtLeast(1) } }
            Text(d.name.uppercase(), color = Neon.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            if (d.video != null) {
                VideoDemo(BASE + d.video, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)))
            } else if (d.images.isNotEmpty()) {
                AsyncImage(d.images[frame], d.name, Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
            }
            Text("Muscles: ${d.primary.joinToString()}  |  ${d.equipment}", color = Neon.TextSecondary)
            NeonCard {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    d.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Neon.TextPrimary) }
                }
            }
            GlowButton("Voice coach: speak steps", onClick = {
                tts.speak("Chalo shuru karte hain. ${d.name}.", TextToSpeech.QUEUE_FLUSH, null, "intro")
                d.steps.forEachIndexed { i, s -> tts.speak("Step ${i + 1}. $s", TextToSpeech.QUEUE_ADD, null, "s$i") }
            }, enabled = ready)
        }
        Spacer(Modifier.height(Space.lg))
    }
}

/** Whole frame is always visible (FIT, never cropped), muted, looping. */
@Composable
private fun VideoDemo(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            androidx.media3.ui.PlayerView(c).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier,
    )
}
