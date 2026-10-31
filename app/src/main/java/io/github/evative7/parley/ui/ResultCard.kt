package io.github.evative7.parley.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.evative7.parley.R
import io.github.evative7.parley.state.CardCell
import io.github.evative7.parley.state.CardStage
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
) {
    val motion = MaterialTheme.motionScheme
    TappableCard(
        card, source = card.source, translation = card.translation,
        onCopy = onCopy, onDelete = onDelete, modifier = modifier,
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

            if (card.translation.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                StreamingText(
                    text = card.translation,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            ErrorRow(card, onRetry, retryEnabled, belowText = card.source.isNotEmpty() || waitingForSource)
        }
    }
}

/**
 * One side of a card in the landscape transcript: only the text in that side's language. The side that
 * reads the translation gets it large; the speaker's own words stay quiet, as in [ResultCard].
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
) {
    val motion = MaterialTheme.motionScheme
    val listening = card.stage == CardStage.Listening || card.stage == CardStage.Recognizing
    val text = when (cell) {
        is CardCell.Hearing -> cell.text
        is CardCell.Source -> cell.text
        is CardCell.Translation -> cell.text
    }
    val waiting = text.isEmpty() && when (cell) {
        is CardCell.Hearing -> listening
        is CardCell.Source -> false
        is CardCell.Translation -> card.stage == CardStage.Translating
    }
    // An error before anything was heard leaves this side blank; the other side carries the message.
    if (text.isEmpty() && !waiting && !showError) return

    val translated = cell is CardCell.Translation
    TappableCard(
        card,
        source = if (translated) "" else text,
        translation = if (translated) text else "",
        onCopy = onCopy, onDelete = onDelete, modifier = modifier, turn = turn,
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
 * A card that opens in a dialog once it has settled, showing [source] and [translation] with copy and delete.
 * A card still being heard or translated can't be deleted yet, and has nothing final to copy.
 */
@Composable
private fun TappableCard(
    card: CardState,
    source: String,
    translation: String,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    turn: Turn = Turn.None,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val settled = card.stage == CardStage.Done || card.stage == CardStage.Error
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface
    Card(
        onClick = { open = true },
        enabled = settled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        // Not tappable yet isn't disabled: it looks the same.
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
            disabledContainerColor = container,
            disabledContentColor = onContainer,
        ),
    ) {
        content()
    }
    if (open) {
        CardDialog(
            card, source, translation,
            onCopy = { open = false; onCopy(it) },
            onDelete = { open = false; onDelete() },
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
    translation: String,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    turn: Turn,
) {
    val colors = MaterialTheme.colorScheme
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
                    card.direction(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.size(16.dp))

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
                    if (source.isNotEmpty() && translation.isNotEmpty()) Spacer(Modifier.size(12.dp))
                    if (translation.isNotEmpty()) {
                        Text(
                            translation,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = colors.onSurface,
                        )
                    }
                    if (card.stage == CardStage.Error) {
                        if (source.isNotEmpty() || translation.isNotEmpty()) Spacer(Modifier.size(16.dp))
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_delete))
                    }
                    Spacer(Modifier.weight(1f))
                    // The translation if there is one: it's what the other person needs.
                    val copyText = translation.ifEmpty { source }
                    if (copyText.isNotEmpty()) {
                        FilledTonalButton(onClick = { onCopy(copyText) }) {
                            Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_copy))
                        }
                    }
                }
            }
        }
    }
}

/** Which way the card translates. */
private fun CardState.direction(): String =
    target?.let { to ->
        sourceLang?.displayName()?.let { "$it → ${to.displayName()}" } ?: "→ ${to.displayName()}"
    }
        // Until the speaker's language is known, either direction is possible.
        ?: "${langs.first.displayName()} ⇄ ${langs.second.displayName()}"

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
