package com.example.bmicalci.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Radius
import com.example.bmicalci.ui.theme.Space

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Neon.Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(Neon.Surface)
            .border(BorderStroke(1.dp, borderColor), Radius.card)
            .padding(Space.md),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, color: Color = Neon.Cyan, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier.padding(bottom = Space.xs),
    )
}

@Composable
fun NeonTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    labelColor: Color,
    modifier: Modifier = Modifier,
    error: String? = null,
    integerOnly: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(label, labelColor)
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                val ok = if (integerOnly) new.all { it.isDigit() } else new.count { it == '.' } <= 1 && new.all { it.isDigit() || it == '.' }
                if (ok && new.length <= 6) onValueChange(new)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            singleLine = true,
            isError = error != null,
            shape = Radius.field,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Neon.TextPrimary,
                unfocusedTextColor = Neon.TextPrimary,
                focusedBorderColor = labelColor,
                unfocusedBorderColor = Neon.Border,
                cursorColor = labelColor,
                errorBorderColor = Neon.Red,
                focusedContainerColor = Neon.SurfaceHigh,
                unfocusedContainerColor = Neon.SurfaceHigh,
                errorContainerColor = Neon.SurfaceHigh,
            ),
        )
        if (error != null) {
            Text(
                text = error,
                color = Neon.Red,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(Radius.button)
            .background(if (enabled) Neon.Accent else Neon.Border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Neon.OnAccent,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun OutlineChipButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Neon.Accent,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    val on = selected == true
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(Radius.pill)
            .background(if (on) color else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) color else Neon.Border), Radius.pill)
            .semantics { if (selected != null) this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (on) Neon.OnAccent else Neon.TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Generic 2+ option segmented toggle (Metric/Imperial, Male/Female ...). */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Neon.Accent,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.field)
            .border(BorderStroke(1.dp, Neon.Border), Radius.field),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) accent else Color.Transparent,
                tween(200), label = "seg-bg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(bg)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) Neon.OnAccent else Neon.TextSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Top app switch: pill with a sliding neon thumb. */
@Composable
fun PillSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val outer by animateDpAsState(if (compact) 38.dp else 52.dp, tween(300), label = "pill-h")
    val inner = outer - 8.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(outer)
            .clip(Radius.pill)
            .background(Neon.Surface)
            .border(BorderStroke(1.dp, Neon.Border), Radius.pill)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, tween(250), label = "pill-offset")
        Box(
            modifier = Modifier
                .offset(x = offset)
                .width(segment)
                .height(inner)
                .clip(Radius.pill)
                .background(Neon.Accent),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(inner)
                        .clip(Radius.pill)
                        .semantics {
                            this.selected = selected
                            contentDescription = label
                        }
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) Neon.OnAccent else Neon.TextSecondary,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Neon.TextPrimary,
) {
    Column(modifier = modifier) {
        Text(label, color = Neon.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium)
    }
}
