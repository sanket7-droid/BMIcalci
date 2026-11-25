package com.example.bmicalci.bmi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bmicalci.ui.components.GlowButton
import com.example.bmicalci.ui.components.MetricTile
import com.example.bmicalci.ui.components.NeonCard
import com.example.bmicalci.ui.components.NeonTextField
import com.example.bmicalci.ui.components.OutlineChipButton
import com.example.bmicalci.ui.components.SectionLabel
import com.example.bmicalci.ui.components.SegmentedToggle
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Space
import java.util.Locale

private fun BmiCategory.color(): Color = when (this) {
    BmiCategory.UNDERWEIGHT -> Neon.Underweight
    BmiCategory.HEALTHY -> Neon.Healthy
    BmiCategory.OVERWEIGHT -> Neon.Overweight
    BmiCategory.OBESE -> Neon.Obese
}

@Composable
fun BmiScreen(viewModel: BmiViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val form = ui.form
    val unitW = if (form.isMetric) "kg" else "lbs"
    val unitL = if (form.isMetric) "cm" else "inches"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // Unit switch
        SegmentedToggle(
            options = listOf("Metric", "Imperial"),
            selectedIndex = if (form.isMetric) 0 else 1,
            onSelect = { viewModel.onUnit(it == 0) },
        )

        NeonCard {
            SectionLabel("Biological Sex")
            SegmentedToggle(
                options = listOf("Male", "Female"),
                selectedIndex = if (form.isMale) 0 else 1,
                onSelect = { viewModel.onGender(it == 0) },
            )
            Spacer(Modifier.height(Space.md))
            NeonTextField("Weight ($unitW)", form.weight, viewModel::onWeight, Neon.Accent, error = ui.errors["weight"])
            Spacer(Modifier.height(Space.md))
            NeonTextField("Height ($unitL)", form.height, viewModel::onHeight, Neon.Accent, error = ui.errors["height"])
            Spacer(Modifier.height(Space.md))
            NeonTextField("Waist Circumference ($unitL)", form.waist, viewModel::onWaist, Neon.Accent, error = ui.errors["waist"])
            Spacer(Modifier.height(Space.md))
            NeonTextField("Age (Years)", form.age, viewModel::onAge, Neon.Accent, error = ui.errors["age"], integerOnly = true)
        }

        GlowButton(text = "Calculate Complete Diagnostics", onClick = viewModel::calculate)

        ResultCard(ui.result)

        HydrationCard(ui.waterMl, ui.waterGoalMl, onLog = { viewModel.logWater() })

        Spacer(Modifier.height(Space.lg))
    }
}

@Composable
private fun ResultCard(result: BmiResult?) {
    NeonCard {
        Text(
            "YOUR MASS DIAGNOSTICS",
            color = Neon.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        val accent = result?.category?.color() ?: Neon.Green
        Text(
            text = result?.let { String.format(Locale.US, "%.2f", it.bmi) } ?: "0.0",
            color = accent,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = result?.category?.label ?: "Category Status",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Border))
        Spacer(Modifier.height(Space.md))

        Row(Modifier.fillMaxWidth()) {
            MetricTile("BMR (Metabolism)", result?.let { "${it.bmr} kcal" } ?: "--- kcal", Modifier.weight(1f))
            MetricTile("Waist-to-Height (WHtR)", result?.let { String.format(Locale.US, "%.2f", it.whtr) } ?: "---", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth()) {
            MetricTile(
                "Abdominal Status",
                result?.let { if (it.whtrHealthy) "Healthy (Low Risk)" else "High Abdominal Fat" } ?: "---",
                Modifier.weight(1f),
                valueColor = result?.let { if (it.whtrHealthy) Neon.Healthy else Neon.Obese } ?: Neon.TextPrimary,
            )
            MetricTile("Ideal Weight Range", result?.idealRange ?: "---", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.md))
        Text(
            text = result?.category?.actionPlan
                ?: "Tailored Diagnostics: Enter parameters above to see personalized health guidelines.",
            color = Neon.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Neon.SurfaceHigh)
                .padding(14.dp),
        )
    }
}

@Composable
private fun HydrationCard(loggedMl: Int, goalMl: Int, onLog: () -> Unit) {
    NeonCard {
        SectionLabel("Personalized Diagnostic Hydration", Neon.Blue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    String.format(Locale.US, "Logged: %.2f / %.2f L", loggedMl / 1000f, goalMl / 1000f),
                    color = Neon.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                val progress = if (goalMl > 0) (loggedMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
                val done = progress >= 1f
                Text(
                    text = if (done) "Goal Achieved! Perfect Diagnostics Met" else "Goal Progress: ${(progress * 100).toInt()}%",
                    color = if (done) Neon.Green else Neon.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(Space.sm))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                    color = if (done) Neon.Green else Neon.Blue,
                    trackColor = Neon.Border,
                )
            }
            OutlineChipButton("+ Log Glass", onLog)
        }
    }
}
