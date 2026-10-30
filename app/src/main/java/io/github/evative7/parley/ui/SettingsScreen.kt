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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.evative7.parley.R
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.SettingsError
import io.github.evative7.parley.state.SettingsViewModel

/** Service settings: what is in effect at the top, then one card per service. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val config by vm.settings.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveFailed by vm.saveFailed.collectAsStateWithLifecycle()

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
    val dirty = draft != baseline
    val errors = draft.validationErrors()
    val shown = if (attempted) errors else errors.filter { it == SettingsError.TranslationUrl }
    val leave = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = !saving) { leave() }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).imePadding(),
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
                onRevert = { draft = baseline; attempted = false },
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
            StatusCard(saved, saveFailed)

            ServiceCard(
                icon = R.drawable.ic_mic,
                title = stringResource(R.string.settings_asr),
                provider = draft.asrService.provider.label(),
                hint = stringResource(draft.asrService.provider.hintResource()),
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
                        ) { draft = draft.copy(dashScopeKey = it.trim()) }
                    }
                    AsrProvider.OpenAi -> SecretField(
                        stringResource(R.string.settings_key), draft.openAiAsrKey, !saving,
                        SettingsError.AsrKey.shownIn(shown),
                    ) { draft = draft.copy(openAiAsrKey = it.trim()) }
                    AsrProvider.Volcengine -> {
                        SettingField(
                            stringResource(R.string.settings_app_id), draft.volcAppId, !saving,
                            SettingsError.AsrAppId.shownIn(shown), keyboardType = KeyboardType.Text,
                        ) { draft = draft.copy(volcAppId = it.trim()) }
                        SecretField(
                            stringResource(R.string.settings_access_token), draft.volcAccessToken, !saving,
                            SettingsError.AsrKey.shownIn(shown),
                        ) { draft = draft.copy(volcAccessToken = it.trim()) }
                    }
                }
            }

            ServiceCard(
                icon = R.drawable.ic_translate,
                title = stringResource(R.string.settings_translation),
                provider = stringResource(R.string.settings_translation_provider),
            ) {
                SettingField(stringResource(R.string.settings_base_url), draft.translationUrl, !saving, SettingsError.TranslationUrl.shownIn(shown)) {
                    draft = draft.copy(translationUrl = it.trim())
                }
                SettingField(stringResource(R.string.settings_model), draft.translationModel, !saving, SettingsError.TranslationModel.shownIn(shown)) {
                    draft = draft.copy(translationModel = it.trim())
                }
                SecretField(stringResource(R.string.settings_key), draft.translationKey, !saving, SettingsError.TranslationKey.shownIn(shown)) {
                    draft = draft.copy(translationKey = it.trim())
                }
            }

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

/** What is saved and in effect right now, not the draft being edited. */
@Composable
private fun StatusCard(saved: ServiceSettings, saveFailed: Boolean) {
    val colors = MaterialTheme.colorScheme
    data class Look(
        val container: Color, val content: Color, val badge: Color, val onBadge: Color,
        @DrawableRes val icon: Int, val title: String, val body: String,
    )
    val look = when {
        saved.storageError || saveFailed -> Look(
            colors.errorContainer, colors.onErrorContainer, colors.error, colors.onError, R.drawable.ic_error,
            stringResource(R.string.settings_status_error), stringResource(R.string.settings_storage_error),
        )
        saved.isReady -> Look(
            colors.primaryContainer, colors.onPrimaryContainer, colors.primary, colors.onPrimary, R.drawable.ic_check,
            stringResource(R.string.settings_status_ready),
            stringResource(R.string.settings_status_ready_body, saved.asrService.label(), saved.translationModel),
        )
        else -> Look(
            colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary, colors.onSecondary, R.drawable.ic_settings,
            stringResource(R.string.settings_status_setup), stringResource(R.string.settings_status_setup_body),
        )
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = look.container, contentColor = look.content) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // The same cookie the talk button rests in.
            Box(
                Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(look.badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(look.icon), null, Modifier.size(28.dp), tint = look.onBadge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(look.title, style = MaterialTheme.typography.titleLarge)
                Text(look.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AsrServiceField(selected: AsrService, enabled: Boolean, onPick: (AsrService) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(stringResource(R.string.settings_asr_service), style = MaterialTheme.typography.labelMedium)
            Text(selected.label(), style = MaterialTheme.typography.bodyLarge)
        }
        Icon(painterResource(R.drawable.ic_arrow_drop_down), null)
    }
    Text(
        selected.description(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                                TextButton(
                                    onClick = { onPick(service); open = false },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                ) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                        Text(service.label(), color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            service.description(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (service == selected) Icon(painterResource(R.drawable.ic_check), null)
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DashScopeRegion.entries.forEachIndexed { index, region ->
                SegmentedButton(
                    selected = selected == region,
                    onClick = { onPick(region) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, DashScopeRegion.entries.size),
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
private fun AsrProvider.hintResource(): Int = when (this) {
    AsrProvider.DashScope -> R.string.settings_dashscope_hint
    AsrProvider.OpenAi -> R.string.settings_openai_asr_hint
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
})

@Composable
private fun SettingField(
    label: String,
    value: String,
    enabled: Boolean,
    error: SettingsError?,
    keyboardType: KeyboardType = KeyboardType.Uri,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it.message()) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, enabled: Boolean, error: SettingsError?, onChange: (String) -> Unit) {
    // Not saveable on purpose: a revealed key should not come back revealed after process death.
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it.message()) } },
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
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
