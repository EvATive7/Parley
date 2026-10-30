package io.github.evative7.parley.ui

import android.content.ClipData
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.evative7.parley.R
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.Hint
import io.github.evative7.parley.state.LangPair
import io.github.evative7.parley.state.PttPhase
import io.github.evative7.parley.state.PttStateMachine
import io.github.evative7.parley.state.TranslateViewModel
import io.github.evative7.parley.state.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(vm: TranslateViewModel = viewModel(), settingsVm: SettingsViewModel = viewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Lives above both pages, so leaving the app from the settings page also releases the socket.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground() }
    // Settings finished loading or were just saved: connect with the new ones.
    LaunchedEffect(settings) { if (settings != null) vm.onForeground() }

    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = showSettings,
        transitionSpec = {
            // Settings slide in from the trailing edge and back out the same way.
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / 4 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                (slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * it / 4 } + fadeOut(motion.fastEffectsSpec()))
        },
        label = "page",
    ) { settingsOpen ->
        if (settingsOpen) {
            SettingsScreen(settingsVm, onBack = { showSettings = false })
        } else {
            ConversationScreen(vm, settings, onOpenSettings = { showSettings = true })
        }
    }
}

@Composable
private fun ConversationScreen(vm: TranslateViewModel, settings: ServiceSettings?, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val volume = vm.volume.collectAsStateWithLifecycle()
    val elapsed = vm.elapsedMs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Which language bar slot the picker dialog is open for.
    var picking by remember { mutableStateOf<Int?>(null) }

    PhaseHaptics(state.phase)

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            LanguageBar(
                langs = state.langs,
                onPick = { picking = it },
                onSwap = vm::swapLangs,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
            ) {
                SettingsButton(
                    settings = settings,
                    enabled = state.phase !in setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating),
                    onClick = onOpenSettings,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                CardList(
                    cards = state.cards,
                    onCopy = { card ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("translation", card.translation)))
                        }
                        // Android 13+ shows its own clipboard confirmation.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) vm.showHint(Hint.Copied)
                    },
                    onDelete = { vm.delete(it.id) },
                    onRetry = { vm.onRetry(it.id) },
                    retryEnabled = state.phase !in setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating),
                )
            }

            HintLine(
                phase = state.phase,
                hint = state.hint,
                langs = state.langs,
                elapsedMs = { elapsed.value },
            )
            PttButton(
                phase = state.phase,
                volume = { volume.value },
                onPress = vm::onPress,
                onDrag = vm::onDrag,
                onRelease = vm::onRelease,
                onAbort = vm::onAbort,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
            )
        }
    }

    picking?.let { slot ->
        LanguagePickerDialog(
            selected = state.langs[slot],
            other = state.langs[1 - slot],
            onPick = { vm.setLang(slot, it); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun CardList(
    cards: List<CardState>,
    onCopy: (CardState) -> Unit,
    onDelete: (CardState) -> Unit,
    onRetry: (CardState) -> Unit,
    retryEnabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    // Cards that already played their entrance; scrolling back must not replay it.
    val entered = remember { mutableSetOf<Long>() }
    val rise = with(LocalDensity.current) { 48.dp.toPx() }
    val listState = rememberLazyListState()
    // A new recording adds its card at index 0; bring it into view even if the user had scrolled up.
    val newest = cards.firstOrNull()?.id
    LaunchedEffect(newest) {
        if (newest != null) listState.animateScrollToItem(0)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
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
                onDelete = { onDelete(card) },
                onRetry = { onRetry(card) },
                retryEnabled = retryEnabled,
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

/** The two sides of the conversation. ASR tells which one is speaking; the other one is the target. */
@Composable
private fun LanguageBar(
    langs: LangPair,
    onPick: (slot: Int) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageButton(langs.first, onClick = { onPick(0) }, modifier = Modifier.weight(1f))
        IconButton(onClick = onSwap) {
            Icon(painterResource(R.drawable.ic_swap_horiz), stringResource(R.string.swap_languages))
        }
        LanguageButton(langs.second, onClick = { onPick(1) }, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Opens the service settings; a dot says they need attention (red) or that results are fake (demo). */
@Composable
private fun SettingsButton(settings: ServiceSettings?, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val needsSetup = settings != null && !settings.isReady
    val demo = settings != null && settings.isReady && settings.demoMode
    val status = when {
        needsSetup -> stringResource(R.string.settings_required)
        demo -> stringResource(R.string.settings_demo)
        else -> null
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { if (status != null) stateDescription = status },
    ) {
        BadgedBox(
            badge = {
                when {
                    needsSetup -> Badge()
                    demo -> Badge(containerColor = colors.tertiary)
                }
            },
        ) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
        }
    }
}

@Composable
private fun LanguageButton(lang: Lang, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        AnimatedContent(
            targetState = lang,
            transitionSpec = { riseAndFade(motion) },
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f, fill = false),
            label = "lang",
        ) { l ->
            Text(l.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_arrow_drop_down), null)
    }
}

private enum class Phrase(@StringRes val res: Int) {
    HoldToTalk(R.string.ptt_hold_to_talk),
    ReleaseToSend(R.string.ptt_release_to_send),
    TimeLeft(R.string.ptt_time_left),
    ReleaseToCancel(R.string.ptt_release_to_cancel),
    Recognizing(R.string.ptt_recognizing),
    Copied(R.string.ptt_copied),
}

private data class HintText(val phrase: Phrase, val seconds: Int, val langs: LangPair)

/** What the button does right now, in both languages, so whoever holds the phone can read it. */
@Composable
private fun HintLine(phase: PttPhase, hint: Hint?, langs: LangPair, elapsedMs: () -> Long) {
    val leftSec = ((PttStateMachine.MAX_RECORD_MS - elapsedMs()) / 1000 + 1).toInt()
    val phrase = when {
        phase == PttPhase.CancelArmed -> Phrase.ReleaseToCancel
        phase == PttPhase.Recording && leftSec <= 10 -> Phrase.TimeLeft
        phase == PttPhase.Recording -> Phrase.ReleaseToSend
        phase == PttPhase.Recognizing -> Phrase.Recognizing
        hint == Hint.Copied -> Phrase.Copied
        // A press while translating starts the next sentence, so it reads the same as idle.
        else -> Phrase.HoldToTalk
    }
    val motion = MaterialTheme.motionScheme
    Box(
        // Above the button, whose voice pulse grows into this space.
        Modifier.zIndex(1f).fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = HintText(phrase, if (phrase == Phrase.TimeLeft) leftSec else 0, langs),
            transitionSpec = { riseAndFade(motion) },
            contentAlignment = Alignment.Center,
            label = "hint",
        ) { t ->
            val colors = MaterialTheme.colorScheme
            // Both languages look the same: neither party is the main one.
            val color = if (t.phrase == Phrase.ReleaseToCancel) colors.error else colors.onSurfaceVariant
            val first = rememberLangResources(t.langs.first).getString(t.phrase.res, t.seconds)
            val second = rememberLangResources(t.langs.second).getString(t.phrase.res, t.seconds)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    first,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Languages without their own phrases fall back to English; don't print it twice.
                if (second != first) {
                    Text(
                        second,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
