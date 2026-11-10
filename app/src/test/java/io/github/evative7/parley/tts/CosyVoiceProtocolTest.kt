package io.github.evative7.parley.tts

import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosyVoiceProtocolTest {
    @Test fun `run task requests 24 kHz PCM with model voice and language hint`() {
        val root = Json.parseToJsonElement(
            CosyVoiceProtocol.runTask("task", "qwen-audio-3.1-tts-flash", "longanhuan_v3.1", Lang("zh-CN")),
        ).jsonObject
        val header = root.getValue("header").jsonObject
        val payload = root.getValue("payload").jsonObject
        val parameters = payload.getValue("parameters").jsonObject
        assertEquals("run-task", header.getValue("action").jsonPrimitive.content)
        assertEquals("task", header.getValue("task_id").jsonPrimitive.content)
        assertEquals("tts", payload.getValue("task").jsonPrimitive.content)
        assertEquals("qwen-audio-3.1-tts-flash", payload.getValue("model").jsonPrimitive.content)
        assertEquals("longanhuan_v3.1", parameters.getValue("voice").jsonPrimitive.content)
        assertEquals("pcm", parameters.getValue("format").jsonPrimitive.content)
        assertEquals("24000", parameters.getValue("sample_rate").jsonPrimitive.content)
        assertTrue(parameters.getValue("language_hints").toString().contains("zh"))
    }

    @Test fun `continue and finish preserve the task id and text`() {
        val continued = Json.parseToJsonElement(CosyVoiceProtocol.continueTask("task", "hello")).jsonObject
        assertEquals("continue-task", continued.getValue("header").jsonObject.getValue("action").jsonPrimitive.content)
        assertEquals("hello", continued.getValue("payload").jsonObject.getValue("input").jsonObject.getValue("text").jsonPrimitive.content)
        val finished = Json.parseToJsonElement(CosyVoiceProtocol.finishTask("task")).jsonObject
        assertEquals("finish-task", finished.getValue("header").jsonObject.getValue("action").jsonPrimitive.content)
    }

    @Test fun `server events and authentication failures decode`() {
        assertEquals(CosyVoiceEvent.Started, CosyVoiceProtocol.parse("""{"header":{"event":"task-started"}}"""))
        assertEquals(CosyVoiceEvent.Finished, CosyVoiceProtocol.parse("""{"header":{"event":"task-finished"}}"""))
        val failed = CosyVoiceProtocol.parse(
            """{"header":{"event":"task-failed","error_code":"InvalidApiKey"}}""",
        ) as CosyVoiceEvent.Failed
        assertEquals(ServiceFailure.Authentication, failed.error.failure)
    }
}
