package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.evative7.parley.asr.AsrAlternative
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.CardStage
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.CardTranslation
import io.github.evative7.parley.state.ConversationStore
import io.github.evative7.parley.state.ErrorKind
import io.github.evative7.parley.state.LangPair
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.conversationDataStore by preferencesDataStore("conversation")
private val CardsKey = stringPreferencesKey("cards_encrypted")
private const val CONVERSATION_FIELD = "conversation"

/** Encrypted storage for settled result cards. It is deliberately independent from [ConfigFile]. */
@Singleton
class ConversationRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val vault: KeyVault,
) : ConversationStore {
    private val store = context.conversationDataStore

    override suspend fun load(): List<CardState>? {
        val encrypted = try {
            store.data.first()[CardsKey] ?: return null
        } catch (e: Exception) {
            DebugLog.log(TAG) { "conversation read failed: ${e.javaClass.simpleName}" }
            return emptyList()
        }
        return try {
            decode(vault.decrypt(encrypted, CONVERSATION_FIELD))
        } catch (e: Exception) {
            DebugLog.log(TAG) { "conversation decode failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override suspend fun save(cards: List<CardState>) {
        try {
            val encrypted = vault.encrypt(encode(cards), CONVERSATION_FIELD)
            store.edit { it[CardsKey] = encrypted }
        } catch (e: Exception) {
            DebugLog.log(TAG) { "conversation save failed: ${e.javaClass.simpleName}" }
        }
    }

    internal companion object {
        private const val TAG = "Conversation"
        private const val VERSION = 1
        private const val MAX_CARDS = 200
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        internal fun encode(cards: List<CardState>): String = json.encodeToString(
            ConversationDocument(
                version = VERSION,
                cards = cards.asSequence()
                    .filter { it.isSettled() }
                    .take(MAX_CARDS)
                    .map(CardDocument::from)
                    .toList(),
            ),
        )

        internal fun decode(text: String): List<CardState> {
            val document = json.decodeFromString<ConversationDocument>(text)
            require(document.version in 1..VERSION) { "Unsupported conversation version ${document.version}" }
            return document.cards.mapNotNull(CardDocument::toCard).take(MAX_CARDS)
        }

    }
}

@Serializable
private data class ConversationDocument(
    val version: Int,
    val cards: List<CardDocument> = emptyList(),
)

@Serializable
private data class CardDocument(
    val langs: List<String>,
    val stage: String,
    val source: String = "",
    @SerialName("source_lang") val sourceLang: String? = null,
    @SerialName("source_service") val sourceService: String? = null,
    val alternatives: List<AlternativeDocument> = emptyList(),
    val translations: List<TranslationDocument> = emptyList(),
    val error: String? = null,
    @SerialName("can_retry") val canRetry: Boolean = false,
) {
    fun toCard(): CardState? {
        val pair = langs.takeIf { it.size == 2 }
            ?.mapNotNull(::storedTarget)
            ?.takeIf { it.size == 2 && it[0] != it[1] }
            ?.let { LangPair(it[0], it[1]) }
            ?: return null
        val parsedStage = enumValueOrNull<CardStage>(stage)
            ?.takeIf { it == CardStage.Done || it == CardStage.Error }
            ?: return null
        return CardState(
            id = 0,
            langs = pair,
            stage = parsedStage,
            source = source,
            sourceLang = parseLanguage(sourceLang),
            sourceService = enumValueOrNull<AsrService>(sourceService),
            alternatives = alternatives.mapNotNull(AlternativeDocument::toAlternative),
            translations = translations.mapNotNull(TranslationDocument::toTranslation),
            error = enumValueOrNull<ErrorKind>(error),
            canRetry = canRetry,
        ).takeIf { it.isSettled() }
    }

    companion object {
        fun from(card: CardState) = CardDocument(
            langs = listOf(card.langs.first.tag, card.langs.second.tag),
            stage = card.stage.name,
            source = card.source,
            sourceLang = card.sourceLang?.tag,
            sourceService = card.sourceService?.name,
            alternatives = card.alternatives.map(AlternativeDocument::from),
            translations = card.translations.map(TranslationDocument::from),
            error = card.error?.name,
            canRetry = card.canRetry,
        )
    }
}

@Serializable
private data class AlternativeDocument(val service: String, val text: String) {
    fun toAlternative(): AsrAlternative? = enumValueOrNull<AsrService>(service)?.let { AsrAlternative(it, text) }

    companion object {
        fun from(value: AsrAlternative) = AlternativeDocument(value.service.name, value.text)
    }
}

@Serializable
private data class TranslationDocument(val to: String, val text: String = "") {
    fun toTranslation(): CardTranslation? = storedTarget(to)?.let { CardTranslation(it, text) }

    companion object {
        fun from(value: CardTranslation) = TranslationDocument(value.to.tag, value.text)
    }
}

private fun storedTarget(tag: String): Lang? = Lang.targets.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

private fun parseLanguage(tag: String?): Lang? {
    if (tag.isNullOrBlank()) return null
    val locale = Locale.forLanguageTag(tag)
    return tag.takeIf { locale.language.isNotBlank() && locale.toLanguageTag() != "und" }?.let(::Lang)
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private fun CardState.isSettled(): Boolean = when (stage) {
    CardStage.Done -> true
    CardStage.Error -> source.isNotEmpty() || translations.isNotEmpty()
    else -> false
}
