package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.models.RpcSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL
import java.util.Base64

// The endpoints of one source belong to different providers, so a provider must only ever
// receive its own API key and auth.
class EndpointCredentialsTest {

    private class Server(private val alive: Boolean) {
        val apiKeys = mutableListOf<String?>()
        val authorizations = mutableListOf<String?>()

        private val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    apiKeys.add(request.getHeader("TRON-PRO-API-KEY"))
                    authorizations.add(request.getHeader("Authorization"))
                    return if (alive) {
                        MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"chainParameter":[{"key":"getEnergyFee","value":100}]}""")
                    } else {
                        MockResponse().setResponseCode(503)
                    }
                }
            }
            start()
        }

        val url: URL = server.url("/").toUrl()

        fun stop() = server.shutdown()
    }

    private val down = Server(alive = false)
    private val up = Server(alive = true)

    @After
    fun tearDown() {
        down.stop()
        up.stop()
    }

    private fun basic(auth: String) = "Basic " + Base64.getEncoder().encodeToString(auth.toByteArray())

    @Test
    fun eachEndpointReceivesOnlyItsOwnCredentials() = runBlocking {
        val source = RpcSource(
            listOf(
                RpcSource.Endpoint(down.url, apiKeys = listOf("first-key")),
                RpcSource.Endpoint(up.url, auth = "second-auth"),
            )
        )

        val parameters = FailoverNodeProvider.forSource(source).fetchChainParameters()

        assertEquals(listOf(ChainParameterResponse("getEnergyFee", 100)), parameters)
        assertEquals(listOf<String?>("first-key"), down.apiKeys)
        assertEquals(listOf<String?>(null), down.authorizations)
        assertEquals(listOf<String?>(null), up.apiKeys)
        assertEquals(listOf<String?>(basic("second-auth")), up.authorizations)
    }

    @Test
    fun singleProviderSourceSendsItsKeysToEveryUrl() = runBlocking {
        val source = RpcSource(listOf(down.url, up.url), apiKeys = listOf("shared-key"))

        FailoverNodeProvider.forSource(source).fetchChainParameters()

        assertEquals(listOf<String?>("shared-key"), down.apiKeys)
        assertEquals(listOf<String?>("shared-key"), up.apiKeys)
    }
}
