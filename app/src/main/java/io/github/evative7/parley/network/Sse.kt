package io.github.evative7.parley.network

import io.github.evative7.parley.debug.DebugLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

/** SSE framing: comments, CRLF, multiline data and blank-line dispatch. */
internal fun readSse(source: BufferedSource, emit: (String) -> Boolean) {
    val data = StringBuilder()
    while (true) {
        val line = source.readUtf8Line() ?: break
        if (line.isEmpty()) {
            if (data.isNotEmpty()) {
                if (!emit(data.toString().removeSuffix("\n"))) return
                data.setLength(0)
            }
        } else if (line.startsWith("data:")) {
            data.append(line.substring(5).removePrefix(" ")).append('\n')
            if (data.length > 256 * 1024) throw ServiceException(ServiceFailure.Response)
        }
    }
    // An incomplete event at EOF is not dispatched.
}

/** [tag] labels debug-mode timings; nothing from the request or response body is logged. */
internal fun Call.sseEvents(tag: String = "SSE") = callbackFlow {
    val call = this@sseEvents
    val sentAt = System.nanoTime()
    fun elapsed() = (System.nanoTime() - sentAt) / 1_000_000
    DebugLog.log(tag) { "request sent to ${call.request().url.host}" }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            DebugLog.log(tag) { "request failed after ${elapsed()} ms: ${e.javaClass.simpleName}" }
            close(networkFailure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            DebugLog.log(tag) { "HTTP ${response.code} headers after ${elapsed()} ms (${response.protocol})" }
            try {
                response.use {
                    if (!it.isSuccessful) throw httpFailure(it.code)
                    if (it.body?.contentType()?.subtype != "event-stream") {
                        throw ServiceException(ServiceFailure.Response)
                    }
                    var events = 0
                    readSse(it.body!!.source()) { event ->
                        if (events++ == 0) DebugLog.log(tag) { "first SSE event after ${elapsed()} ms" }
                        trySendBlocking(event).isSuccess && event != "[DONE]"
                    }
                    DebugLog.log(tag) { "stream closed after ${elapsed()} ms, $events events" }
                }
                close()
            } catch (e: Exception) {
                close(if (e is ServiceException) e else networkFailure(e))
            }
        }
    })
    awaitClose { call.cancel() }
}
