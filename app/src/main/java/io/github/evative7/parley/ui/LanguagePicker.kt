package io.github.evative7.parley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.evative7.parley.R
import java.util.Locale
import io.github.evative7.parley.model.Lang

/**
 * Picks the language for one side of the conversation; [other] is what the other side currently uses.
 * Face to face, [turn] points the dialog toward the person who opened it.
 */
@Composable
fun LanguagePickerDialog(
    selected: Lang,
    other: Lang,
    onPick: (Lang) -> Unit,
    onDismiss: () -> Unit,
    turn: Turn = Turn.None,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // Turned, the dialog's width runs along the short side of the screen; keep it off the edges.
        modifier = if (turn == Turn.None) Modifier else Modifier.padding(vertical = 24.dp).turned(turn),
        // The platform width cap would become the turned dialog's height and leave room for only a few rows.
        properties = DialogProperties(usePlatformDefaultWidth = turn == Turn.None),
    ) {
        TurnedDialogWindow(turn)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                // English only: whoever picks may not read the device language or the current pair.
                Text(
                    "Select language",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                val scroll = rememberScrollState()
                val density = LocalDensity.current
                // Open with the current choice in view rather than at the top of the list.
                var selectedTop by remember { mutableFloatStateOf(-1f) }
                LaunchedEffect(selectedTop >= 0) {
                    if (selectedTop >= 0) scroll.scrollTo((selectedTop - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0))
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = if (turn == Turn.None) 440.dp else 450.dp)
                        .scrollbar(scroll)
                        .then(if (turn == Turn.None) Modifier else Modifier.turnedScroll(scroll))
                        .verticalScroll(scroll, enabled = turn == Turn.None)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Lang.targets.forEach { lang ->
                        LanguageRow(
                            lang = lang,
                            selected = lang == selected,
                            inOtherSlot = lang == other,
                            onClick = { onPick(lang) },
                            modifier = if (lang == selected) {
                                Modifier.onPlaced { selectedTop = it.positionInParent().y }
                            } else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    lang: Lang,
    selected: Boolean,
    inOtherSlot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Each language in its own script, so the other party can find theirs; English under it.
            val native = lang.nativeName()
            Text(native, style = MaterialTheme.typography.bodyLarge, color = content)
            val english = lang.displayName(Locale.ENGLISH)
            if (english != native) {
                Text(
                    english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) content.copy(alpha = 0.72f) else colors.onSurfaceVariant,
                )
            }
        }
        when {
            selected -> Icon(painterResource(R.drawable.ic_check), null, tint = content)
            inOtherSlot -> SwapBadge()
        }
    }
}

/** Marks the language the other side holds: picking it swaps the two. */
@Composable
private fun SwapBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_swap_horiz), null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.lang_in_other_slot), style = MaterialTheme.typography.labelMedium)
        }
    }
}
