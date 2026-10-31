package io.github.evative7.parley.ui

import android.content.ClipData
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
import io.github.evative7.parley.state.UiState
import io.github.evative7.parley.state.SettingsViewModel
import io.github.evative7.parley.state.cell
import io.github.evative7.parley.state.showsError
import io.github.evative7.parley.state.slotFor
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(vm: TranslateViewModel = viewModel(), settingsVm: SettingsViewModel = viewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Bumped on each opening, so the page never picks up the edits of the one still sliding out.
    var settingsVisit by rememberSaveable { mutableIntStateOf(0) }
    var faceToFace by rememberSaveable { mutableStateOf(false) }

    // The app stays portrait (see the manifest); face to face is always landscape. Lying flat on a table the
    // sensor can't tell which way is up, so neither follows rotation.
    val activity = LocalActivity.current
    DisposableEffect(faceToFace, activity) {
        if (faceToFace && activity != null) {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // Nothing between the two halves but the conversation; a swipe from an edge brings the bars back briefly.
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                bars.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = previous
            }
        } else {
            onDispose {}
        }
    }

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
            key(settingsVisit) { SettingsScreen(settingsVm, onBack = { showSettings = false }) }
        } else {
            ConversationScreen(
                vm,
                settings,
                faceToFace = faceToFace,
                onFaceToFace = { faceToFace = it },
                onOpenSettings = { settingsVisit++; showSettings = true },
            )
        }
    }
}

@Composable
private fun ConversationScreen(
    vm: TranslateViewModel,
    settings: ServiceSettings?,
    faceToFace: Boolean,
    onFaceToFace: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val volume = vm.volume.collectAsStateWithLifecycle()
    val elapsed = vm.elapsedMs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Which language bar slot the picker dialog is open for.
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val entered = remember { mutableSetOf<Long>() }
    // Face to face, each person has a list of their own.
    val faceLists = List(2) { rememberLazyListState() }
    val faceEntered = remember { List(2) { mutableSetOf<Long>() } }
    // A new recording adds its card at index 0; bring it into view even if the user had scrolled up.
    val newest = state.cards.firstOrNull()?.id
    LaunchedEffect(newest, faceToFace) {
        if (newest == null) return@LaunchedEffect
        if (faceToFace) faceLists.forEach { launch { it.animateScrollToItem(0) } } else listState.animateScrollToItem(0)
    }
    BackHandler(enabled = faceToFace) { onFaceToFace(false) }

    PhaseHaptics(state.phase)

    val busy = state.phase in setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating)
    val settingsButton = @Composable {
        SettingsButton(settings = settings, enabled = !busy, onClick = onOpenSettings)
    }
    val faceToFaceButton = @Composable {
        // Tonal like the language buttons, and sized to hold its own beside the talk button.
        FilledTonalIconButton(
            onClick = { onFaceToFace(!faceToFace) },
            enabled = !busy,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_face_to_face),
                stringResource(if (faceToFace) R.string.face_to_face_exit else R.string.face_to_face),
            )
        }
    }
    val onCopy = { text: String ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("translation", text)))
        }
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) vm.showHint(Hint.Copied)
    }
    val pttButton = @Composable { modifier: Modifier ->
        PttButton(
            phase = state.phase,
            volume = { volume.value },
            onPress = vm::onPress,
            onDrag = vm::onDrag,
            onRelease = vm::onRelease,
            onAbort = vm::onAbort,
            modifier = modifier,
            cancelAnyDirection = faceToFace,
        )
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        if (faceToFace) {
            // Flat on the table, one person at each end: each half is turned to face its reader.
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                FacingHalf(
                    slot = 0,
                    state = state,
                    listState = faceLists[0],
                    entered = faceEntered[0],
                    onPick = { picking = 0 },
                    cell = { card, modifier, shape ->
                        val slot = card.slotFor(state.langs, 0)
                        ResultCell(
                            card = card,
                            cell = card.cell(slot),
                            showError = card.showsError(slot),
                            onCopy = onCopy,
                            onDelete = { vm.delete(card.id) },
                            onRetry = { vm.onRetry(card.id) },
                            turn = Turn.Clockwise,
                            retryEnabled = !busy,
                            onEdit = { vm.editSource(card.id, it) },
                            modifier = modifier,
                            shape = shape,
                        )
                    },
                    // Above the button, whose voice pulse grows into the hints.
                    modifier = Modifier.weight(1f).zIndex(1f).turned(Turn.Clockwise),
                )
                // Back leaves face to face; settings wait until then. Each hint fills the blank above or below
                // the button, turned toward its reader on their left hand, and drawn over the voice pulse.
                Column(
                    Modifier.fillMaxHeight().width(FaceRailWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).zIndex(1f)) {
                        HintLine(
                            state.phase, state.hint, listOf(state.langs.first), { elapsed.value },
                            Modifier.turned(Turn.Clockwise).fillMaxSize(),
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                    pttButton(Modifier)
                    Box(Modifier.weight(1f).zIndex(1f)) {
                        HintLine(
                            state.phase, state.hint, listOf(state.langs.second), { elapsed.value },
                            Modifier.turned(Turn.CounterClockwise).fillMaxSize(),
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                }
                FacingHalf(
                    slot = 1,
                    state = state,
                    listState = faceLists[1],
                    entered = faceEntered[1],
                    onPick = { picking = 1 },
                    cell = { card, modifier, shape ->
                        val slot = card.slotFor(state.langs, 1)
                        ResultCell(
                            card = card,
                            cell = card.cell(slot),
                            showError = card.showsError(slot),
                            onCopy = onCopy,
                            onDelete = { vm.delete(card.id) },
                            onRetry = { vm.onRetry(card.id) },
                            turn = Turn.CounterClockwise,
                            retryEnabled = !busy,
                            onEdit = { vm.editSource(card.id, it) },
                            modifier = modifier,
                            shape = shape,
                        )
                    },
                    modifier = Modifier.weight(1f).zIndex(1f).turned(Turn.CounterClockwise),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                LanguageBar(
                    langs = state.langs,
                    onPick = { picking = it },
                    onSwap = vm::swapLangs,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 12.dp),
                    trailing = settingsButton,
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CardList(state.cards, listState, entered) { card, modifier, shape ->
                        ResultCard(
                            card = card,
                            onCopy = onCopy,
                            onDelete = { vm.delete(card.id) },
                            onRetry = { vm.onRetry(card.id) },
                            retryEnabled = !busy,
                            onEdit = { vm.editSource(card.id, it) },
                            modifier = modifier,
                            shape = shape,
                        )
                    }
                }

                // A little room between the list and the hint, taken from below the button so the bar keeps its height.
                HintLine(
                    phase = state.phase,
                    hint = state.hint,
                    langs = listOf(state.langs.first, state.langs.second),
                    elapsedMs = { elapsed.value },
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    pttButton(Modifier)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { faceToFaceButton() }
                }
            }
        }
    }

    picking?.let { slot ->
        LanguagePickerDialog(
            selected = state.langs[slot],
            other = state.langs[1 - slot],
            onPick = { vm.setLang(slot, it); picking = null },
            onDismiss = { picking = null },
            // Face to face, the picker turns toward whoever tapped it.
            turn = when {
                !faceToFace -> Turn.None
                slot == 0 -> Turn.Clockwise
                else -> Turn.CounterClockwise
            },
        )
    }
}

private val CardGap = 3.dp
private val CardOuterCorner = 28.dp
private val CardInnerCorner = 8.dp

/** The face-to-face center column: the button with a hint above and below it, each as deep as this. */
private val FaceRailWidth = 136.dp

/**
 * One person's half of the face-to-face view, laid out from their side before it is turned toward them:
 * the conversation reads top to bottom like a chat, newest last, just above their language picker at the
 * edge nearest them.
 */
@Composable
private fun FacingHalf(
    slot: Int,
    state: UiState,
    listState: LazyListState,
    entered: MutableSet<Long>,
    onPick: () -> Unit,
    cell: @Composable (card: CardState, modifier: Modifier, shape: Shape) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CardList(state.cards, listState, entered, turned = true, content = cell)
        }
        LanguageButton(
            state.langs[slot],
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * Newest card at the bottom, each rising into place once. [entered] holds cards that already played their
 * entrance; scrolling back must not replay it. A [turned] face-to-face half scrolls by its own drag handling.
 * Each new card scrolls the list to the bottom (see the caller), and the newest card grows upward from there.
 *
 * The whole conversation is one connected group: round at its two ends, tight where two cards meet.
 * A card's corners ease into place when a new card or a deletion moves it to or from an end.
 */
@Composable
private fun CardList(
    cards: List<CardState>,
    listState: LazyListState,
    entered: MutableSet<Long>,
    turned: Boolean = false,
    content: @Composable (card: CardState, modifier: Modifier, shape: Shape) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val rise = with(LocalDensity.current) { 48.dp.toPx() }
    // Tighter in portrait, where the whole conversation shares one list.
    val edge = if (turned) 12.dp else 8.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().then(if (turned) Modifier.turnedScroll(listState, reversed = true) else Modifier),
        state = listState,
        userScrollEnabled = !turned,
        // Newest first in the list, so index 0 sits at the bottom.
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = edge),
        verticalArrangement = Arrangement.spacedBy(CardGap, Alignment.Bottom),
    ) {
        itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
            // The newest card closes the group at the bottom, the oldest opens it at the top.
            val top by animateDpAsState(
                if (index == cards.lastIndex) CardOuterCorner else CardInnerCorner, motion.defaultSpatialSpec(), label = "top",
            )
            val bottom by animateDpAsState(
                if (index == 0) CardOuterCorner else CardInnerCorner, motion.defaultSpatialSpec(), label = "bottom",
            )
            val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
            val enter = remember(card.id) { Animatable(if (card.id in entered) 1f else 0f) }
            LaunchedEffect(card.id) {
                entered += card.id
                enter.animateTo(1f, motion.slowSpatialSpec())
            }
            content(
                card,
                Modifier
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
                shape,
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

/** Opens the service settings; a dot says they need attention. */
@Composable
private fun SettingsButton(settings: ServiceSettings?, enabled: Boolean, onClick: () -> Unit) {
    val needsSetup = settings != null && !settings.isReady
    val status = if (needsSetup) stringResource(R.string.settings_required) else null
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { if (status != null) stateDescription = status },
    ) {
        BadgedBox(
            badge = { if (needsSetup) Badge() },
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
            // In its own script, so each party recognizes theirs whatever the phone's language.
            Text(l.nativeName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private data class HintText(val phrase: Phrase, val seconds: Int, val langs: List<Lang>)

/**
 * What the button does right now, in each of [langs], so whoever holds the phone can read it.
 * Face to face gives each person their own hint in their own language, wrapping up to [maxLines] within the
 * space the [modifier] gives it.
 */
@Composable
private fun HintLine(
    phase: PttPhase,
    hint: Hint?,
    langs: List<Lang>,
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
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
        modifier.zIndex(1f).fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
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
            val lines = t.langs.map { rememberLangResources(it).getString(t.phrase.res, t.seconds) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Languages without their own phrases fall back to English; don't print it twice.
                for (line in lines.distinct()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        textAlign = TextAlign.Center,
                        maxLines = maxLines,
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
