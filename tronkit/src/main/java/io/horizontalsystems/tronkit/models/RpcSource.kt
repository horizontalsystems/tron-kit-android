package io.horizontalsystems.tronkit.models

import io.horizontalsystems.tronkit.network.Network
import java.net.URL

class RpcSource(val urls: List<URL>, val apiKeys: List<String> = emptyList(), val auth: String? = null) {
    init {
        require(urls.isNotEmpty()) { "RpcSource requires at least one URL" }
    }

    companion object {
        fun tronGrid(network: Network, apiKeys: List<String>): RpcSource =
            RpcSource(listOf(network.tronGridUrl), apiKeys)

        fun tronGrid(network: Network, apiKey: String?): RpcSource =
            RpcSource(listOf(network.tronGridUrl), listOfNotNull(apiKey))
    }
}
