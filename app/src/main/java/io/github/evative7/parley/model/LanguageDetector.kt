package io.github.evative7.parley.model

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

fun interface LanguageDetector {
    suspend fun detect(text: String): Lang?
}

/** Bundled offline model: no transcript is sent to a second cloud service for language ID. */
@Singleton
class OfflineLanguageDetector @Inject constructor() : LanguageDetector {
    private val identifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build(),
        )
    }

    override suspend fun detect(text: String): Lang? = suspendCancellableCoroutine { continuation ->
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag -> continuation.resume(tag.takeUnless { it == "und" }?.let(::Lang)) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
