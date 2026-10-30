package io.github.evative7.parley.asr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object VolcengineProtocol {
    const val CLIENT_FULL_REQUEST = 0x1
    const val CLIENT_AUDIO_ONLY_REQUEST = 0x2
    const val SERVER_FULL_RESPONSE = 0x9
    const val SERVER_ERROR_RESPONSE = 0xf
    const val POS_SEQUENCE = 0x1
    const val NEG_WITH_SEQUENCE = 0x3
    const val NO_SERIALIZATION = 0x0
    const val JSON_SERIALIZATION = 0x1

    data class Response(
        val messageType: Int,
        val sequence: Int?,
        val isLast: Boolean,
        val json: JsonObject? = null,
        val errorCode: Int? = null,
    )

    fun frame(
        messageType: Int,
        flags: Int,
        sequence: Int,
        payload: ByteArray,
        serialization: Int,
        compress: Boolean = true,
    ): ByteArray {
        val body = if (compress) gzip(payload) else payload
        val output = ByteArrayOutputStream(12 + body.size)
        output.write(0x11)
        output.write((messageType shl 4) or flags)
        output.write((serialization shl 4) or if (compress) 0x1 else 0x0)
        output.write(0)
        output.write(intBytes(sequence))
        output.write(intBytes(body.size))
        output.write(body)
        return output.toByteArray()
    }

    fun parse(raw: ByteArray): Response? {
        if (raw.size < 4) return null
        val headerBytes = (raw[0].toInt() and 0x0f) * 4
        if (headerBytes < 4 || raw.size < headerBytes) return null
        val messageType = (raw[1].toInt() ushr 4) and 0x0f
        val flags = raw[1].toInt() and 0x0f
        val serialization = (raw[2].toInt() ushr 4) and 0x0f
        val compression = raw[2].toInt() and 0x0f
        var offset = headerBytes
        var sequence: Int? = null
        if (flags and 0x1 != 0) {
            if (raw.size < offset + 4) return null
            sequence = readInt(raw, offset)
            offset += 4
        }
        if (flags and 0x4 != 0) {
            if (raw.size < offset + 4) return null
            offset += 4
        }
        if (messageType == SERVER_ERROR_RESPONSE) {
            if (raw.size < offset + 8) return null
            val code = readInt(raw, offset)
            offset += 4
            val size = readInt(raw, offset).coerceAtLeast(0)
            offset += 4
            val payload = raw.copyOfRange(offset, minOf(raw.size, offset + size))
            decodePayload(payload, serialization, compression)
            return Response(messageType, sequence, flags and 0x2 != 0, errorCode = code)
        }
        if (messageType != SERVER_FULL_RESPONSE || raw.size < offset + 4) return null
        val size = readInt(raw, offset).coerceAtLeast(0)
        offset += 4
        val payload = raw.copyOfRange(offset, minOf(raw.size, offset + size))
        return Response(
            messageType = messageType,
            sequence = sequence,
            isLast = flags and 0x2 != 0,
            json = decodePayload(payload, serialization, compression),
        )
    }

    private fun decodePayload(payload: ByteArray, serialization: Int, compression: Int): JsonObject? {
        val decoded = try {
            if (compression == 0x1 && payload.isNotEmpty()) GZIPInputStream(ByteArrayInputStream(payload)).readBytes()
            else payload
        } catch (_: Exception) {
            return null
        }
        if (serialization != JSON_SERIALIZATION || decoded.isEmpty()) return null
        return try { Json.parseToJsonElement(decoded.toString(Charsets.UTF_8)) as? JsonObject }
        catch (_: Exception) { null }
    }

    private fun gzip(value: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(value) }
        output.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
    private fun readInt(value: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).int
}
