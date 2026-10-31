package io.github.evative7.parley.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.evative7.parley.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch

private class Segment(val start: Int, val end: Int, val alpha: Animatable<Float, AnimationVector1D>)

/** Text that grows by appending; each new fragment fades in over 150 ms. */
@Composable
fun StreamingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val segments = remember { mutableStateListOf<Segment>() }
    // Text present on first composition (e.g. scrolled back into view) shows at once.
    var known by remember { mutableIntStateOf(text.length) }

    LaunchedEffect(text) {
        when {
            text.length < known -> {
                segments.clear(); known = text.length
            }
            text.length > known && !reduceMotion -> {
                val seg = Segment(known, text.length, Animatable(0f))
                segments += seg
                known = text.length
                scope.launch {
                    seg.alpha.animateTo(1f, tween(150, easing = LinearEasing))
                    segments.remove(seg)
                }
            }
            else -> known = text.length
        }
    }

    val annotated = streamingText(text, known, segments.map { it.start until it.end to it.alpha.value }, color)
    Text(annotated, modifier, color = color, style = style)
}

/**
 * [text] with each fading [segments] range at its alpha, and anything past [known] (arrived this frame, not
 * registered yet) invisible. [known] and the ranges come from the previous text and are only brought up to
 * date after composition, so text that just shrank or was replaced can be shorter than they say: every index
 * is clamped to [text].
 */
internal fun streamingText(text: String, known: Int, segments: List<Pair<IntRange, Float>>, color: Color): AnnotatedString =
    buildAnnotatedString {
        val end = text.length
        var cursor = 0
        for ((range, alpha) in segments) {
            val start = range.first.coerceIn(cursor, end)
            val stop = (range.last + 1).coerceIn(start, end)
            append(text.substring(cursor, start))
            withStyle(SpanStyle(color = color.copy(alpha = color.alpha * alpha))) { append(text.substring(start, stop)) }
            cursor = stop
        }
        val shown = known.coerceIn(cursor, end)
        append(text.substring(cursor, shown))
        if (shown < end) withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(shown)) }
    }

/** Placeholder lines shown while a whole-file ASR service is working. */
@Composable
fun SkeletonLines(modifier: Modifier = Modifier, lines: Int = 2) {
    val reduceMotion = LocalReduceMotion.current
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = if (reduceMotion) 0.45f else 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(lines) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == lines - 1) 0.6f else 1f)
                    .height(14.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }
    }
}
