package io.github.evative7.parley.ui

import android.icu.text.ListFormatter
import kotlin.math.roundToLong
import androidx.compose.material3.Slider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.R
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.data.ConfigFile
import io.github.evative7.parley.data.ConfigFileException
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.SettingsError
import io.github.evative7.parley.data.TranslationPreset
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import io.github.evative7.parley.mock.MockTtsFault
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.LanguageSupport
import io.github.evative7.parley.model.SupportWording
import io.github.evative7.parley.model.withSupportLine
import io.github.evative7.parley.model.render
import io.github.evative7.parley.model.summarize
import io.github.evative7.parley.state.SettingsViewModel
import io.github.evative7.parley.tts.MiniMaxRegion
import io.github.evative7.parley.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

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
    // Saved with the rest of the page, so Discard undoes it too.
    var debugDraft by remember(saved, debugMode) { mutableStateOf(debugMode) }
    val dirty = draft != baseline || debugDraft != debugMode
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configMessages = ConfigMessages(
        exported = stringResource(R.string.settings_config_exported),
        imported = stringResource(R.string.settings_config_imported),
        invalid = stringResource(R.string.settings_config_invalid),
        tooNew = stringResource(R.string.settings_config_too_new),
        failed = stringResource(R.string.settings_config_failed),
    )
    // What is saved goes out; unsaved edits are not a configuration yet.
    val exportConfig = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = ConfigFile.encode(baseline, BuildConfig.VERSION_NAME)
        scope.launch {
            val written = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            snackbar.showSnackbar(if (written) configMessages.exported else configMessages.failed)
        }
    }
    // An import fills the page like typing would; nothing is stored until Save.
    val importConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readUpTo(MAX_CONFIG_BYTES) }.decodeToString()
                }
                ConfigFile.decode(text)
            }
            result.onSuccess {
                draft = it
                attempted = false
                revision++
            }
            snackbar.showSnackbar(
                when ((result.exceptionOrNull() as? ConfigFileException)?.reason) {
                    null -> if (result.isSuccess) configMessages.imported else configMessages.failed
                    ConfigFileException.Reason.NotAConfig -> configMessages.invalid
                    ConfigFileException.Reason.TooNew -> configMessages.tooNew
                },
            )
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val contentScroll = rememberScrollState()
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
                onRevert = { draft = baseline; debugDraft = debugMode; attempted = false; revision++ },
                onSave = {
                    if (errors.isEmpty()) vm.save(draft, debugDraft, onBack) else attempted = true
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(contentScroll)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            key(saved, revision) {
                val asrProviders = draft.asrProviders
                val asrProviderNames = asrProviders.map { it.label() }
                ServiceCard(
                    icon = R.drawable.ic_mic,
                    title = stringResource(R.string.settings_asr),
                    provider = asrProviderNames.joinToString(" · "),
                ) {
                    AsrServiceField(
                        draft.asrServices, !saving, SettingsError.AsrNone.shownIn(shown),
                    ) { draft = draft.copy(asrServices = it) }
                    if (draft.asrServices.size > 1) {
                        AsrGraceField(draft.asrGraceMs, !saving) { draft = draft.copy(asrGraceMs = it) }
                    }
                    if (AsrProvider.Mock in asrProviders) {
                        ProviderFields(AsrProvider.Mock, draft, !saving, emptyList()) { draft = it }
                    }
                }

                TtsCard(draft, !saving) { draft = it }

                val providers = draft.usedProviders.filter { it != AsrProvider.Mock }
                val providerNames = providers.map { it.label() }
                if (providers.isNotEmpty()) {
                    ServiceCard(
                        icon = R.drawable.ic_lock,
                        title = stringResource(R.string.settings_credentials),
                        provider = providerNames.joinToString(" · "),
                    ) {
                        for (provider in providers) {
                            Text(
                                provider.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                            val providerShown = if (attempted) draft.providerErrors(provider) else emptyList()
                            ProviderFields(provider, draft, !saving, providerShown) { draft = it }
                            provider.hintResource()?.let {
                                Text(
                                    stringResource(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

            DebugModeCard(debugDraft, onChange = { debugDraft = it })

            ConfigCard(
                enabled = !saving,
                onExport = { exportConfig.launch("parley-config.json") },
                onImport = { importConfig.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            )

            AboutCard()
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

/** How long to wait, after the first result, for the other services' results, in half seconds. */
@Composable
private fun AsrGraceField(valueMs: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val steps = (ServiceSettings.MAX_ASR_GRACE_MS / GRACE_STEP_MS).toInt()
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_asr_grace),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.settings_asr_grace_value, valueMs / 1000f),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
        Slider(
            value = (valueMs / GRACE_STEP_MS).toFloat(),
            onValueChange = { onChange(it.roundToLong() * GRACE_STEP_MS) },
            valueRange = 0f..steps.toFloat(),
            steps = steps - 1,
            enabled = enabled,
        )
        Text(
            stringResource(R.string.settings_asr_grace_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

private const val GRACE_STEP_MS = 500L

@Composable
private fun TtsCard(draft: ServiceSettings, enabled: Boolean, onChange: (ServiceSettings) -> Unit) {
    val service = draft.ttsService
    ServiceCard(
        icon = R.drawable.ic_volume_up,
        title = stringResource(R.string.settings_tts),
        provider = service?.label() ?: stringResource(R.string.settings_tts_off),
        hint = null,
    ) {
        TtsServiceField(
            selected = service,
            region = draft.dashScopeRegion,
            enabled = enabled,
        ) { onChange(draft.copy(ttsService = it)) }
        if (service != null && service != TtsService.Mock) {
            SettingField(
                label = stringResource(R.string.settings_tts_voice),
                value = draft.ttsVoices[service.id].orEmpty(),
                enabled = enabled,
                error = null,
                keyboardType = KeyboardType.Text,
                placeholder = stringResource(R.string.settings_tts_voice_default),
            ) { voice ->
                val voices = if (voice.isBlank()) draft.ttsVoices - service.id else draft.ttsVoices + (service.id to voice)
                onChange(draft.copy(ttsVoices = voices))
            }
        }
        if (service == TtsService.Mock) {
            MockSpeedField(draft.mockTtsSpeed, enabled) { onChange(draft.copy(mockTtsSpeed = it)) }
            ChoiceField(
                label = stringResource(R.string.settings_mock_fault),
                options = MockTtsFault.entries,
                selected = draft.mockTtsFault,
                optionLabel = { stringResource(it.label()) },
                enabled = enabled,
                warn = draft.mockTtsFault != MockTtsFault.None,
            ) { onChange(draft.copy(mockTtsFault = it)) }
        }
        SettingsSwitch(
            title = stringResource(R.string.settings_tts_autoplay),
            checked = draft.ttsAutoPlay,
            enabled = enabled,
        ) { onChange(draft.copy(ttsAutoPlay = it)) }
        SettingsSwitch(
            title = stringResource(R.string.settings_tts_autoplay_face_to_face),
            checked = draft.ttsAutoPlayFaceToFace,
            enabled = enabled,
        ) { onChange(draft.copy(ttsAutoPlayFaceToFace = it)) }
    }
}

@Composable
private fun SettingsSwitch(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun TtsService.label(): String = stringResource(labelResource())

@StringRes
internal fun TtsService.labelResource(): Int = when (this) {
    TtsService.QwenAudioTtsFlash -> R.string.settings_tts_qwen_audio_flash
    TtsService.CosyVoiceV3Flash -> R.string.settings_tts_cosy_v3_flash
    TtsService.DoubaoSeedTts20 -> R.string.settings_tts_doubao
    TtsService.MiniMaxSpeech28Turbo -> R.string.settings_tts_minimax_turbo
    TtsService.MiniMaxSpeech28Hd -> R.string.settings_tts_minimax_hd
    TtsService.Mock -> R.string.settings_tts_mock
}

@StringRes
private fun TtsService.priceDescription(): Int = when (this) {
    TtsService.Mock -> R.string.settings_tts_price_free
    TtsService.QwenAudioTtsFlash -> R.string.settings_tts_price_qwen_audio_flash
    TtsService.CosyVoiceV3Flash -> R.string.settings_tts_price_cosy_v3_flash
    TtsService.DoubaoSeedTts20 -> R.string.settings_tts_price_doubao
    TtsService.MiniMaxSpeech28Turbo -> R.string.settings_tts_price_minimax_turbo
    TtsService.MiniMaxSpeech28Hd -> R.string.settings_tts_price_minimax_hd
}

/** The shared credential fields of one [provider], marking the errors in [shown]. */
@Composable
private fun ProviderFields(
    provider: AsrProvider,
    draft: ServiceSettings,
    enabled: Boolean,
    shown: List<SettingsError>,
    onChange: (ServiceSettings) -> Unit,
) {
    when (provider) {
        AsrProvider.DashScope -> {
            DashScopeRegionField(draft.dashScopeRegion, enabled) { onChange(draft.copy(dashScopeRegion = it)) }
            SecretField(
                stringResource(R.string.settings_key), draft.dashScopeKey, enabled,
                SettingsError.AsrKey.shownIn(shown),
            ) { onChange(draft.copy(dashScopeKey = it)) }
        }
        AsrProvider.OpenAi -> SecretField(
            stringResource(R.string.settings_key), draft.openAiKey, enabled,
            SettingsError.AsrKey.shownIn(shown),
        ) { onChange(draft.copy(openAiKey = it)) }
        AsrProvider.Volcengine -> {
            SettingField(
                stringResource(R.string.settings_app_id), draft.volcengineAppId, enabled,
                SettingsError.AsrAppId.shownIn(shown), keyboardType = KeyboardType.Text,
            ) { onChange(draft.copy(volcengineAppId = it)) }
            SecretField(
                stringResource(R.string.settings_access_token), draft.volcengineAccessToken, enabled,
                SettingsError.AsrKey.shownIn(shown),
            ) { onChange(draft.copy(volcengineAccessToken = it)) }
        }
        AsrProvider.MiniMax -> {
            MiniMaxRegionField(draft.miniMaxRegion, enabled) { onChange(draft.copy(miniMaxRegion = it)) }
            SecretField(
                stringResource(R.string.settings_key), draft.miniMaxKey, enabled,
                SettingsError.AsrKey.shownIn(shown),
            ) { onChange(draft.copy(miniMaxKey = it)) }
        }
        AsrProvider.Mock -> {
            MockSpeedField(draft.mockAsrSpeed, enabled) { onChange(draft.copy(mockAsrSpeed = it)) }
            ChoiceField(
                label = stringResource(R.string.settings_mock_fault),
                options = MockAsrFault.entries,
                selected = draft.mockAsrFault,
                optionLabel = { stringResource(it.label()) },
                enabled = enabled,
                warn = draft.mockAsrFault != MockAsrFault.None,
            ) { onChange(draft.copy(mockAsrFault = it)) }
        }
    }
}

/**
 * Looks like the text fields around it; picking happens in a dialog, where any number of services can be
 * checked. They are kept in the order they were checked.
 */
@Composable
private fun AsrServiceField(
    selected: List<AsrService>,
    enabled: Boolean,
    error: SettingsError?,
    onChange: (List<AsrService>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { open = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (error != null) colors.errorContainer else colors.surfaceContainerHighest,
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
                val names = selected.map { it.label() }
                Text(names.joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
        }
    }
    if (error != null) {
        Text(
            error.message(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    selected.forEach { service ->
        // With several services, each description is headed by its name.
        SelectedDescription(title = service.label().takeIf { selected.size > 1 }, text = service.fullDescription())
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
                    Text(
                        stringResource(R.string.settings_asr_services_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    val listScroll = rememberScrollState()
                    Column(Modifier.heightIn(max = 480.dp).scrollbar(listScroll).verticalScroll(listScroll)) {
                        AsrProvider.available.forEach { provider ->
                            Text(
                                provider.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                            )
                            AsrService.available.filter { it.provider == provider }.forEach { service ->
                                // A pill clips the corners of multi-line descriptions; a rounded rect does not.
                                val isSelected = service in selected
                                Surface(
                                    onClick = { onChange(if (isSelected) selected - service else selected + service) },
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
                                                service.fullDescription(),
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
                    TextButton(
                        onClick = { open = false },
                        modifier = Modifier.align(Alignment.End).padding(end = 12.dp, top = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsServiceField(
    selected: TtsService?,
    region: DashScopeRegion,
    enabled: Boolean,
    onChange: (TtsService?) -> Unit,
) {
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
                    stringResource(R.string.settings_tts_service),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    selected?.label() ?: stringResource(R.string.settings_tts_off),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
        }
    }
    selected?.let { SelectedDescription(title = null, text = it.fullDescription(region)) }
    if (open) {
        BasicAlertDialog(onDismissRequest = { open = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(vertical = 20.dp)) {
                    Text(
                        stringResource(R.string.settings_tts_service),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    val listScroll = rememberScrollState()
                    Column(Modifier.heightIn(max = 480.dp).scrollbar(listScroll).verticalScroll(listScroll)) {
                        ServiceChoiceRow(
                            title = stringResource(R.string.settings_tts_off),
                            description = null,
                            selected = selected == null,
                            onClick = { onChange(null); open = false },
                        )
                        // MiniMax only offers speech, so it isn't among the ASR providers.
                        AsrProvider.entries.filter { provider ->
                            TtsService.available.any { it.provider == provider }
                        }.forEach { provider ->
                            Text(
                                provider.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                            )
                            TtsService.available.filter { it.provider == provider }.forEach { service ->
                                ServiceChoiceRow(
                                    title = service.label(),
                                    description = service.fullDescription(region),
                                    selected = selected == service,
                                    onClick = { onChange(service); open = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The description of a chosen service, shown under its selector. */
@Composable
private fun SelectedDescription(title: String?, text: String) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServiceChoiceRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(painterResource(R.drawable.ic_check), null)
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
private fun MiniMaxRegionField(selected: MiniMaxRegion, enabled: Boolean, onPick: (MiniMaxRegion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.settings_minimax_region),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectedChoice(
            options = MiniMaxRegion.entries,
            selected = selected,
            optionLabel = {
                stringResource(
                    if (it == MiniMaxRegion.China) R.string.settings_region_china
                    else R.string.settings_region_international,
                )
            },
            enabled = enabled,
            onPick = onPick,
        )
    }
}

@Composable
private fun AsrProvider.label(): String = stringResource(when (this) {
    AsrProvider.DashScope -> R.string.settings_provider_dashscope
    AsrProvider.OpenAi -> R.string.settings_provider_openai
    AsrProvider.Volcengine -> R.string.settings_provider_volcengine
    AsrProvider.MiniMax -> R.string.settings_provider_minimax
    AsrProvider.Mock -> R.string.settings_provider_mock
})

@StringRes
private fun AsrProvider.hintResource(): Int? = when (this) {
    AsrProvider.DashScope -> R.string.settings_dashscope_hint
    AsrProvider.OpenAi -> null
    AsrProvider.Volcengine -> R.string.settings_volcengine_hint
    AsrProvider.MiniMax, AsrProvider.Mock -> null
}

@Composable
internal fun AsrService.label(): String = stringResource(labelResource())

@StringRes
internal fun AsrService.labelResource(): Int = when (this) {
    AsrService.FunAsrRealtimeStream -> R.string.settings_asr_fun_stream
    AsrService.FunAsrRealtimeNonStream -> R.string.settings_asr_fun_non_stream
    AsrService.GptRealtimeWhisper -> R.string.settings_asr_gpt_realtime
    AsrService.Gpt4oTranscribeNonStream -> R.string.settings_asr_gpt_4o
    AsrService.Gpt4oMiniTranscribeNonStream -> R.string.settings_asr_gpt_4o_mini
    AsrService.DoubaoSeedAsr20Stream -> R.string.settings_asr_doubao_stream
    AsrService.DoubaoSeedAsr20NonStream -> R.string.settings_asr_doubao_non_stream
    AsrService.DoubaoSeedAsr20NonStreamTurbo -> R.string.settings_asr_doubao_turbo
    AsrService.MockStream -> R.string.settings_asr_mock_stream
    AsrService.MockNonStream -> R.string.settings_asr_mock_non_stream
}

@StringRes
private fun AsrService.descriptionResource(): Int = when (this) {
    AsrService.FunAsrRealtimeStream -> R.string.settings_asr_fun_stream_desc
    AsrService.FunAsrRealtimeNonStream -> R.string.settings_asr_fun_non_stream_desc
    AsrService.GptRealtimeWhisper -> R.string.settings_asr_gpt_realtime_desc
    AsrService.Gpt4oTranscribeNonStream -> R.string.settings_asr_gpt_4o_desc
    AsrService.Gpt4oMiniTranscribeNonStream -> R.string.settings_asr_gpt_4o_mini_desc
    AsrService.DoubaoSeedAsr20Stream -> R.string.settings_asr_doubao_stream_desc
    AsrService.DoubaoSeedAsr20NonStream -> R.string.settings_asr_doubao_non_stream_desc
    AsrService.DoubaoSeedAsr20NonStreamTurbo -> R.string.settings_asr_doubao_turbo_desc
    AsrService.MockStream -> R.string.settings_asr_mock_stream_desc
    AsrService.MockNonStream -> R.string.settings_asr_mock_non_stream_desc
}

/** The written description with the language line slotted in before its last sentence, the variant's. */
@Composable
private fun AsrService.fullDescription(): String =
    withSupportLine(stringResource(descriptionResource()), languageSupport.supportDescription())

@StringRes
private fun TtsService.descriptionResource(): Int = when (this) {
    TtsService.QwenAudioTtsFlash -> R.string.settings_tts_qwen_audio_flash_desc
    TtsService.CosyVoiceV3Flash -> R.string.settings_tts_cosy_v3_flash_desc
    TtsService.DoubaoSeedTts20 -> R.string.settings_tts_doubao_desc
    TtsService.MiniMaxSpeech28Turbo -> R.string.settings_tts_minimax_turbo_desc
    TtsService.MiniMaxSpeech28Hd -> R.string.settings_tts_minimax_hd_desc
    TtsService.Mock -> R.string.settings_tts_mock_desc
}

/** What the service is, which languages it reads in this region, and what it costs. */
@Composable
private fun TtsService.fullDescription(region: DashScopeRegion): String = listOf(
    stringResource(descriptionResource()),
    support(region).supportDescription(),
    stringResource(priceDescription()),
).joinToString("\n")

@Composable
private fun LanguageSupport.supportDescription(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val resources = LocalContext.current.resources
    val formatter = remember(locale) { ListFormatter.getInstance(locale) }
    val wording = SupportWording(
        supports = { resources.getString(R.string.settings_supports, it) },
        unsupported = { resources.getString(R.string.settings_does_not_support, it) },
        extras = { resources.getString(R.string.settings_also_supports, it) },
        extrasMore = { names, more, total -> resources.getString(R.string.settings_also_supports_more, names, more) },
        list = { formatter.format(it) },
        // The separator the language puts between the first items of a list, e.g. ", " or "、".
        series = { names ->
            val separator = formatter.format("\u0001", "\u0002", "\u0003").substringAfter('\u0001').substringBefore('\u0002')
            names.joinToString(separator)
        },
        sentenceGap = if (locale.language in setOf("zh", "ja")) "" else " ",
    )
    return summarize().render(
        languageName = { Locale.forLanguageTag(it).getDisplayName(locale).replaceFirstChar { c -> c.titlecase(locale) } },
        projectName = { it.displayName(locale) },
        wording = wording,
    )
}

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
    // One connected group, like the rows of the About card.
    Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
        presets.forEachIndexed { index, preset ->
            val selected = preset.id == draft.translationPresetId
            Surface(
                selected = selected,
                onClick = { onSelect(preset) },
                enabled = enabled,
                shape = GroupShapes.at(index, presets.size),
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
                            if (preset.mock) {
                                stringResource(R.string.settings_translation_mock_detail, stringResource(preset.mockSpeed.label()))
                            } else {
                                "${preset.model} · ${preset.host}"
                            },
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
    // The name field only reports edits outward; this rebuilds it when the type fills in a name.
    var nameRevision by remember(initial) { mutableIntStateOf(0) }
    val errors = preset.validationErrors()
    // Missing fields wait for Done; a malformed URL is pointed out as soon as one is typed.
    val shown = if (attempted) errors else errors.filter { it == SettingsError.TranslationUrl && preset.url.isNotEmpty() }
    val contentScroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.settings_translation_new else R.string.settings_translation_edit)) },
        text = {
            Column(
                Modifier.scrollbar(contentScroll).verticalScroll(contentScroll).padding(end = ScrollbarGutter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Simulated services only exist in debug builds; one saved earlier can still be edited.
                if (BuildConfig.DEBUG || preset.mock) {
                    val mockName = stringResource(R.string.settings_translation_mock_name)
                    ConnectedChoice(
                        options = listOf(false, true),
                        selected = preset.mock,
                        optionLabel = {
                            stringResource(if (it) R.string.settings_translation_type_mock else R.string.settings_translation_type_real)
                        },
                        enabled = true,
                    ) { mock ->
                        // A new simulated service needs no thought about its name.
                        val name = if (mock && preset.name.isBlank()) mockName else preset.name
                        preset = preset.copy(mock = mock, name = name)
                        nameRevision++
                    }
                }
                key(nameRevision) {
                    SettingField(
                        stringResource(R.string.settings_translation_name), preset.name, true,
                        SettingsError.PresetName.shownIn(shown), keyboardType = KeyboardType.Text,
                    ) { preset = preset.copy(name = it) }
                }
                if (preset.mock) {
                    MockSpeedField(preset.mockSpeed, true) { preset = preset.copy(mockSpeed = it) }
                    ChoiceField(
                        label = stringResource(R.string.settings_mock_fault),
                        options = MockTranslationFault.entries,
                        selected = preset.mockFault,
                        optionLabel = { stringResource(it.label()) },
                        enabled = true,
                        warn = preset.mockFault != MockTranslationFault.None,
                    ) { preset = preset.copy(mockFault = it) }
                    return@Column
                }
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

@StringRes
private fun MockSpeed.label(): Int = when (this) {
    MockSpeed.Fast -> R.string.settings_mock_speed_fast
    MockSpeed.Normal -> R.string.settings_mock_speed_normal
    MockSpeed.Slow -> R.string.settings_mock_speed_slow
}

@StringRes
private fun MockAsrFault.label(): Int = when (this) {
    MockAsrFault.None -> R.string.mock_fault_none
    MockAsrFault.NoSpeech -> R.string.mock_fault_no_speech
    MockAsrFault.NetworkWhileRecording -> R.string.mock_fault_network_recording
    MockAsrFault.Authentication -> R.string.mock_fault_authentication
    MockAsrFault.Timeout -> R.string.mock_fault_timeout
}

@StringRes
private fun MockTranslationFault.label(): Int = when (this) {
    MockTranslationFault.None -> R.string.mock_fault_none
    MockTranslationFault.NetworkMidway -> R.string.mock_fault_network_midway
    MockTranslationFault.Authentication -> R.string.mock_fault_authentication
    MockTranslationFault.RateLimit -> R.string.mock_fault_rate_limit
    MockTranslationFault.Timeout -> R.string.mock_fault_timeout
}

@StringRes
private fun MockTtsFault.label(): Int = when (this) {
    MockTtsFault.None -> R.string.mock_fault_none
    MockTtsFault.NetworkMidway -> R.string.mock_fault_network_midway
    MockTtsFault.Authentication -> R.string.mock_fault_authentication
    MockTtsFault.Timeout -> R.string.mock_fault_timeout
}

@Composable
private fun MockSpeedField(selected: MockSpeed, enabled: Boolean, onPick: (MockSpeed) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.settings_mock_speed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectedChoice(MockSpeed.entries, selected, { stringResource(it.label()) }, enabled, onPick)
    }
}

/** One of a few options as filled connected buttons, matching the filled fields around them. */
@Composable
private fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    enabled: Boolean,
    onPick: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onPick(option) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
            ) {
                Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Looks like a text field; picking happens in a dialog. [warn] tints it, for options that break things on purpose. */
@Composable
private fun <T> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    enabled: Boolean,
    warn: Boolean = false,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { open = true },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (warn) colors.errorContainer else colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = FieldHeight).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(optionLabel(selected), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.onSurfaceVariant)
        }
    }
    if (open) {
        BasicAlertDialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 20.dp)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.size(12.dp))
                    val listScroll = rememberScrollState()
                    Column(Modifier.heightIn(max = 480.dp).scrollbar(listScroll).verticalScroll(listScroll)) {
                        options.forEach { option ->
                            val isSelected = option == selected
                            Surface(
                                onClick = { onPick(option); open = false },
                                shape = MaterialTheme.shapes.large,
                                color = if (isSelected) colors.secondaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    if (isSelected) Icon(painterResource(R.drawable.ic_check), null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_CONFIG_BYTES = 1 shl 20

/** Android-compatible equivalent of InputStream.readNBytes(limit), which requires API 33. */
private fun InputStream.readUpTo(limit: Int): ByteArray {
    val result = ByteArray(limit)
    var size = 0
    while (size < limit) {
        val read = read(result, size, limit - size)
        if (read < 0) break
        if (read == 0) {
            val byte = read()
            if (byte < 0) break
            result[size++] = byte.toByte()
        } else {
            size += read
        }
    }
    return result.copyOf(size)
}

private class ConfigMessages(
    val exported: String,
    val imported: String,
    val invalid: String,
    val tooNew: String,
    val failed: String,
)

/** Moves the service settings in and out as a file. */
@Composable
private fun ConfigCard(enabled: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_import_export), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_config), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            }
            Text(stringResource(R.string.settings_config_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onExport, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_config_export))
                }
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_config_import))
                }
            }
        }
    }
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

private const val RepositoryUrl = "https://github.com/EvATive7/Parley"
private const val AuthorName = "EvATive7"
private const val AuthorUrl = "https://github.com/EvATive7"

/** Libraries shipped in the app, with their licenses. */
private val OpenSourceLibraries = listOf(
    "Jetpack Compose · Material 3" to "Apache License 2.0",
    "AndroidX Core · Activity · Lifecycle · DataStore" to "Apache License 2.0",
    "AndroidX Graphics Shapes" to "Apache License 2.0",
    "Kotlin · kotlinx.coroutines · kotlinx.serialization" to "Apache License 2.0",
    "Dagger Hilt" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "ML Kit Language Identification" to "ML Kit Terms of Service",
)

/** Privacy, where the code lives, who wrote it, and what it's built on. */
@Composable
private fun AboutCard() {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_info), null, Modifier.size(22.dp), tint = colors.onSecondaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.settings_about_subtitle), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                AboutRow(
                    icon = R.drawable.ic_lock,
                    title = stringResource(R.string.settings_privacy_title),
                    detail = stringResource(R.string.settings_privacy),
                    shape = GroupShapes.first,
                    detailMaxLines = Int.MAX_VALUE,
                )
                AboutRow(
                    icon = R.drawable.ic_code,
                    title = stringResource(R.string.settings_source_code),
                    detail = RepositoryUrl.removePrefix("https://"),
                    shape = GroupShapes.middle,
                    onClick = { uriHandler.openUri(RepositoryUrl) },
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
                AboutRow(
                    icon = R.drawable.ic_person,
                    title = stringResource(R.string.settings_author),
                    detail = AuthorName,
                    shape = GroupShapes.middle,
                    onClick = { uriHandler.openUri(AuthorUrl) },
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
                val turn by animateFloatAsState(
                    if (licensesOpen) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron",
                )
                AboutRow(
                    icon = R.drawable.ic_description,
                    title = stringResource(R.string.settings_licenses),
                    detail = pluralStringResource(R.plurals.settings_licenses_count, OpenSourceLibraries.size, OpenSourceLibraries.size),
                    shape = if (licensesOpen) GroupShapes.middle else GroupShapes.last,
                    onClick = { licensesOpen = !licensesOpen },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right), null,
                        Modifier.size(20.dp).rotate(turn), tint = colors.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(
                    visible = licensesOpen,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(GroupShapes.last)
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_licenses_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        for ((name, license) in OpenSourceLibraries) {
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                                Text(license, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GroupGap = 3.dp

/** Rows stacked into one group: round at the group's outer ends, tight where two rows meet. */
private object GroupShapes {
    val single = RoundedCornerShape(20.dp)
    val first = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val middle = RoundedCornerShape(6.dp)
    val last = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

    fun at(index: Int, count: Int) = when {
        count == 1 -> single
        index == 0 -> first
        index == count - 1 -> last
        else -> middle
    }
}

@Composable
private fun AboutRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    detailMaxLines: Int = 1,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val content = @Composable {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = colors.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = detailMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Surface(shape = shape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { content() }
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
    SettingsError.AsrNone -> R.string.settings_missing_asr_service
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
