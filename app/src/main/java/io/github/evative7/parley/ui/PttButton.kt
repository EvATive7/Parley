package io.github.evative7.parley.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collectLatest

private val ButtonSize = 96.dp
private const val RecordingScale = 1.08f
// How much louder speech adds on top of [RecordingScale]. Range 0 (no pulse) to ~1; full volume
// reaches RecordingScale + MaxPulse, plus spring overshoot.
private const val MaxPulse = 0.5f
// Only a little room around the button, so the hint sits right above it. The pulse draws past this
// and under the hint (see HintLine's zIndex) rather than pushing the hint away.
private val ButtonArea = ButtonSize * 1.25f
// Bounce, range (0, 1]: 1 glides with no overshoot, lower overshoots more per syllable
// (0.5 ≈ 16 %, 0.3 ≈ 37 %, 0.1 ≈ 73 %) and keeps wobbling longer. 0 never settles.
private const val PulseDamping = 0.1f
// Speed, range ~200–10 000 (Compose: Low 200, MediumLow 400, Medium 1500, High 10 000).
// Higher swells and springs back faster.
private const val PulseStiffness = 1500f
private val PulseSpring = spring<Float>(dampingRatio = PulseDamping, stiffness = PulseStiffness)
private val CancelDistance = 80.dp

/**
 * The single morphing control the whole app is built around:
 * cookie (idle) → soft burst swelling with the voice (recording) → idle-sized red disc (release to cancel) → loading indicator (busy).
 */
@Composable
fun PttButton(
    phase: PttPhase,
    volume: () -> Float,
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
            recording -> RecordingScale
            else -> 1f
        },
        motion.defaultSpatialSpec(),
        label = "scale",
    )
    // Loudness drives the size directly; the spring turns 20 ms level steps into a pulse.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(recording, reduceMotion) {
        if (recording && !reduceMotion) {
            snapshotFlow { volume() }.collectLatest { pulse.animateTo(it.coerceIn(0f, 1f), PulseSpring) }
        } else {
            pulse.animateTo(0f, motion.defaultSpatialSpec())
        }
    }

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

    Box(modifier.size(ButtonArea), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(ButtonSize)
                .graphicsLayer {
                    val s = scale + pulse.value * MaxPulse
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
