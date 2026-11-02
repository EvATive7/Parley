package io.github.evative7.parley

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.DashScopeConfig
import io.github.evative7.parley.asr.DashScopeSession
import io.github.evative7.parley.asr.WarmSocket
import io.github.evative7.parley.data.KeyVault
import io.github.evative7.parley.data.SettingsRepository
import io.github.evative7.parley.di.NetworkModule
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.OfflineLanguageDetector
import io.github.evative7.parley.state.LangPair
import io.github.evative7.parley.translate.OpenAiCompatibleTranslator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Opt-in, billable device tests using only the credentials saved by the user in Parley.
 * Fixtures are public/synthetic audio, NOT microphone audio. Keys are never test arguments
 * or test output. A passing transport test alone is insufficient: also check the final text,
 * detected language, translation direction, and a translation keyword.
 */
@RunWith(AndroidJUnit4::class)
class CloudPipelineTest {
    @Test(timeout = 40_000) fun publicAudioToAsrToTranslation(): Unit = runPipeline(
        "/data/local/tmp/parley-asr-example.wav",
        "Hello World，这里是阿里巴巴语音实验室。",
        "zh", "en", "alibaba",
    )

    @Test(timeout = 40_000) fun englishAudioToChinese(): Unit = runPipeline(
        "/data/local/tmp/parley-english.wav",
        "Good morning. I would like a cup of coffee, please.",
        "en", "zh", "咖啡",
    )

    // The press path of the app: the socket was opened while the screen sat idle.
    @Test(timeout = 40_000) fun warmSocketPublicAudio(): Unit = runPipeline(
        "/data/local/tmp/parley-asr-example.wav",
        "Hello World，这里是阿里巴巴语音实验室。",
        "zh", "en", "alibaba", warm = true,
    )

    @Test(timeout = 40_000) fun warmSocketEnglish(): Unit = runPipeline(
        "/data/local/tmp/parley-english.wav",
        "Good morning. I would like a cup of coffee, please.",
        "en", "zh", "咖啡", warm = true,
    )

    // No task and no audio, so nothing is billed. Opt in with -e probeIdle true; takes up to 90 s.
    @Test(timeout = 100_000) fun spareSocketIdleLifetime(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("probeIdle") == "true")
        val repository = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext, KeyVault())
        val settings = withTimeout(10_000) { repository.settings.filterNotNull().first() }
        val client = NetworkModule.client()
        val diedAt = AtomicLong(-1)
        check(AsrProvider.DashScope in settings.asrProviders)
        val config = DashScopeConfig(
            "wss://${settings.dashScopeRegion.host}/api-ws/v1/inference",
            settings.dashScopeKey,
        )
        val socket = WarmSocket(client, config) { _, age -> diedAt.set(age) }
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (diedAt.get() < 0 && SystemClock.elapsedRealtime() < deadline) delay(250)
        socket.close()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putLong("spare_idle_lifetime_ms", diedAt.get())
        })
    }

    private fun runPipeline(
        path: String,
        expectedText: String,
        expectedLanguage: String,
        expectedTarget: String,
        translatedKeyword: String,
        warm: Boolean = false,
    ): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Billable tests require -e runCloudTests true", args.getString("runCloudTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Use the shell launcher, not startActivitySync: some OEMs reject an instrumentation
        // background activity launch and leave that synchronous method waiting indefinitely.
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n io.github.evative7.parley/.MainActivity",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val repository = SettingsRepository(instrumentation.targetContext, KeyVault())
        val settings = withTimeout(10_000) { repository.settings.filterNotNull().first() }
        check(settings.isReady && AsrProvider.DashScope in settings.asrProviders) {
            "Select and configure a DashScope service in Parley first"
        }
        val asrConfig = DashScopeConfig(
            "wss://${settings.dashScopeRegion.host}/api-ws/v1/inference",
            settings.dashScopeKey,
        )
        require(Regex("/data/local/tmp/parley-[A-Za-z0-9_.-]+\\.wav").matches(path))
        val bytes = instrumentation.uiAutomation.executeShellCommand("cat $path")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        val pcm = readMono16kWav(bytes)
        val client = NetworkModule.client()
        val translator = OpenAiCompatibleTranslator(client, repository)
        val detector = OfflineLanguageDetector()
        val diagnostics = ConcurrentLinkedQueue<String>()
        val spare = if (warm) WarmSocket(client, asrConfig).also { delay(3_000) } else null
        val startedAt = SystemClock.elapsedRealtime()
        val firstPartialAt = AtomicLong(-1)
        val session = DashScopeSession(client, asrConfig, spare) { diagnostics.add(it) }
        val partialJob = launch {
            session.partials.collect { text ->
                if (text.isNotBlank()) firstPartialAt.compareAndSet(-1, SystemClock.elapsedRealtime())
            }
        }
        try {
            // Same 20 ms frames as AudioRecorder, real-time paced; no mocked recognizer/translator.
            for (offset in pcm.indices step 320) {
                session.feed(pcm.copyOfRange(offset, minOf(offset + 320, pcm.size)))
                delay(20)
            }
            val releasedAt = SystemClock.elapsedRealtime()
            val transcript = withTimeout(10_000) { session.finish() }
            val finalizedAt = SystemClock.elapsedRealtime()
            instrumentation.sendStatus(0, Bundle().apply { putString("asr_text", transcript.text) })
            assertEquals("Final ASR includes stale drafts or wrong words", normalize(expectedText), normalize(transcript.text))
            val language = transcript.lang ?: withTimeout(1_500) { detector.detect(transcript.text) }
            assertEquals("Unexpected detected language", expectedLanguage, language?.base)
            val target = LangPair(Lang("zh-CN"), Lang("en")).targetFor(language)
            assertEquals(expectedTarget, target.base)
            val translatingAt = SystemClock.elapsedRealtime()
            var firstTranslationAt = -1L
            val translated = StringBuilder()
            withTimeout(10_000) {
                translator.translate(transcript.text, language, target).collect {
                    if (firstTranslationAt < 0) firstTranslationAt = SystemClock.elapsedRealtime()
                    translated.append(it)
                }
            }
            assertTrue("Translation is empty", translated.isNotBlank())
            assertTrue("Unexpected translation", translated.contains(translatedKeyword, ignoreCase = true))
            instrumentation.sendStatus(0, Bundle().apply {
                putString("fixture", path.substringAfterLast('/') + if (warm) " (warm socket)" else "")
                putString("asr_text", transcript.text)
                putString("detected_language", language?.tag ?: "und")
                putString("target_language", target.tag)
                putString("translation", translated.toString())
                putLong("audio_ms", pcm.size * 1000L / 16000)
                putLong("first_partial_ms", firstPartialAt.get().takeIf { it >= 0 }?.minus(startedAt) ?: -1)
                putLong("release_to_final_ms", finalizedAt - releasedAt)
                putLong("translation_first_fragment_ms", firstTranslationAt - translatingAt)
                putLong("translation_total_ms", SystemClock.elapsedRealtime() - translatingAt)
            })
        } finally {
            partialJob.cancel()
            session.cancel()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            diagnostics.forEach { diagnostic ->
                instrumentation.sendStatus(0, Bundle().apply { putString("asr_diagnostic", diagnostic) })
            }
        }
    }

    private fun normalize(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()

    private fun readMono16kWav(bytes: ByteArray): ShortArray {
        require(bytes.size in 44..2_000_000) { "Fixture is missing or exceeds the 60-second limit" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE")
        var offset = 12
        var supported = false
        while (offset + 8 <= bytes.size) {
            val size = buffer.getInt(offset + 4)
            require(size >= 0 && offset.toLong() + 8 + size <= bytes.size)
            when (tag(offset)) {
                "fmt " -> {
                    require(size >= 16)
                    supported = buffer.getShort(offset + 8).toInt() == 1 &&
                        buffer.getShort(offset + 10).toInt() == 1 &&
                        buffer.getInt(offset + 12) == 16000 &&
                        buffer.getShort(offset + 22).toInt() == 16
                }
                "data" -> {
                    require(supported && size % 2 == 0)
                    return ShortArray(size / 2) { buffer.getShort(offset + 8 + it * 2) }
                }
            }
            offset += 8 + size + (size % 2)
        }
        error("No PCM data chunk in fixture")
    }
}
