package io.github.evative7.parley.ui

import android.content.ClipData
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    // The menu that opens under the talk button, and the typing dialog it opens.
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(enabled = menuOpen && !faceToFace) { menuOpen = false }

    PhaseHaptics(state.phase)

    val busy = state.phase in setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating)
    // Speaking needs the room: a press folds the menu away.
    LaunchedEffect(busy) { if (busy) menuOpen = false }
    val needsSetup = settings != null && !settings.isReady
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
                    cell = { card, modifier ->
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
                    cell = { card, modifier ->
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
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // While the menu is open, a touch on the conversation folds it away first.
                        .then(if (menuOpen) Modifier.foldOnTouch { menuOpen = false } else Modifier),
                ) {
                    CardList(state.cards, listState, entered) { card, modifier ->
                        ResultCard(
                            card = card,
                            onCopy = onCopy,
                            onDelete = { vm.delete(card.id) },
                            onRetry = { vm.onRetry(card.id) },
                            retryEnabled = !busy,
                            onEdit = { vm.editSource(card.id, it) },
                            modifier = modifier,
                        )
                    }
                }

                BottomPanel(
                    open = menuOpen,
                    hint = {
                        HintLine(
                            phase = state.phase,
                            hint = state.hint,
                            langs = listOf(state.langs.first, state.langs.second),
                            elapsedMs = { elapsed.value },
                        )
                    },
                    talk = { pttButton(Modifier) },
                    toggle = {
                        MenuToggle(open = menuOpen, badge = needsSetup, enabled = !busy) { menuOpen = !menuOpen }
                    },
                    menu = {
                        MenuGroup(
                            langs = state.langs,
                            enabled = !busy,
                            needsSetup = needsSetup,
                            clearEnabled = state.cards.isNotEmpty(),
                            onFaceToFace = { menuOpen = false; onFaceToFace(true) },
                            onType = { menuOpen = false; typing = true },
                            onClear = { menuOpen = false; confirmClear = true },
                            onOpenSettings = { menuOpen = false; onOpenSettings() },
                        )
                    },
                )
            }
        }
    }

    if (typing) {
        ManualInputDialog(
            onSubmit = { typing = false; vm.submitText(it) },
            onDismiss = { typing = false },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_conversation_title)) },
            text = { Text(stringResource(R.string.clear_conversation_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearConversation() }) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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

/**
 * The hint and the talk button, and under them a menu. Opening it doesn't lay a sheet over the screen: the
 * whole panel grows, so the button and its hint rise with the menu, on a surface of their own.
 */
@Composable
private fun BottomPanel(
    open: Boolean,
    hint: @Composable () -> Unit,
    talk: @Composable () -> Unit,
    toggle: @Composable () -> Unit,
    menu: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val panel by animateColorAsState(
        if (open) colors.surfaceContainer else colors.surface, motion.defaultEffectsSpec(), label = "panel",
    )
    // Below the button: the usual margin closed; open, the menu takes over the room.
    val below by animateDpAsState(if (open) 16.dp else 28.dp, motion.defaultSpatialSpec(), label = "below")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            // Drawn, not clipped: the button's voice pulse may spread past the panel.
            .background(panel, PanelShape)
            .navigationBarsPadding(),
    ) {
        // A little room between the list and the hint, taken from below the button so the bar keeps its height.
        Spacer(Modifier.height(8.dp))
        hint()
        Row(
            Modifier.fillMaxWidth().padding(bottom = below),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            talk()
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { toggle() }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
                fadeOut(motion.fastEffectsSpec()),
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) { menu() }
        }
    }
}

private val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** Beside the talk button: opens the menu, and turns over to close it. A dot says the services need setup. */
@Composable
private fun MenuToggle(open: Boolean, badge: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec(), label = "toggle")
    val status = if (badge) stringResource(R.string.settings_required) else null
    // Tonal like the language buttons, and sized to hold its own beside the talk button.
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(56.dp)
            .semantics { if (status != null) stateDescription = status },
    ) {
        BadgedBox(badge = { if (badge && !open) Badge() }) {
            Icon(
                painterResource(R.drawable.ic_expand_less),
                stringResource(R.string.more_actions),
                Modifier.graphicsLayer { rotationZ = turn },
            )
        }
    }
}

/**
 * Face to face, typing instead of speaking, clearing the conversation, and service settings: four rows joined into one group,
 * each labeled in both languages of the conversation so either party can pick.
 */
@Composable
private fun MenuGroup(
    langs: LangPair,
    enabled: Boolean,
    needsSetup: Boolean,
    clearEnabled: Boolean,
    onFaceToFace: () -> Unit,
    onType: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        MenuRow(R.drawable.ic_face_to_face, R.string.face_to_face, langs, enabled, RowShapes.first, onClick = onFaceToFace)
        MenuRow(R.drawable.ic_add, R.string.manual_input, langs, enabled, RowShapes.middle, onClick = onType)
        MenuRow(
            R.drawable.ic_delete, R.string.clear_conversation, langs, enabled && clearEnabled, RowShapes.middle,
            onClick = onClear,
        )
        MenuRow(
            R.drawable.ic_settings, R.string.settings_title, langs, enabled, RowShapes.last,
            badge = needsSetup, onClick = onOpenSettings,
        )
    }
}

private val RowGap = 3.dp

/** Rows joined into one group: round at the group's outer ends, tight where two rows meet. */
private object RowShapes {
    private val outer = 24.dp
    private val inner = 6.dp
    val first = RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    val middle = RoundedCornerShape(inner)
    val last = RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
}

/** One option: its icon in a round badge, then its name in each language, one under the other. */
@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    langs: LangPair,
    enabled: Boolean,
    shape: Shape,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Both languages look the same, as in the hint: neither party is the main one.
    val lines = listOf(rememberLangResources(langs.first), rememberLangResources(langs.second))
        .map { it.getString(label) }
        .distinct()
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(badge = { if (badge) Badge() }) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                for (line in lines) {
                    Text(line, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
        }
    }
}

/** Calls [onTouch] on any touch inside, before the content sees it; the touch still reaches the content. */
private fun Modifier.foldOnTouch(onTouch: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
    }
}

// Separate cards, but close: the conversation should still read as one.
private val CardGap = 6.dp

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
    cell: @Composable (card: CardState, modifier: Modifier) -> Unit,
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
 */
@Composable
private fun CardList(
    cards: List<CardState>,
    listState: LazyListState,
    entered: MutableSet<Long>,
    turned: Boolean = false,
    content: @Composable (card: CardState, modifier: Modifier) -> Unit,
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
        items(cards, key = { it.id }) { card ->
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
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageButton(langs.first, onClick = { onPick(0) }, modifier = Modifier.weight(1f))
        IconButton(onClick = onSwap) {
            Icon(painterResource(R.drawable.ic_swap_horiz), stringResource(R.string.swap_languages))
        }
        LanguageButton(langs.second, onClick = { onPick(1) }, modifier = Modifier.weight(1f))
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
