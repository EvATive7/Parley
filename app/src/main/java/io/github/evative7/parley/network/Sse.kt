package io.github.evative7.parley.network

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

internal fun Call.sseEvents() = callbackFlow {
    val call = this@sseEvents
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            close(networkFailure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    if (!it.isSuccessful) throw httpFailure(it.code)
                    if (it.body?.contentType()?.subtype != "event-stream") {
                        throw ServiceException(ServiceFailure.Response)
                    }
                    readSse(it.body!!.source()) { event ->
                        trySendBlocking(event).isSuccess && event != "[DONE]"
                    }
                }
                close()
            } catch (e: Exception) {
                close(if (e is ServiceException) e else networkFailure(e))
            }
        }
    })
    awaitClose { call.cancel() }
}
