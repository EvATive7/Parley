package io.github.evative7.parley.ui

import android.content.ClipData
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.evative7.parley.R
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.Hint
import io.github.evative7.parley.state.PttPhase
import io.github.evative7.parley.state.PttStateMachine
import io.github.evative7.parley.state.TranslateViewModel
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(vm: TranslateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val volume = vm.volume.collectAsStateWithLifecycle()
    val elapsed = vm.elapsedMs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Going to the background (or an incoming call) ends the recording without sending it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onAbort() }
    PhaseHaptics(state.phase)

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            LanguageBar(
                target = state.target,
                onSelect = vm::setTarget,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(R.string.prototype_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.cards.isEmpty()) {
                    EmptyState(Modifier.align(Alignment.Center))
                }
                CardList(
                    cards = state.cards,
                    onCopy = { card ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("translation", card.translation)))
                        }
                        // Android 13+ shows its own clipboard confirmation.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) vm.showHint(Hint.Copied)
                    },
                    onFavorite = { vm.toggleFavorite(it.id) },
                    onRedo = { vm.redo(it.id) },
                    onRetry = { vm.onRetry() },
                )
            }

            HintLine(
                phase = state.phase,
                hint = state.hint,
                elapsedMs = { elapsed.value },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            PttButton(
                phase = state.phase,
                volume = { volume.value },
                progress = { (elapsed.value.toFloat() / PttStateMachine.MAX_RECORD_MS).coerceIn(0f, 1f) },
                onPress = vm::onPress,
                onDrag = vm::onDrag,
                onRelease = vm::onRelease,
                onAbort = vm::onAbort,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CardList(
    cards: List<CardState>,
    onCopy: (CardState) -> Unit,
    onFavorite: (CardState) -> Unit,
    onRedo: (CardState) -> Unit,
    onRetry: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    // Cards that already played their entrance; scrolling back must not replay it.
    val entered = remember { mutableSetOf<Long>() }
    val rise = with(LocalDensity.current) { 48.dp.toPx() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true, // newest sits right above the button it came out of
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        items(cards, key = { it.id }) { card ->
            val enter = remember(card.id) { Animatable(if (card.id in entered) 1f else 0f) }
            LaunchedEffect(card.id) {
                entered += card.id
                enter.animateTo(1f, motion.slowSpatialSpec())
            }
            ResultCard(
                card = card,
                onCopy = { onCopy(card) },
                onFavorite = { onFavorite(card) },
                onRedo = { onRedo(card) },
                onRetry = onRetry,
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    )
                    .graphicsLayer {
                        val p = enter.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (1f - p) * rise
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.85f + 0.15f * p
                        alpha = p.coerceIn(0f, 1f)
                    },
            )
        }
    }
}

@Composable
private fun LanguageBar(target: Lang, onSelect: (Lang) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        // The source language is detected by ASR; only the target is chosen here.
        FilledTonalButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.target_language, target.displayName()))
            Icon(painterResource(R.drawable.ic_arrow_drop_down), null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (lang in Lang.targets) {
                DropdownMenuItem(
                    text = { Text(lang.displayName()) },
                    onClick = { onSelect(lang); expanded = false },
                    trailingIcon = if (lang == target) {
                        { Icon(painterResource(R.drawable.ic_check), null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HintLine(phase: PttPhase, hint: Hint?, elapsedMs: () -> Long, modifier: Modifier = Modifier) {
    val leftSec = ((PttStateMachine.MAX_RECORD_MS - elapsedMs()) / 1000 + 1).toInt()
    val text = when {
        phase == PttPhase.CancelArmed -> stringResource(R.string.hint_release_to_cancel)
        phase == PttPhase.Recording && leftSec <= 10 -> stringResource(R.string.hint_time_left, leftSec)
        hint == Hint.HoldToTalk -> stringResource(R.string.hint_hold_to_talk)
        hint == Hint.Redo -> stringResource(R.string.hint_redo)
        hint == Hint.Copied -> stringResource(R.string.copied)
        else -> null
    }
    val motion = MaterialTheme.motionScheme
    Box(modifier.height(28.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "hint",
        ) { t ->
            if (t != null) {
                Text(
                    t,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (phase == PttPhase.CancelArmed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.size(1.dp))
            }
        }
    }
}

/** Haptics for state changes; the press itself vibrates directly in the gesture handler. */
@Composable
private fun PhaseHaptics(phase: PttPhase) {
    val view = LocalView.current
    var previous by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        val from = previous
        previous = phase
        if (from == phase) return@LaunchedEffect
        when (phase) {
            PttPhase.CancelArmed -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            PttPhase.Result -> view.haptic(confirm = true)
            PttPhase.Error -> view.haptic(confirm = false)
            else -> Unit
        }
    }
}

private fun View.haptic(confirm: Boolean) {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (confirm) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        // CONFIRM / REJECT need Android 11; fall back to a plain short tick.
        else -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
}
