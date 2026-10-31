package io.github.evative7.parley.ui

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch

/** Which way face-to-face content is turned toward its reader at the left or right end of the screen. */
enum class Turn { None, Clockwise, CounterClockwise }

/** Turns the content a quarter, laying it out in the swapped size so it fills the slot. Touch follows the turn. */
fun Modifier.turned(turn: Turn): Modifier = if (turn == Turn.None) this else layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        // Centered, so turning about the middle lands it exactly in the swapped box.
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = if (turn == Turn.Clockwise) 90f else -90f }
    }
}

/**
 * Drag and fling for a top-down list inside [turned] content; give the list `userScrollEnabled = false`.
 * Compose's own scrolling measures fling speed wrong under a quarter turn, so a quick swipe stops dead and
 * bounces. Speed here comes only from the per-event positions, which are mapped through the turn correctly.
 */
@Composable
fun Modifier.turnedScroll(state: ScrollableState): Modifier {
    val fling = ScrollableDefaults.flingBehavior()
    val scope = rememberCoroutineScope()
    return pointerInput(state, fling) {
        val tracker = VelocityTracker()
        val flingAway = {
            val velocity = -tracker.calculateVelocity().y
            scope.launch { state.scroll { with(fling) { performFling(velocity) } } }
        }
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                // Catching a list that is still flinging stops it, as a normal list does.
                scope.launch { state.stopScroll() }
            },
            onDragEnd = { flingAway() },
            onDragCancel = { flingAway() },
        ) { change, dragAmount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            state.dispatchRawDelta(-dragAmount)
        }
    }
}
