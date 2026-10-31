package io.github.evative7.parley.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.evative7.parley.R
import io.github.evative7.parley.state.CardCell
import io.github.evative7.parley.state.CardStage
import io.github.evative7.parley.state.CardTranslation
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.ErrorKind

@Composable
fun ResultCard(
    card: CardState,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
    onEdit: ((String) -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    turn: Turn = Turn.None,
    // Face to face, a side shows only the translation into its own language.
    translations: List<CardTranslation> = card.translations,
) {
    val motion = MaterialTheme.motionScheme
    TappableCard(
        card, source = card.source, translations = translations,
        onCopy = onCopy, onDelete = onDelete, modifier = modifier, turn = turn,
        onEdit = onEdit, editEnabled = retryEnabled, shape = shape,
    ) {
        Column(
            Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(16.dp),
        ) {
            val waitingForSource = card.source.isEmpty() &&
                (card.stage == CardStage.Listening || card.stage == CardStage.Recognizing)
            if (waitingForSource) {
                SkeletonLines(Modifier.fillMaxWidth())
            } else if (card.source.isNotEmpty()) {
                StreamingText(
                    text = card.source,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Translations(translations, streaming = true)

            ErrorRow(card, onRetry, retryEnabled, belowText = card.source.isNotEmpty() || waitingForSource)
        }
    }
}

/**
 * One side of a card in the landscape transcript: only the text in that side's language. The side that
 * reads the translation gets it large; the speaker's own words stay quiet, as in [ResultCard].
 * Speech translated both ways belongs to neither side, so each gets the whole card: the speech over the
 * translation into its own language.
 * Either side's tap opens the card, turned toward that side's reader by [turn].
 */
@Composable
fun ResultCell(
    card: CardState,
    cell: CardCell,
    showError: Boolean,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    turn: Turn,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
    onEdit: ((String) -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    val motion = MaterialTheme.motionScheme
    val listening = card.stage == CardStage.Listening || card.stage == CardStage.Recognizing
    val text = when (cell) {
        is CardCell.Hearing -> cell.text
        is CardCell.Source -> cell.text
        is CardCell.Translation -> cell.translation.text
        is CardCell.Whole -> {
            ResultCard(card, onCopy, onDelete, onRetry, modifier, retryEnabled, onEdit, shape, turn, listOf(cell.translation))
            return
        }
    }
    val waiting = text.isEmpty() && when (cell) {
        is CardCell.Hearing -> listening
        is CardCell.Source -> false
        is CardCell.Translation -> card.stage == CardStage.Translating
        is CardCell.Whole -> false
    }
    // An error before anything was heard leaves this side blank; the other side carries the message.
    if (text.isEmpty() && !waiting && !showError) return

    val translated = cell is CardCell.Translation
    TappableCard(
        card,
        source = if (translated) "" else text,
        translations = if (cell is CardCell.Translation) listOf(cell.translation) else emptyList(),
        onCopy = onCopy, onDelete = onDelete, modifier = modifier, turn = turn,
        onEdit = onEdit, editEnabled = retryEnabled, shape = shape,
    ) {
        Column(
            Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(20.dp),
        ) {
            if (waiting) {
                SkeletonLines(Modifier.fillMaxWidth())
            } else if (text.isNotEmpty()) {
                StreamingText(
                    text = text,
                    style = if (translated) {
                        MaterialTheme.typography.headlineSmallEmphasized
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (translated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showError) ErrorRow(card, onRetry, retryEnabled, belowText = text.isNotEmpty() || waiting)
        }
    }
}

/**
 * A card that opens in a dialog once it has settled, on a tap or a long press, showing [source] and
 * [translations] with copy and delete, and a correction of the recognized text when [onEdit] is given.
 * A card still being heard or translated can't be opened yet, and has nothing final to copy.
 */
@Composable
private fun TappableCard(
    card: CardState,
    source: String,
    translations: List<CardTranslation>,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    turn: Turn = Turn.None,
    onEdit: ((String) -> Unit)? = null,
    editEnabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val settled = card.stage == CardStage.Done || card.stage == CardStage.Error
    val haptics = LocalHapticFeedback.current
    // Not tappable yet isn't disabled: it looks the same.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                enabled = settled,
                onClick = { open = true },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    open = true
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content()
    }
    if (open) {
        val edit = onEdit?.takeIf { card.source.isNotEmpty() }
        CardDialog(
            card, source, translations.filter { it.text.isNotEmpty() },
            onCopy = { open = false; onCopy(it) },
            onDelete = { open = false; onDelete() },
            onEdit = edit?.let { { text: String -> open = false; it(text) } },
            editEnabled = editEnabled,
            onDismiss = { open = false },
            turn = turn,
        )
    }
}

/** A card opened up: its direction, its text in full, and what can be done with it. */
@Composable
private fun CardDialog(
    card: CardState,
    source: String,
    translations: List<CardTranslation>,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: ((String) -> Unit)?,
    editEnabled: Boolean,
    onDismiss: () -> Unit,
    turn: Turn,
) {
    val colors = MaterialTheme.colorScheme
    var editing by rememberSaveable { mutableStateOf(false) }
    // Face to face, the editor turns back upright: the keyboard follows the device, not either reader.
    val turn = if (editing) Turn.None else turn
    Dialog(
        onDismissRequest = onDismiss,
        // Turned, the platform width cap would become the dialog's height.
        properties = DialogProperties(usePlatformDefaultWidth = turn == Turn.None),
    ) {
        Surface(
            modifier = if (turn == Turn.None) {
                Modifier
            } else {
                // About as wide as the portrait dialog.
                Modifier.padding(vertical = 24.dp).turned(turn).widthIn(max = 360.dp)
            },
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    if (editing) stringResource(R.string.edit_source_title) else card.direction(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.size(16.dp))

                if (editing && onEdit != null) {
                    SourceEditor(card.source, enabled = editEnabled, onSubmit = onEdit, onCancel = { editing = false })
                } else {
                    // A long translation scrolls; the buttons stay in place.
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .then(if (turn == Turn.None) Modifier else Modifier.turnedScroll(scroll))
                            .verticalScroll(scroll, enabled = turn == Turn.None)
                            .padding(horizontal = 24.dp),
                    ) {
                        if (source.isNotEmpty()) {
                            Text(source, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        Translations(translations, streaming = false, spaced = source.isNotEmpty())
                        if (card.stage == CardStage.Error) {
                            if (source.isNotEmpty() || translations.isNotEmpty()) Spacer(Modifier.size(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_error), null, Modifier.size(20.dp), tint = colors.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(card.error.message()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.error,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(24.dp))
                    // Translations first: they're what the other person needs.
                    val plain = source.isEmpty() || translations.isEmpty()
                    val copyLabel = stringResource(R.string.action_copy)
                    val copyTranslation = stringResource(R.string.action_copy_translation)
                    val copyOriginal = stringResource(R.string.action_copy_original)
                    val named = translations.associateWith {
                        stringResource(R.string.action_copy_translation_to, it.to.nativeName())
                    }
                    val editLabel = stringResource(R.string.action_edit_source)
                    val actions = buildList {
                        for (t in translations) {
                            val label = when {
                                translations.size > 1 -> named.getValue(t)
                                plain -> copyLabel
                                else -> copyTranslation
                            }
                            add(Action(R.drawable.ic_copy, label) { onCopy(t.text) })
                        }
                        if (source.isNotEmpty()) {
                            add(Action(R.drawable.ic_copy, if (plain) copyLabel else copyOriginal) { onCopy(source) })
                        }
                        if (onEdit != null) {
                            add(Action(R.drawable.ic_edit, editLabel, enabled = editEnabled) { editing = true })
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ActionGroup(actions)
                        if (actions.isNotEmpty()) Spacer(Modifier.size(12.dp))
                        ActionBar(
                            Action(R.drawable.ic_delete, stringResource(R.string.action_delete), onClick = onDelete),
                            shape = BarShapes.single,
                            container = colors.errorContainer,
                            content = colors.onErrorContainer,
                            badge = colors.error,
                            onBadge = colors.onError,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The card's translations under its speech. With more than one, each is named by its language, in that
 * language, so each reader finds theirs. [streaming] text grows as it arrives; the dialog shows it settled.
 */
@Composable
private fun Translations(translations: List<CardTranslation>, streaming: Boolean, spaced: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val named = translations.size > 1
    translations.filter { it.text.isNotEmpty() }.forEachIndexed { i, t ->
        if (spaced || i > 0) Spacer(Modifier.size(if (named) 12.dp else 8.dp))
        if (named) {
            Text(t.to.nativeName(), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Spacer(Modifier.size(2.dp))
        }
        if (streaming) {
            StreamingText(text = t.text, style = MaterialTheme.typography.headlineSmallEmphasized, color = colors.onSurface)
        } else {
            Text(t.text, style = MaterialTheme.typography.headlineSmallEmphasized, color = colors.onSurface)
        }
    }
}

/** The recognized text, open for correction before it is translated again. */
@Composable
private fun SourceEditor(source: String, enabled: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf(source) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = MaterialTheme.typography.bodyLarge,
        minLines = 3,
        maxLines = 8,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .focusRequester(focus),
    )
    Spacer(Modifier.size(20.dp))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(BarGap)) {
        ActionBar(
            Action(
                R.drawable.ic_translate, stringResource(R.string.action_retranslate),
                // Unchanged text would only translate the same thing again.
                enabled = enabled && text.isNotBlank() && text.trim() != source,
            ) { onSubmit(text) },
            shape = BarShapes.first,
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            badge = colors.primary,
            onBadge = colors.onPrimary,
        )
        ActionBar(
            Action(R.drawable.ic_arrow_back, stringResource(R.string.action_cancel), onClick = onCancel),
            shape = BarShapes.last,
        )
    }
}

private class Action(
    @DrawableRes val icon: Int,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private val BarGap = 3.dp

/** Bars stacked into one group: round at the group's outer ends, tight where two bars meet. */
private object BarShapes {
    private val outer = 20.dp
    private val inner = 6.dp
    val single = RoundedCornerShape(outer)
    val first = RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    val middle = RoundedCornerShape(inner)
    val last = RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)

    fun at(index: Int, count: Int) = when {
        count == 1 -> single
        index == 0 -> first
        index == count - 1 -> last
        else -> middle
    }
}

@Composable
private fun ActionGroup(actions: List<Action>) {
    if (actions.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BarGap)) {
        actions.forEachIndexed { i, action ->
            // The first action leads; the rest stay quiet.
            ActionBar(
                action,
                shape = BarShapes.at(i, actions.size),
                badge = if (i == 0) colors.primary else colors.secondaryContainer,
                onBadge = if (i == 0) colors.onPrimary else colors.onSecondaryContainer,
            )
        }
    }
}

/** A full-width action: an icon in a round badge, then its label. */
@Composable
private fun ActionBar(
    action: Action,
    shape: RoundedCornerShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    badge: Color = MaterialTheme.colorScheme.secondaryContainer,
    onBadge: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        onClick = action.onClick,
        enabled = action.enabled,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (action.enabled) 1f else 0.38f),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(action.icon), null, Modifier.size(20.dp), tint = onBadge)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Which way the card translates, each language named in itself so both readers find theirs. */
private fun CardState.direction(): String {
    // Until the speech has been recognized, either direction is possible.
    if (translations.isEmpty()) return "${langs.first.nativeName()} ⇄ ${langs.second.nativeName()}"
    val to = translations.joinToString(" · ") { it.to.nativeName() }
    return sourceLang?.let { "${it.nativeName()} → $to" } ?: "→ $to"
}

/** What went wrong, with a retry when there is recognized text to resend. */
@Composable
private fun ErrorRow(card: CardState, onRetry: () -> Unit, retryEnabled: Boolean, belowText: Boolean) {
    if (card.stage != CardStage.Error) return
    // Alone in the card, it takes the card's own padding.
    if (belowText) Spacer(Modifier.size(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_error), null,
            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(card.error.message()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        // Without recognized text there is nothing to resend.
        if (card.canRetry) {
            TextButton(onClick = onRetry, enabled = retryEnabled) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

private fun ErrorKind?.message(): Int = when (this) {
    ErrorKind.Microphone -> R.string.error_mic
    ErrorKind.NoSpeech -> R.string.error_no_speech
    ErrorKind.Timeout -> R.string.error_timeout
    ErrorKind.Configuration -> R.string.error_configuration
    ErrorKind.Authentication -> R.string.error_authentication
    ErrorKind.Permission -> R.string.error_permission
    ErrorKind.RateLimit -> R.string.error_rate_limit
    ErrorKind.Network -> R.string.error_network
    ErrorKind.Service -> R.string.error_service
    ErrorKind.Unknown, null -> R.string.error_generic
}
