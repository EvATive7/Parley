package io.github.evative7.parley.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.evative7.parley.R
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.SettingsError
import io.github.evative7.parley.data.TranslationPreset
import io.github.evative7.parley.state.SettingsViewModel

/** Service settings: one card per service; storage trouble surfaces as a snackbar. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val config by vm.settings.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveFailed by vm.saveFailed.collectAsStateWithLifecycle()
    val debugMode by vm.debugMode.collectAsStateWithLifecycle()

    val saved = config
    if (saved == null) {
        BackHandler(onBack = onBack)
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
        return
    }

    // Keys must not enter savedInstanceState / rememberSaveable.
    val baseline = remember(saved) { saved.copy(storageError = false) }
    var draft by remember(saved) { mutableStateOf(baseline) }
    // Missing fields are only pointed out once the user tries to save; malformed URLs right away.
    var attempted by remember(saved) { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // Text fields only report edits outward; bumping this rebuilds them from the draft after Undo.
    var revision by remember(saved) { mutableIntStateOf(0) }
    val dirty = draft != baseline
    val errors = draft.validationErrors()
    val shown = if (attempted) errors else emptyList()
    val leave = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = !saving) { leave() }

    val snackbar = remember { SnackbarHostState() }
    val storageError = stringResource(R.string.settings_storage_error)
    val saveError = stringResource(R.string.settings_save_failed)
    LaunchedEffect(saved.storageError) {
        if (saved.storageError) snackbar.showSnackbar(storageError, duration = SnackbarDuration.Long)
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) snackbar.showSnackbar(saveError)
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = leave, enabled = !saving) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            SaveBar(
                visible = dirty,
                saving = saving,
                onRevert = { draft = baseline; attempted = false; revision++ },
                onSave = {
                    if (errors.isEmpty()) vm.save(draft, onBack) else attempted = true
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            key(saved, revision) {
                ServiceCard(
                    icon = R.drawable.ic_mic,
                    title = stringResource(R.string.settings_asr),
                    provider = draft.asrService.provider.label(),
                    hint = draft.asrService.provider.hintResource()?.let { stringResource(it) },
                ) {
                    AsrServiceField(draft.asrService, !saving) { draft = draft.copy(asrService = it) }
                    when (draft.asrService.provider) {
                        AsrProvider.DashScope -> {
                            DashScopeRegionField(draft.dashScopeRegion, !saving) {
                                draft = draft.copy(dashScopeRegion = it)
                            }
                            SecretField(
                                stringResource(R.string.settings_key), draft.dashScopeKey, !saving,
                                SettingsError.AsrKey.shownIn(shown),
                            ) { draft = draft.copy(dashScopeKey = it) }
                        }
                        AsrProvider.OpenAi -> SecretField(
                            stringResource(R.string.settings_key), draft.openAiAsrKey, !saving,
                            SettingsError.AsrKey.shownIn(shown),
                        ) { draft = draft.copy(openAiAsrKey = it) }
                        AsrProvider.Volcengine -> {
                            SettingField(
                                stringResource(R.string.settings_app_id), draft.volcAppId, !saving,
                                SettingsError.AsrAppId.shownIn(shown), keyboardType = KeyboardType.Text,
                            ) { draft = draft.copy(volcAppId = it) }
                            SecretField(
                                stringResource(R.string.settings_access_token), draft.volcAccessToken, !saving,
                                SettingsError.AsrKey.shownIn(shown),
                            ) { draft = draft.copy(volcAccessToken = it) }
                        }
                    }
                }

                ServiceCard(
                    icon = R.drawable.ic_translate,
                    title = stringResource(R.string.settings_translation),
                    provider = stringResource(R.string.settings_translation_provider),
                ) {
                    TranslationServiceList(
                        draft = draft,
                        enabled = !saving,
                        showError = shown.any { it in TranslationErrors },
                        onSelect = { draft = draft.selectTranslation(it) },
                        onSave = { draft = draft.putTranslationPreset(it) },
                        onDelete = { draft = draft.removeTranslationPreset(it.id) },
                    )
                }
            }

            DebugModeCard(debugMode, onChange = vm::setDebugMode)

            PrivacyNote()
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) {
                    Text(stringResource(R.string.settings_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.settings_keep_editing))
                }
            },
        )
    }
}

/** Looks like the text fields around it; picking happens in a dialog. */
@Composable
private fun AsrServiceField(selected: AsrService, enabled: Boolean, onPick: (AsrService) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { open = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = FieldHeight).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_asr_service),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(selected.label(), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
        }
    }
    if (open) {
        BasicAlertDialog(onDismissRequest = { open = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(vertical = 20.dp)) {
                    Text(
                        stringResource(R.string.settings_asr_service),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        AsrProvider.entries.forEach { provider ->
                            item(key = "provider-${provider.name}") {
                                Text(
                                    provider.label(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(AsrService.entries.filter { it.provider == provider }, key = { it.id }) { service ->
                                // A pill clips the corners of multi-line descriptions; a rounded rect does not.
                                val isSelected = service == selected
                                Surface(
                                    onClick = { onPick(service); open = false },
                                    shape = MaterialTheme.shapes.large,
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(service.label(), style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                service.description(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isSelected) {
                                            Spacer(Modifier.width(12.dp))
                                            Icon(painterResource(R.drawable.ic_check), null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashScopeRegionField(selected: DashScopeRegion, enabled: Boolean, onPick: (DashScopeRegion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.settings_dashscope_region),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Filled connected buttons, so they sit with the filled fields instead of drawing outlines.
        val regions = DashScopeRegion.entries
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            regions.forEachIndexed { index, region ->
                ToggleButton(
                    checked = selected == region,
                    onCheckedChange = { onPick(region) },
                    enabled = enabled,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        regions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                ) {
                    Text(stringResource(if (region == DashScopeRegion.Beijing) R.string.settings_region_beijing else R.string.settings_region_singapore))
                }
            }
        }
    }
}

@Composable
private fun AsrProvider.label(): String = stringResource(when (this) {
    AsrProvider.DashScope -> R.string.settings_provider_dashscope
    AsrProvider.OpenAi -> R.string.settings_provider_openai
    AsrProvider.Volcengine -> R.string.settings_provider_volcengine
})

@StringRes
private fun AsrProvider.hintResource(): Int? = when (this) {
    AsrProvider.DashScope -> R.string.settings_dashscope_hint
    AsrProvider.OpenAi -> null
    AsrProvider.Volcengine -> R.string.settings_volcengine_hint
}

@Composable
private fun AsrService.label(): String = stringResource(when (this) {
    AsrService.FunAsrRealtimeStream -> R.string.settings_asr_fun_stream
    AsrService.FunAsrRealtimeNonStream -> R.string.settings_asr_fun_non_stream
    AsrService.GptRealtimeWhisper -> R.string.settings_asr_gpt_realtime
    AsrService.Gpt4oTranscribeNonStream -> R.string.settings_asr_gpt_4o
    AsrService.Gpt4oMiniTranscribeNonStream -> R.string.settings_asr_gpt_4o_mini
    AsrService.DoubaoSeedAsr20Stream -> R.string.settings_asr_doubao_stream
    AsrService.DoubaoSeedAsr20NonStream -> R.string.settings_asr_doubao_non_stream
    AsrService.DoubaoSeedAsr20NonStreamTurbo -> R.string.settings_asr_doubao_turbo
})

@Composable
private fun AsrService.description(): String = stringResource(when (this) {
    AsrService.FunAsrRealtimeStream -> R.string.settings_asr_fun_stream_desc
    AsrService.FunAsrRealtimeNonStream -> R.string.settings_asr_fun_non_stream_desc
    AsrService.GptRealtimeWhisper -> R.string.settings_asr_gpt_realtime_desc
    AsrService.Gpt4oTranscribeNonStream -> R.string.settings_asr_gpt_4o_desc
    AsrService.Gpt4oMiniTranscribeNonStream -> R.string.settings_asr_gpt_4o_mini_desc
    AsrService.DoubaoSeedAsr20Stream -> R.string.settings_asr_doubao_stream_desc
    AsrService.DoubaoSeedAsr20NonStream -> R.string.settings_asr_doubao_non_stream_desc
    AsrService.DoubaoSeedAsr20NonStreamTurbo -> R.string.settings_asr_doubao_turbo_desc
})

@Composable
private fun ServiceCard(
    @DrawableRes icon: Int,
    title: String,
    provider: String,
    hint: String? = null,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(provider, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            fields()
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

private val TranslationErrors = setOf(SettingsError.TranslationUrl, SettingsError.TranslationModel, SettingsError.TranslationKey)

/**
 * The saved translation services; the selected one is used. Selecting, adding, editing and
 * deleting all change the draft, so they are applied (or undone) with the rest of the page.
 */
@Composable
private fun TranslationServiceList(
    draft: ServiceSettings,
    enabled: Boolean,
    showError: Boolean,
    onSelect: (TranslationPreset) -> Unit,
    onSave: (TranslationPreset) -> Unit,
    onDelete: (TranslationPreset) -> Unit,
) {
    var editing by remember { mutableStateOf<TranslationPreset?>(null) }
    val colors = MaterialTheme.colorScheme
    val presets = draft.translationPresets

    if (presets.isEmpty()) {
        Text(
            stringResource(R.string.settings_translation_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
    presets.forEach { preset ->
        val selected = preset.id == draft.translationPresetId
        Surface(
            selected = selected,
            onClick = { onSelect(preset) },
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            color = if (selected) colors.secondaryContainer else colors.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
        ) {
            Row(Modifier.heightIn(min = FieldHeight), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(horizontal = 14.dp))
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${preset.model} · ${preset.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { editing = preset }, enabled = enabled, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.settings_translation_edit))
                }
            }
        }
    }
    if (showError) {
        Text(
            stringResource(when {
                presets.isEmpty() -> R.string.settings_translation_add_first
                draft.selectedTranslation == null -> R.string.settings_translation_choose
                else -> R.string.settings_translation_incomplete
            }),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
        )
    }
    FilledTonalButton(
        onClick = { editing = TranslationPreset(name = "", url = "", model = "", key = "") },
        enabled = enabled,
    ) {
        Icon(painterResource(R.drawable.ic_add), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.settings_translation_add))
    }

    editing?.let { preset ->
        val isNew = presets.none { it.id == preset.id }
        TranslationServiceDialog(
            initial = preset,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = if (isNew) null else ({ onDelete(preset); editing = null }),
        )
    }
}

@Composable
private fun TranslationServiceDialog(
    initial: TranslationPreset,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TranslationPreset) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var preset by remember(initial) { mutableStateOf(initial) }
    var attempted by remember(initial) { mutableStateOf(false) }
    val errors = preset.validationErrors()
    // Missing fields wait for Done; a malformed URL is pointed out as soon as one is typed.
    val shown = if (attempted) errors else errors.filter { it == SettingsError.TranslationUrl && preset.url.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.settings_translation_new else R.string.settings_translation_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingField(
                    stringResource(R.string.settings_translation_name), preset.name, true,
                    SettingsError.PresetName.shownIn(shown), keyboardType = KeyboardType.Text,
                ) { preset = preset.copy(name = it) }
                SettingField(
                    stringResource(R.string.settings_base_url), preset.url, true,
                    SettingsError.TranslationUrl.shownIn(shown), placeholder = ServiceSettings().translationUrl,
                ) { preset = preset.copy(url = it.trimEnd('/')) }
                SettingField(
                    stringResource(R.string.settings_model), preset.model, true,
                    SettingsError.TranslationModel.shownIn(shown), placeholder = ServiceSettings().translationModel,
                ) { preset = preset.copy(model = it) }
                SecretField(stringResource(R.string.settings_key), preset.key, true, SettingsError.TranslationKey.shownIn(shown)) {
                    preset = preset.copy(key = it)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (errors.isEmpty()) onSave(preset) else attempted = true }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Takes effect at once, outside the service form's save/undo. */
@Composable
private fun DebugModeCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_bug), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_debug_mode), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(R.string.settings_debug_mode_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun PrivacyNote() {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 4.dp)) {
        Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(18.dp), tint = colors.outline)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = colors.outline)
    }
}

/** Slides up only while there is something to save. */
@Composable
private fun SaveBar(visible: Boolean, saving: Boolean, onRevert: () -> Unit, onSave: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_unsaved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRevert, enabled = !saving) { Text(stringResource(R.string.settings_revert)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !saving) {
                    Text(stringResource(if (saving) R.string.settings_saving else R.string.settings_save))
                }
            }
        }
    }
}

private fun SettingsError.shownIn(shown: List<SettingsError>) = takeIf { it in shown }

@Composable
private fun SettingsError.message(): String = stringResource(when (this) {
    SettingsError.AsrKey -> R.string.settings_missing_asr_key
    SettingsError.AsrAppId -> R.string.settings_missing_asr_app_id
    SettingsError.TranslationUrl -> R.string.settings_invalid_translation_url
    SettingsError.TranslationModel -> R.string.settings_missing_model
    SettingsError.TranslationKey -> R.string.settings_missing_key
    SettingsError.PresetName -> R.string.settings_translation_missing_name
})

/** Taller than the 56dp default, which looks cramped on the roomy cards. */
private val FieldHeight = 64.dp
// 12dp above and below instead of 8dp, so the extra height is split evenly around label and text.
private val FieldPadding = TextFieldDefaults.contentPaddingWithLabel(top = 12.dp, bottom = 12.dp)

/** Pasted keys often drag a newline or tab along; such characters are never valid in any field. */
private val NoControlChars = InputTransformation {
    val text = asCharSequence()
    if (text.any { it.isISOControl() }) replace(0, length, text.filterNot { it.isISOControl() })
}

/** Filled and rounded like the service picker; the container, not an underline, carries the state. */
@Composable
private fun fieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHighest,
        disabledContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.6f),
        errorContainerColor = colors.errorContainer,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}

/**
 * A [TextFieldState] (whose text fields accept a content padding) seeded from the draft. Edits only
 * flow out, trimmed; writing the draft back in would race fast typing. Undo rebuilds the field instead.
 * Not saveable on purpose: keys must not enter savedInstanceState.
 */
@Composable
private fun rememberFieldState(value: String, onChange: (String) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value) }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString().trim() }.collect { latest(it) }
    }
    return state
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    enabled: Boolean,
    error: SettingsError?,
    keyboardType: KeyboardType = KeyboardType.Uri,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    TextField(
        state = rememberFieldState(value, onChange),
        inputTransformation = NoControlChars,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it.message()) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType),
        shape = MaterialTheme.shapes.large,
        colors = fieldColors(),
        contentPadding = FieldPadding,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, enabled: Boolean, error: SettingsError?, onChange: (String) -> Unit) {
    // Not saveable on purpose: a revealed key should not come back revealed after process death.
    var revealed by remember { mutableStateOf(false) }
    SecureTextField(
        state = rememberFieldState(value, onChange),
        inputTransformation = NoControlChars,
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it.message()) } },
        shape = MaterialTheme.shapes.large,
        colors = fieldColors(),
        contentPadding = FieldPadding,
        textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    stringResource(if (revealed) R.string.settings_hide_key else R.string.settings_show_key),
                )
            }
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
