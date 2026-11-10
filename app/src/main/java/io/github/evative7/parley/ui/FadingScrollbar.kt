package io.github.evative7.parley.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private const val ScrollbarFadeDelayMs = 700L

/** A narrow position indicator that appears while the scroll position changes, then fades away. */
fun Modifier.fadingScrollbar(state: ScrollState): Modifier = composed {
    val alpha = rememberScrollbarAlpha { state.value }
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    val width = with(LocalDensity.current) { 3.dp.toPx() }
    val inset = with(LocalDensity.current) { 3.dp.toPx() }
    val minLength = with(LocalDensity.current) { 24.dp.toPx() }
    drawWithContent {
        drawContent()
        val contentLength = size.height + state.maxValue
        if (alpha.value > 0f && state.maxValue > 0 && contentLength > 0f) {
            val length = (size.height * size.height / contentLength).coerceAtLeast(minLength).coerceAtMost(size.height)
            val available = size.height - length
            val top = available * state.value / state.maxValue
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha.value),
                topLeft = Offset(size.width - width - inset, top),
                size = Size(width, length),
                cornerRadius = CornerRadius(width / 2f),
            )
        }
    }
}

/** Lazy-list counterpart of [fadingScrollbar], using visible item positions for a stable estimate. */
fun Modifier.fadingScrollbar(state: LazyListState): Modifier = composed {
    val alpha = rememberScrollbarAlpha { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    val width = with(LocalDensity.current) { 3.dp.toPx() }
    val inset = with(LocalDensity.current) { 3.dp.toPx() }
    val minLength = with(LocalDensity.current) { 24.dp.toPx() }
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        val total = info.totalItemsCount
        if (alpha.value > 0f && total > 0 && visible.isNotEmpty()) {
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val average = visible.map { it.size }.average().toFloat().coerceAtLeast(1f)
            val content = average * total
            if (content > viewport) {
                val length = (viewport * viewport / content).coerceAtLeast(minLength).coerceAtMost(viewport)
                val first = visible.first()
                val itemProgress = (-first.offset / first.size.toFloat()).coerceIn(0f, 1f)
                val progress = ((first.index + itemProgress) / total).coerceIn(0f, 1f)
                val top = (viewport - length) * progress
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha.value),
                    topLeft = Offset(size.width - width - inset, top),
                    size = Size(width, length),
                    cornerRadius = CornerRadius(width / 2f),
                )
            }
        }
    }
}

@Composable
private fun rememberScrollbarAlpha(position: () -> Any): Animatable<Float, *> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alpha) {
        snapshotFlow(position).distinctUntilChanged().drop(1).collectLatest {
            alpha.snapTo(1f)
            delay(ScrollbarFadeDelayMs)
            alpha.animateTo(0f, tween(durationMillis = 300))
        }
    }
    return alpha
}
