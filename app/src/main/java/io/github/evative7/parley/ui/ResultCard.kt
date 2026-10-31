package io.github.evative7.parley.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.evative7.parley.R
import io.github.evative7.parley.state.CardCell
import io.github.evative7.parley.state.CardStage
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.ErrorKind

@Composable
fun ResultCard(
    card: CardState,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
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

            CardActions(
                card, onCopy, onDelete, onRetry, retryEnabled,
                direction = card.target?.let { to ->
                    card.sourceLang?.displayName()?.let { "$it → ${to.displayName()}" } ?: "→ ${to.displayName()}"
                }
                    // Until the speaker's language is known, either direction is possible.
                    ?: "${card.langs.first.displayName()} ⇄ ${card.langs.second.displayName()}",
            )
        }
    }
}

/**
 * One side of a card in the landscape transcript: only the text in that side's language. The side that
 * reads the translation gets it large; the speaker's own words stay quiet, as in [ResultCard].
 */
@Composable
fun ResultCell(
    card: CardState,
    cell: CardCell,
    showActions: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
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
    if (text.isEmpty() && !waiting && !showActions) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            if (waiting) {
                SkeletonLines(Modifier.fillMaxWidth())
            } else if (text.isNotEmpty()) {
                val translated = cell is CardCell.Translation
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
            if (showActions) {
                CardActions(card, onCopy, onDelete, onRetry, retryEnabled)
            } else {
                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

/**
 * The error row while failed, copy and delete once done. A [direction] label shares the row with copy and
 * delete, which keeps the row's height from the start so the card doesn't grow when they appear.
 */
@Composable
private fun CardActions(
    card: CardState,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
    direction: String? = null,
) {
    val motion = MaterialTheme.motionScheme
    if (card.stage == CardStage.Error) {
        Spacer(Modifier.size(12.dp))
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
            DeleteButton(onDelete)
        }
    }

    if (direction != null) {
        val failed = card.stage == CardStage.Error
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // The error row above already has its own delete button.
                .heightIn(min = if (failed) 0.dp else ActionRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = direction,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = card.stage == CardStage.Done,
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                Row {
                    CopyButton(onCopy)
                    DeleteButton(onDelete)
                }
            }
        }
        if (failed) Spacer(Modifier.size(8.dp))
        return
    }

    AnimatedVisibility(
        visible = card.stage == CardStage.Done,
        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            CopyButton(onCopy)
            DeleteButton(onDelete)
        }
    }
    if (card.stage != CardStage.Done) Spacer(Modifier.size(12.dp))
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_copy), stringResource(R.string.action_copy),
            Modifier.size(ActionIconSize),
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete),
            Modifier.size(ActionIconSize),
        )
    }
}

/** Smaller than the default 24 dp so the actions don't compete with the translation. */
private val ActionIconSize = 20.dp

/** An icon button's touch target, so the row is as tall before its buttons show as after. */
private val ActionRowHeight = 48.dp

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
