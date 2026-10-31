package io.github.evative7.parley.mock

import io.github.evative7.parley.model.Lang

/**
 * Sample sentences in every language of the language bar. The mock recognizer "hears" them and the mock
 * translator finds them again, so a mock conversation reads like a real one.
 */
internal object MockPhrases {
    private val phrases: List<Map<String, String>> = listOf(
        mapOf(
            "en" to "Hello, nice to meet you.",
            "zh-CN" to "你好，很高兴认识你。",
            "zh-TW" to "你好，很高興認識你。",
            "ja" to "こんにちは、はじめまして。",
            "ko" to "안녕하세요, 만나서 반갑습니다.",
            "fr" to "Bonjour, ravi de vous rencontrer.",
            "de" to "Hallo, schön, Sie kennenzulernen.",
            "es" to "Hola, encantado de conocerte.",
            "it" to "Ciao, piacere di conoscerti.",
            "pt" to "Olá, prazer em conhecê-lo.",
            "ru" to "Здравствуйте, приятно познакомиться.",
            "ar" to "مرحبًا، تشرفت بمعرفتك.",
            "th" to "สวัสดีครับ ยินดีที่ได้รู้จัก",
            "vi" to "Xin chào, rất vui được gặp bạn.",
            "id" to "Halo, senang bertemu dengan Anda.",
        ),
        mapOf(
            "en" to "Where is the nearest train station?",
            "zh-CN" to "最近的火车站在哪里？",
            "zh-TW" to "最近的火車站在哪裡？",
            "ja" to "一番近い駅はどこですか？",
            "ko" to "가장 가까운 기차역이 어디예요?",
            "fr" to "Où se trouve la gare la plus proche ?",
            "de" to "Wo ist der nächste Bahnhof?",
            "es" to "¿Dónde está la estación de tren más cercana?",
            "it" to "Dov'è la stazione ferroviaria più vicina?",
            "pt" to "Onde fica a estação de trem mais próxima?",
            "ru" to "Где ближайший вокзал?",
            "ar" to "أين أقرب محطة قطار؟",
            "th" to "สถานีรถไฟที่ใกล้ที่สุดอยู่ที่ไหน",
            "vi" to "Ga tàu gần nhất ở đâu?",
            "id" to "Di mana stasiun kereta terdekat?",
        ),
        mapOf(
            "en" to "How much does this cost?",
            "zh-CN" to "这个多少钱？",
            "zh-TW" to "這個多少錢？",
            "ja" to "これはいくらですか？",
            "ko" to "이거 얼마예요?",
            "fr" to "Combien ça coûte ?",
            "de" to "Wie viel kostet das?",
            "es" to "¿Cuánto cuesta esto?",
            "it" to "Quanto costa questo?",
            "pt" to "Quanto custa isto?",
            "ru" to "Сколько это стоит?",
            "ar" to "كم سعر هذا؟",
            "th" to "อันนี้ราคาเท่าไหร่",
            "vi" to "Cái này giá bao nhiêu?",
            "id" to "Berapa harganya?",
        ),
        mapOf(
            "en" to "Could you speak a little more slowly, please?",
            "zh-CN" to "请你说慢一点好吗？",
            "zh-TW" to "請你說慢一點好嗎？",
            "ja" to "もう少しゆっくり話していただけますか？",
            "ko" to "조금만 천천히 말씀해 주시겠어요?",
            "fr" to "Pourriez-vous parler un peu plus lentement, s'il vous plaît ?",
            "de" to "Könnten Sie bitte etwas langsamer sprechen?",
            "es" to "¿Podría hablar un poco más despacio, por favor?",
            "it" to "Potrebbe parlare un po' più lentamente, per favore?",
            "pt" to "Você poderia falar um pouco mais devagar, por favor?",
            "ru" to "Не могли бы вы говорить немного медленнее?",
            "ar" to "هل يمكنك التحدث ببطء أكثر قليلًا من فضلك؟",
            "th" to "ช่วยพูดช้าลงอีกนิดได้ไหมครับ",
            "vi" to "Bạn có thể nói chậm hơn một chút được không?",
            "id" to "Bisakah Anda berbicara sedikit lebih pelan?",
        ),
        mapOf(
            "en" to "I'd like to book a table for two at seven tonight.",
            "zh-CN" to "我想预订今晚七点的两人桌。",
            "zh-TW" to "我想預訂今晚七點的兩人桌。",
            "ja" to "今夜7時に2名で予約したいのですが。",
            "ko" to "오늘 저녁 7시에 두 명 자리를 예약하고 싶어요.",
            "fr" to "Je voudrais réserver une table pour deux ce soir à sept heures.",
            "de" to "Ich möchte heute Abend um sieben einen Tisch für zwei reservieren.",
            "es" to "Quisiera reservar una mesa para dos esta noche a las siete.",
            "it" to "Vorrei prenotare un tavolo per due stasera alle sette.",
            "pt" to "Eu gostaria de reservar uma mesa para dois hoje à noite, às sete.",
            "ru" to "Я хотел бы забронировать столик на двоих сегодня на семь вечера.",
            "ar" to "أود حجز طاولة لشخصين الليلة في الساعة السابعة.",
            "th" to "ผมอยากจองโต๊ะสำหรับสองคนคืนนี้ตอนหนึ่งทุ่ม",
            "vi" to "Tôi muốn đặt bàn cho hai người lúc bảy giờ tối nay.",
            "id" to "Saya ingin memesan meja untuk dua orang malam ini pukul tujuh.",
        ),
        mapOf(
            "en" to "Thank you very much for your help today.",
            "zh-CN" to "非常感谢你今天的帮助。",
            "zh-TW" to "非常感謝你今天的幫忙。",
            "ja" to "今日は本当にありがとうございました。",
            "ko" to "오늘 도와주셔서 정말 감사합니다.",
            "fr" to "Merci beaucoup pour votre aide aujourd'hui.",
            "de" to "Vielen Dank für Ihre Hilfe heute.",
            "es" to "Muchas gracias por tu ayuda hoy.",
            "it" to "Grazie mille per il tuo aiuto oggi.",
            "pt" to "Muito obrigado pela sua ajuda hoje.",
            "ru" to "Большое спасибо за вашу помощь сегодня.",
            "ar" to "شكرًا جزيلًا على مساعدتك اليوم.",
            "th" to "ขอบคุณมากสำหรับความช่วยเหลือวันนี้",
            "vi" to "Cảm ơn bạn rất nhiều vì đã giúp đỡ hôm nay.",
            "id" to "Terima kasih banyak atas bantuan Anda hari ini.",
        ),
    )

    val size: Int get() = phrases.size

    /** Sentence [index] in [lang], falling back to English for a language without its own. */
    fun get(index: Int, lang: Lang): String {
        val phrase = phrases[index.mod(phrases.size)]
        return phrase[lang.tag] ?: phrase.entries.firstOrNull { Lang(it.key).base == lang.base }?.value ?: phrase.getValue("en")
    }

    /** [text] in [to] if it is one of the sample sentences, in any language; null otherwise. */
    fun translate(text: String, to: Lang): String? {
        val trimmed = text.trim()
        val index = phrases.indexOfFirst { phrase -> phrase.values.any { it == trimmed } }
        return if (index < 0) null else get(index, to)
    }
}
