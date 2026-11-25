package com.example.bmicalci

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bmicalci.bmi.BmiScreen
import com.example.bmicalci.ui.components.PillSwitch
import com.example.bmicalci.ui.theme.Neon
import com.example.bmicalci.ui.theme.Space
import com.example.bmicalci.workout.WorkoutScreen
import kotlinx.coroutines.launch

/**
 * Top switch: "BMI Calci" (left) | "Workout" (right). On the BMI page it is full width under the title;
 * on the Workout page it shrinks into a small switch in the top-right corner to give the content room.
 */
@Composable
fun AppRoot() {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val compact = pager.currentPage == 1
    val fraction by animateFloatAsState(if (compact) 0.56f else 1f, tween(300), label = "switch-width")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Neon.ScreenGradient)
            .safeDrawingPadding(),
    ) {
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(tween(250)) + expandVertically(tween(300)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(300)),
        ) {
            Text(
                text = "BMI HEALTH PRO",
                color = Neon.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm, bottom = Space.sm),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = if (compact) Space.xs else 0.dp),
        ) {
            PillSwitch(
                options = listOf("BMI Calci", "Workout"),
                selectedIndex = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .align(Alignment.CenterEnd),
                compact = compact,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                if (page == 0) BmiScreen() else WorkoutScreen()
            }
        }
    }
}
