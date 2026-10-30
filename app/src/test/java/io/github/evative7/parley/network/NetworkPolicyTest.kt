package io.github.evative7.parley.network

import io.github.evative7.parley.di.NetworkModule
import org.junit.Assert.*
import org.junit.Test

class NetworkPolicyTest {
    @Test fun `provider liveness is not a five second pong watchdog and requests never auto retry`() {
        val client = NetworkModule.client()
        assertEquals(0, client.pingIntervalMillis)
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }
}
