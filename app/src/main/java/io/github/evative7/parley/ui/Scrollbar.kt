package io.github.evative7.parley.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Room to leave at the end of scrolled content so the thumb never covers it. */
val ScrollbarGutter = 12.dp

/**
 * A thin thumb along the right edge of a scrolled list, shown whenever the list is longer than its box: faint
 * at rest, clearer while it moves. Apply it before `verticalScroll(state)`, so it measures the visible box
 * rather than the whole content, and pad the content's end by [ScrollbarGutter].
 */
fun Modifier.scrollbar(state: ScrollState): Modifier = composed {
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, state.value) {
        if (state.isScrollInProgress) moving = true
        else {
            delay(1_000)
            moving = false
        }
    }
    val emphasis by animateFloatAsState(if (moving) 1f else 0f, tween(durationMillis = 250), label = "scrollbar")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val viewport = size.height
        val width = (3 + emphasis).dp.toPx()
        val margin = 4.dp.toPx()
        val track = viewport - margin * 2
        val length = (track * viewport / (viewport + max)).coerceIn(32.dp.toPx(), track)
        val top = margin + (track - length) * state.value / max
        drawRoundRect(
            color = color.copy(alpha = 0.3f + 0.35f * emphasis),
            topLeft = Offset(size.width - width - margin, top),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2),
        )
    }
}
