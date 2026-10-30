package io.github.evative7.parley.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.evative7.parley.R
import io.github.evative7.parley.state.PttPhase
import io.github.evative7.parley.ui.theme.LocalReduceMotion
import kotlinx.coroutines.CancellationException

private val ButtonSize = 96.dp
private val RingSize = 136.dp
private val CancelDistance = 80.dp

/**
 * The single morphing control the whole app is built around:
 * cookie (idle) → soft burst (recording) → idle-sized red disc (release to cancel) → loading indicator (busy).
 */
@Composable
fun PttButton(
    phase: PttPhase,
    volume: () -> Float,
    progress: () -> Float,
    onPress: () -> Unit,
    onDrag: (armed: Boolean) -> Unit,
    onRelease: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier,
    idleContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    idleContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val view = LocalView.current

    val recording = phase == PttPhase.Recording
    val armed = phase == PttPhase.CancelArmed
    val busy = phase == PttPhase.Recognizing || phase == PttPhase.Translating

    val shape = when {
        armed -> MaterialShapes.Circle
        recording -> MaterialShapes.SoftBurst
        busy -> MaterialShapes.Circle
        else -> MaterialShapes.Cookie9Sided
    }
    val morpher = rememberShapeMorpher(shape, motion.fastSpatialSpec())
    val spin = rememberSpin(
        spinning = !recording && !armed && !busy,
        symmetry = 9, // Cookie9Sided
        settleSpec = motion.fastSpatialSpec(),
    )

    val container by animateColorAsState(
        when {
            armed -> colors.error
            recording -> colors.primary
            else -> idleContainer
        },
        motion.defaultEffectsSpec(),
        label = "container",
    )
    val content by animateColorAsState(
        when {
            armed -> colors.onError
            recording -> colors.onPrimary
            else -> idleContent
        },
        motion.defaultEffectsSpec(),
        label = "content",
    )
    val scale by animateFloatAsState(
        when {
            // The red disc shrinks back to the idle size: releasing now leaves nothing behind.
            armed -> 1f
            recording -> 1.15f
            else -> 1f
        },
        motion.defaultSpatialSpec(),
        label = "scale",
    )
    val ringAlpha by animateFloatAsState(
        if (recording) 1f else 0f, motion.defaultEffectsSpec(), label = "ring",
    )

    val press by rememberUpdatedState(onPress)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val abort by rememberUpdatedState(onAbort)

    val description = stringResource(R.string.ptt_description)
    val stateText = when {
        armed -> stringResource(R.string.state_cancel_armed)
        recording -> stringResource(R.string.state_recording)
        busy -> stringResource(R.string.state_busy)
        else -> ""
    }

    Box(modifier.size(RingSize), contentAlignment = Alignment.Center) {
        if (ringAlpha > 0f) {
            CircularWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ringAlpha },
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest,
                amplitude = { if (reduceMotion) 0f else volume().coerceIn(0f, 1f) },
            )
        }
        Box(
            Modifier
                .size(ButtonSize)
                .graphicsLayer {
                    // A little extra breathing with the voice on top of the 1.15× recording scale.
                    val s = scale * (1f + if (recording && !reduceMotion) volume() * 0.06f else 0f)
                    scaleX = s
                    scaleY = s
                }
                .semantics {
                    role = Role.Button
                    contentDescription = description
                    stateDescription = stateText
                }
                .pointerInput(Unit) {
                    val threshold = CancelDistance.toPx()
                    awaitEachGesture {
                        // Recording starts on touch, not after a long-press delay, so the first word survives.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        press()
                        var isArmed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    abort(); break
                                }
                                if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                    release(); break
                                }
                                val nowArmed = down.position.y - change.position.y > threshold
                                if (nowArmed != isArmed) {
                                    isArmed = nowArmed
                                    drag(nowArmed)
                                }
                                change.consume()
                            }
                        } catch (e: CancellationException) {
                            abort()
                            throw e
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            MorphCanvas(
                morpher = morpher,
                color = { container },
                rotation = { spin.value },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedContent(
                targetState = when {
                    busy -> ButtonIcon.Loading
                    armed -> ButtonIcon.Delete
                    else -> ButtonIcon.Mic
                },
                // The 72 dp loading indicator and the 36 dp icons differ in size; anchored top-start they
                // would slide diagonally while the box resizes.
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), 0.6f)) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), 0.6f))
                },
                label = "icon",
            ) { icon ->
                when (icon) {
                    ButtonIcon.Loading -> LoadingIndicator(Modifier.size(72.dp), color = content)
                    ButtonIcon.Delete -> Icon(
                        painterResource(R.drawable.ic_delete), null, Modifier.size(36.dp), tint = content,
                    )
                    ButtonIcon.Mic -> Icon(
                        painterResource(R.drawable.ic_mic), null, Modifier.size(36.dp), tint = content,
                    )
                }
            }
        }
    }
}

private enum class ButtonIcon { Mic, Delete, Loading }
