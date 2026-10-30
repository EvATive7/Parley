package io.github.evative7.parley.network

import io.github.evative7.parley.debug.DebugLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * An unauthenticated HEAD that only opens a pooled TLS connection, so the next real request to
 * the same host skips the handshake. Carries no key and no data; whatever status comes back is
 * ignored. Only helps plain HTTP calls: OkHttp never hands pooled connections to WebSockets.
 */
class ConnectionWarmer(private val client: OkHttpClient, private val tag: String) {
    private var warmedUrl: String? = null
    private var warmedAt = 0L

    @Synchronized
    fun warm(url: String) {
        val now = System.nanoTime()
        if (warmedUrl == url && now - warmedAt < WARM_INTERVAL_NS) return
        val request = try {
            Request.Builder().url(url).head().build()
        } catch (_: IllegalArgumentException) {
            return
        }
        warmedUrl = url
        warmedAt = now
        DebugLog.log(tag) { "warming connection to ${request.url.host}" }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                DebugLog.log(tag) { "warm-up failed after ${(System.nanoTime() - now) / 1_000_000} ms: ${e.javaClass.simpleName}" }
            }

            override fun onResponse(call: Call, response: Response) {
                DebugLog.log(tag) { "connection warm in ${(System.nanoTime() - now) / 1_000_000} ms" }
                response.close()
            }
        })
    }

    private companion object {
        // OkHttp keeps an idle connection for five minutes; the provider may close it sooner.
        const val WARM_INTERVAL_NS = 60_000_000_000L
    }
}
