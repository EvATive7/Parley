package io.github.evative7.parley.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import io.github.evative7.parley.ui.theme.LocalReduceMotion

/**
 * Animates between [RoundedPolygon]s. Going back to the previous shape reverses the current
 * morph smoothly, which covers the common "slide up, slide back" case without a jump.
 */
@Stable
class ShapeMorpher(initial: RoundedPolygon) {
    private var from = initial
    private var to = initial
    var morph by mutableStateOf(Morph(initial, initial))
        private set
    val progress = Animatable(1f)

    suspend fun animateTo(target: RoundedPolygon, spec: AnimationSpec<Float>) {
        when {
            target === to -> progress.animateTo(1f, spec)
            target === from -> progress.animateTo(0f, spec)
            else -> {
                from = if (progress.value >= 0.5f) to else from
                to = target
                morph = Morph(from, to)
                progress.snapTo(0f)
                progress.animateTo(1f, spec)
            }
        }
    }
}

@Composable
fun rememberShapeMorpher(target: RoundedPolygon, spec: AnimationSpec<Float>): ShapeMorpher {
    val morpher = remember { ShapeMorpher(target) }
    LaunchedEffect(target) { morpher.animateTo(target, spec) }
    return morpher
}

/**
 * Slow idle rotation in degrees. When it stops, the shape springs back upright so the next
 * shape (e.g. the cancel square) is not drawn tilted. [symmetry] is the spinning shape's
 * rotational order; jumping by whole steps of 360 / symmetry is invisible.
 */
@Composable
fun rememberSpin(
    spinning: Boolean,
    symmetry: Int,
    settleSpec: AnimationSpec<Float>,
    periodMs: Int = 12_000,
): Animatable<Float, *> {
    val reduceMotion = LocalReduceMotion.current
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning && !reduceMotion) {
        if (spinning && !reduceMotion) {
            while (true) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                rotation.animateTo(start + 360f, tween(periodMs, easing = LinearEasing))
            }
        } else {
            val step = 360f / symmetry
            val rest = rotation.value.mod(step)
            rotation.snapTo(if (rest > step / 2) rest - step else rest)
            rotation.animateTo(0f, settleSpec)
        }
    }
    return rotation
}

/** Fills the morph's current outline. Reads animation values in the draw phase only. */
@Composable
fun MorphCanvas(
    morpher: ShapeMorpher,
    color: () -> Color,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
) {
    val path = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    Canvas(modifier) {
        path.rewind()
        morpher.morph.toPath(morpher.progress.value, path)
        // MaterialShapes are normalized to a unit square.
        matrix.setScale(size.width, size.height)
        matrix.postRotate(rotation(), size.width / 2, size.height / 2)
        path.transform(matrix)
        drawPath(path.asComposePath(), color())
    }
}
