package io.github.evative7.parley.tts

import io.github.evative7.parley.asr.VolcengineProtocol
import io.github.evative7.parley.model.Lang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoProtocolTest {
    @Test fun `start session reuses Volcengine event framing and requests PCM`() {
        val parsed = VolcengineProtocol.parseEvent(
            DoubaoProtocol.startSession("session", "zh_female_gaolengyujie_uranus_bigtts", Lang("zh-CN")),
        )!!
        assertEquals(DoubaoProtocol.START_SESSION, parsed.event)
        assertEquals("session", parsed.sessionId)
        val body = Json.parseToJsonElement(parsed.payload.toString(Charsets.UTF_8)).jsonObject
        val params = body.getValue("req_params").jsonObject
        val audio = params.getValue("audio_params").jsonObject
        assertEquals("BidirectionalTTS", body.getValue("namespace").jsonPrimitive.content)
        assertEquals("zh-cn", params.getValue("explicit_language").jsonPrimitive.content)
        assertEquals("pcm", audio.getValue("format").jsonPrimitive.content)
        assertEquals("24000", audio.getValue("sample_rate").jsonPrimitive.content)
    }

    @Test fun `task and finish frames keep session and text`() {
        val task = VolcengineProtocol.parseEvent(
            DoubaoProtocol.task("session", "voice", Lang("ja"), "こんにちは"),
        )!!
        assertEquals(DoubaoProtocol.TASK_REQUEST, task.event)
        assertEquals("session", task.sessionId)
        val body = Json.parseToJsonElement(task.payload.toString(Charsets.UTF_8)).jsonObject
        assertEquals("こんにちは", body.getValue("req_params").jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals("ja", body.getValue("req_params").jsonObject.getValue("explicit_language").jsonPrimitive.content)
        val finish = VolcengineProtocol.parseEvent(DoubaoProtocol.finishSession("session"))!!
        assertEquals(DoubaoProtocol.FINISH_SESSION, finish.event)
    }

    @Test fun `resource grant error gives the console action`() {
        val error = DoubaoProtocol.failure("resource not granted".toByteArray(), null)
        assertTrue(error is DoubaoTtsException)
        assertTrue(error.message!!.contains("语音合成 2.0"))
    }
}
