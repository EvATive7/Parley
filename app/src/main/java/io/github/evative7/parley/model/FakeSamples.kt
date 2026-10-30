package io.github.evative7.parley.model

/** Canned sentences used by the M1 prototype in place of real ASR and LLM output. */
internal object FakeSamples {

    class Sample(val lang: Lang, val text: String, val translations: Map<String, String>)

    val all = listOf(
        Sample(
            Lang("zh-CN"), "今天天气不错，我们去公园散步吧。",
            mapOf(
                "en" to "The weather is lovely today. Let's go for a walk in the park.",
                "ja" to "今日はいい天気だね。公園を散歩しに行こうよ。",
                "ko" to "오늘 날씨 좋다. 공원에 산책하러 가자.",
                "fr" to "Il fait beau aujourd'hui. Allons nous promener au parc.",
                "es" to "Hoy hace buen tiempo. Vamos a dar un paseo por el parque.",
                "zh-TW" to "今天天氣不錯，我們去公園散步吧。",
            ),
        ),
        Sample(
            Lang("en"), "Excuse me, how do I get to the nearest subway station?",
            mapOf(
                "zh-CN" to "不好意思，请问最近的地铁站怎么走？",
                "zh-TW" to "不好意思，請問最近的捷運站怎麼走？",
                "ja" to "すみません、一番近い地下鉄の駅へはどう行けばいいですか？",
                "ko" to "실례지만, 가장 가까운 지하철역은 어떻게 가나요?",
                "fr" to "Excusez-moi, comment aller à la station de métro la plus proche ?",
                "es" to "Disculpe, ¿cómo llego a la estación de metro más cercana?",
            ),
        ),
        Sample(
            Lang("ja"), "このラーメン、めちゃくちゃ美味しいね！",
            mapOf(
                "zh-CN" to "这碗拉面也太好吃了吧！",
                "zh-TW" to "這碗拉麵也太好吃了吧！",
                "en" to "This ramen is ridiculously good!",
                "ko" to "이 라멘 진짜 맛있다!",
                "fr" to "Ces ramens sont vraiment délicieux !",
                "es" to "¡Este ramen está buenísimo!",
            ),
        ),
        Sample(
            Lang("zh-CN"), "这个多少钱？能便宜一点吗？",
            mapOf(
                "en" to "How much is this? Could you go a little lower?",
                "ja" to "これはいくらですか？もう少し安くなりませんか？",
                "ko" to "이거 얼마예요? 조금만 깎아 주실 수 있어요?",
                "fr" to "Combien ça coûte ? Vous pouvez faire un petit prix ?",
                "es" to "¿Cuánto cuesta? ¿Me lo puede dejar un poco más barato?",
                "zh-TW" to "這個多少錢？能便宜一點嗎？",
            ),
        ),
    )

    fun find(text: String): Sample? = all.firstOrNull { it.text == text }

    fun translate(text: String, to: Lang): String {
        val sample = find(text) ?: return text
        if (sample.lang.tag == to.tag) return text
        return sample.translations[to.tag]
            ?: sample.translations.entries.firstOrNull { Lang(it.key).base == to.base }?.value
            ?: "〔${to.displayName()}〕$text"
    }

    /** Splits [text] into the chunks a streaming service would emit: words, or 1–2 CJK characters. */
    fun chunks(text: String, cjkSize: Int = 2): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            val cjk = Character.UnicodeScript.of(ch.code).let {
                it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA ||
                    it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
            }
            if ((cjk && sb.length >= cjkSize) || ch == ' ' || !ch.isLetterOrDigit() && !cjk) {
                out += sb.toString(); sb.clear()
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }
}
