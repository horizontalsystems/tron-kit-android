package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.models.RpcSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// A node that takes the connection and never answers holds a request for the whole read
// timeout before the next node is asked, so node calls must not wait as long as history does.
class HungNodeTest {

    private val hung = MockWebServer().apply {
        enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        start()
    }
    private val up = MockWebServer().apply {
        enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"chainParameter":[{"key":"getEnergyFee","value":100}]}""")
        )
        start()
    }

    @After
    fun tearDown() {
        hung.shutdown()
        up.shutdown()
    }

    @Test
    fun silentNodeIsLeftAfterTheNodeReadTimeout() = runBlocking {
        val source = RpcSource(listOf(hung.url("/").toUrl(), up.url("/").toUrl()))
        val provider = FailoverNodeProvider.forSource(source, readTimeoutMs = 300)

        val startedAt = System.nanoTime()
        val parameters = provider.fetchChainParameters()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(listOf(ChainParameterResponse("getEnergyFee", 100)), parameters)
        assertEquals(1, hung.requestCount)
        assertTrue("took $elapsedMs ms", elapsedMs < 5_000)
    }
}
