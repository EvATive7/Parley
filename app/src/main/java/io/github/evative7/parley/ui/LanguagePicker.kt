package io.github.evative7.parley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.evative7.parley.R
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                Text(
                    stringResource(R.string.pick_language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                // Open with the current choice in view rather than at the top of the list.
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (Lang.targets.indexOf(selected) - 2).coerceAtLeast(0),
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = if (turn == Turn.None) 440.dp else 600.dp)
                        .then(if (turn == Turn.None) Modifier else Modifier.turnedScroll(listState)),
                    state = listState,
                    userScrollEnabled = turn == Turn.None,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(Lang.targets, key = { it.tag }) { lang ->
                        LanguageRow(
                            lang = lang,
                            selected = lang == selected,
                            inOtherSlot = lang == other,
                            onClick = { onPick(lang) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: Lang, selected: Boolean, inOtherSlot: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Each language in its own script, so the other party can find theirs too.
            val native = lang.nativeName()
            Text(native, style = MaterialTheme.typography.bodyLarge, color = content)
            val local = lang.displayName()
            if (local != native) {
                Text(
                    local,
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
