package io.horizontalsystems.tronkit.models

import io.horizontalsystems.tronkit.network.Network
import java.net.URL

/**
 * The nodes the kit talks to. The endpoints are tried in order: when one fails the next is
 * used, and the one that answered stays in use. Each endpoint carries its own credentials, so
 * endpoints of different providers can share one source and no provider receives another's.
 */
class RpcSource(val endpoints: List<Endpoint>) {

    /** One node URL with the API keys and auth that belong to it. */
    data class Endpoint(val url: URL, val apiKeys: List<String> = emptyList(), val auth: String? = null)

    /** A single provider: every one of [urls] gets the same [apiKeys] and [auth]. */
    constructor(urls: List<URL>, apiKeys: List<String> = emptyList(), auth: String? = null) :
            this(urls.map { Endpoint(it, apiKeys, auth) })

    init {
        require(endpoints.isNotEmpty()) { "RpcSource requires at least one URL" }
    }

    val urls: List<URL>
        get() = endpoints.map { it.url }

    companion object {
        fun tronGrid(network: Network, apiKeys: List<String>): RpcSource =
            RpcSource(listOf(network.tronGridUrl), apiKeys)

        fun tronGrid(network: Network, apiKey: String?): RpcSource =
            RpcSource(listOf(network.tronGridUrl), listOfNotNull(apiKey))
    }
}
