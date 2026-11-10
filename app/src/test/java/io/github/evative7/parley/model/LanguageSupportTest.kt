package io.github.evative7.parley.model

import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSupportTest {
    private val english = SupportWording(
        supports = { "Supports $it." },
        unsupported = { "Doesn't support $it." },
        extras = { "Also supports $it." },
        extrasMore = { names, more, _ -> "Also supports $names and $more more." },
        list = { it.joinToString(", ") },
        series = { it.joinToString(", ") },
        sentenceGap = " ",
    )
    private val chinese = SupportWording(
        supports = { "支持$it。" },
        unsupported = { "不支持$it。" },
        extras = { "另支持$it。" },
        extrasMore = { names, more, _ -> "另支持${names}及其他${more}种语言。" },
        list = { it.joinToString("、") },
        series = { it.joinToString("、") },
        sentenceGap = "",
    )

    @Test fun `summary compares project languages by base in project order`() {
        val project = listOf(Lang("en"), Lang("zh-CN"), Lang("ja"), Lang("zh-TW"))
        val summary = LanguageSupport.of("zh en fr").summarize(project)
        assertEquals(listOf(Lang("en"), Lang("zh-CN"), Lang("zh-TW")), summary.supported)
        assertEquals(listOf(Lang("ja")), summary.unsupported)
        assertEquals(listOf("fr"), summary.extras)
        assertEquals(1, summary.extraTotal)
    }

    @Test fun `extras keep the documented order, dialects included, and are capped`() {
        val support = LanguageSupport.of("en yue wuu de fr it ja ko pt")
        val summary = support.summarize(listOf(Lang("en")), extraLimit = 6)
        assertEquals(listOf("yue", "wuu", "de", "fr", "it", "ja"), summary.extras)
        assertEquals(8, summary.extraTotal)
        assertEquals(
            "Supports en. Also supports YUE, WUU, DE, FR, IT, JA and 2 more.",
            summary.render(languageName = { it.uppercase() }, projectName = { it.tag }, wording = english),
        )
        assertEquals(
            "支持en。另支持YUE、WUU、DE、FR、IT、JA及其他2种语言。",
            summary.render(languageName = { it.uppercase() }, projectName = { it.tag }, wording = chinese),
        )
    }

    @Test fun `render omits empty sentences`() {
        val text = LanguageSupport.of("en").summarize(listOf(Lang("en"))).render(
            languageName = { it }, projectName = { it.tag }, wording = english,
        )
        assertEquals("Supports en.", text)
    }

    @Test fun `support line goes before the last sentence of a description`() {
        assertEquals(
            "阿里的实时转写模型，质量较高。\nLINE\n流式输入版延迟更低，识别速度更快。",
            withSupportLine("阿里的实时转写模型，质量较高。流式输入版延迟更低，识别速度更快。", "LINE"),
        )
        assertEquals(
            "Model with good accuracy, built on Seed ASR 2.0 (non-stream input).\nLINE\nThe stream version is faster.",
            withSupportLine("Model with good accuracy, built on Seed ASR 2.0 (non-stream input). The stream version is faster.", "LINE"),
        )
        // No sentence break to split at: the line goes last.
        assertEquals("ข้อความ ไม่มีจุด\nLINE", withSupportLine("ข้อความ ไม่มีจุด", "LINE"))
    }

    @Test fun `TTS project support always agrees with the regional default voice`() {
        for (service in TtsService.entries) {
            for (region in DashScopeRegion.entries) {
                val support = service.support(region)
                for (lang in Lang.targets) {
                    assertEquals(
                        "$service $region ${lang.tag}",
                        service.defaultVoice(lang, region) != null,
                        support.supports(lang),
                    )
                }
            }
        }
        assertTrue(TtsService.CosyVoiceV3Flash.supports(Lang("ja"), DashScopeRegion.Beijing))
        assertFalse(TtsService.CosyVoiceV3Flash.supports(Lang("ja"), DashScopeRegion.Singapore))
    }
}
