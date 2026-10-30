package io.github.evative7.parley.ui

import android.content.ClipData
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.evative7.parley.R
import io.github.evative7.parley.debug.DebugLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Floating, draggable log window shown above every screen while debug mode is on.
 * Only the window itself takes touches; the rest of the screen stays usable.
 */
@Composable
fun DebugOverlay() {
    val enabled by DebugLog.enabled.collectAsStateWithLifecycle()
    if (!enabled) return
    val entries by DebugLog.entries.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val initialY = with(LocalDensity.current) { 64.dp.toPx() }
    // Relative to the top-end corner; x is never positive, y never negative.
    var offset by remember { mutableStateOf(Offset(0f, initialY)) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        val maxX = (constraints.maxWidth - windowSize.width).coerceAtLeast(0).toFloat()
        val maxY = (constraints.maxHeight - windowSize.height).coerceAtLeast(0).toFloat()
        val clamp by rememberUpdatedState { o: Offset -> Offset(o.x.coerceIn(-maxX, 0f), o.y.coerceIn(0f, maxY)) }
        val drag = Modifier.pointerInput(Unit) {
            detectDragGestures { change, amount ->
                change.consume()
                offset = clamp(offset + amount)
            }
        }
        val place = Modifier
            .align(Alignment.TopEnd)
            .offset { clamp(offset).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .onSizeChanged { windowSize = it }

        if (expanded) {
            LogWindow(entries, onCollapse = { expanded = false }, dragHandle = drag, modifier = place)
        } else {
            Surface(
                onClick = { expanded = true },
                shape = CircleShape,
                color = WindowColor,
                contentColor = Accent,
                shadowElevation = 6.dp,
                modifier = place.then(drag).size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_bug), stringResource(R.string.debug_log_expand), Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun LogWindow(
    entries: List<DebugLog.Entry>,
    onCollapse: () -> Unit,
    dragHandle: Modifier,
    modifier: Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }
    val listState = rememberLazyListState()
    // Follow new lines unless the user has scrolled back to read older ones.
    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty() && !listState.canScrollForward) listState.scrollToItem(entries.lastIndex)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WindowColor,
        contentColor = TextColor,
        shadowElevation = 6.dp,
        modifier = modifier.width(320.dp).height(280.dp),
    ) {
        Column {
            Row(
                dragHandle.fillMaxWidth().height(40.dp).padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_bug), null, Modifier.size(16.dp), tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(R.string.debug_log_title)} · ${entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                WindowButton(R.drawable.ic_copy, stringResource(R.string.debug_log_copy)) {
                    val text = entries.joinToString("\n") { "${format.format(Date(it.wallMs))} ${it.tag} ${it.message}" }
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Parley log", text))) }
                }
                WindowButton(R.drawable.ic_delete, stringResource(R.string.debug_log_clear), DebugLog::clear)
                WindowButton(R.drawable.ic_minimize, stringResource(R.string.debug_log_collapse), onCollapse)
            }
            HorizontalDivider(color = TextColor.copy(alpha = 0.12f))
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.debug_log_empty), style = MaterialTheme.typography.bodySmall, color = DimColor)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries, key = { it.id }) { entry -> LogLine(entry, format) }
                }
            }
        }
    }
}

@Composable
private fun WindowButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(painterResource(icon), description, Modifier.size(18.dp))
    }
}

@Composable
private fun LogLine(entry: DebugLog.Entry, format: SimpleDateFormat) {
    val failed = "failed" in entry.message
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = DimColor)) { append(format.format(Date(entry.wallMs))) }
            append(' ')
            withStyle(SpanStyle(color = tagColor(entry.tag), fontWeight = FontWeight.Bold)) { append(entry.tag) }
            append(' ')
            withStyle(SpanStyle(color = if (failed) ErrorColor else TextColor)) { append(entry.message) }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 13.sp,
    )
}

private fun tagColor(tag: String) = when (tag) {
    "Flow" -> Color(0xFF8AB4F8)
    "State" -> Color(0xFFC58AF9)
    "ASR", "DashScope" -> Color(0xFF81C995)
    "Translate" -> Color(0xFFFDD663)
    else -> Color(0xFF78D9EC)
}

// Fixed dark palette: the window must stay readable over both light and dark screens.
private val WindowColor = Color(0xE6121418)
private val TextColor = Color(0xFFE6E8EE)
private val DimColor = Color(0xFF9AA0A6)
private val Accent = Color(0xFFFFCC00)
private val ErrorColor = Color(0xFFF28B82)
