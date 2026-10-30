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
import androidx.compose.ui.unit.dp
import io.github.evative7.parley.R
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
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                text = card.target?.let { to ->
                    card.sourceLang?.displayName()?.let { "$it → ${to.displayName()}" } ?: "→ ${to.displayName()}"
                }
                    // Until the speaker's language is known, either direction is possible.
                    ?: "${card.langs.first.displayName()} ⇄ ${card.langs.second.displayName()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

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
                Spacer(Modifier.size(12.dp))
                StreamingText(
                    text = card.translation,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

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

            AnimatedVisibility(
                visible = card.stage == CardStage.Done,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            painterResource(R.drawable.ic_copy), stringResource(R.string.action_copy),
                            Modifier.size(ActionIconSize),
                        )
                    }
                    DeleteButton(onDelete)
                }
            }
            if (card.stage != CardStage.Done) Spacer(Modifier.size(12.dp))
        }
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
