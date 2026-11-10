package io.github.evative7.parley.tts

import io.github.evative7.parley.model.Lang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxProtocolTest {
    @Test fun `start task requests 24 kHz mono PCM and language boost`() {
        val root = Json.parseToJsonElement(
            MiniMaxProtocol.startTask("speech-2.8-turbo", "Japanese_IntellectualSenior", Lang("ja")),
        ).jsonObject
        assertEquals("task_start", root.getValue("event").jsonPrimitive.content)
        assertEquals("speech-2.8-turbo", root.getValue("model").jsonPrimitive.content)
        assertEquals("Japanese", root.getValue("language_boost").jsonPrimitive.content)
        assertEquals(
            "Japanese_IntellectualSenior",
            root.getValue("voice_setting").jsonObject.getValue("voice_id").jsonPrimitive.content,
        )
        val audio = root.getValue("audio_setting").jsonObject
        assertEquals("pcm", audio.getValue("format").jsonPrimitive.content)
        assertEquals("24000", audio.getValue("sample_rate").jsonPrimitive.content)
        assertEquals("1", audio.getValue("channel").jsonPrimitive.content)
    }

    @Test fun `continue and finish events retain protocol fields`() {
        val continued = Json.parseToJsonElement(MiniMaxProtocol.continueTask("hello")).jsonObject
        assertEquals("task_continue", continued.getValue("event").jsonPrimitive.content)
        assertEquals("hello", continued.getValue("text").jsonPrimitive.content)
        val finished = Json.parseToJsonElement(MiniMaxProtocol.finishTask()).jsonObject
        assertEquals("task_finish", finished.getValue("event").jsonPrimitive.content)
    }

    @Test fun `continued event decodes hexadecimal PCM`() {
        val event = MiniMaxProtocol.parse(
            """{"event":"task_continued","data":{"audio":"007fff80"},"is_final":true,"base_resp":{"status_code":0}}""",
        ) as MiniMaxEvent.Audio
        assertArrayEquals(byteArrayOf(0, 127, -1, -128), event.bytes)
        assertTrue(event.final)
    }

    @Test fun `malformed audio and failed status become failures`() {
        assertTrue(
            MiniMaxProtocol.parse("""{"event":"task_continued","data":{"audio":"xyz"}}""") is MiniMaxEvent.Failed,
        )
        assertTrue(
            MiniMaxProtocol.parse(
                """{"event":"task_failed","base_resp":{"status_code":1004,"status_msg":"invalid api key"}}""",
            ) is MiniMaxEvent.Failed,
        )
    }
}
