package io.horizontalsystems.tronkit.models

import io.horizontalsystems.tronkit.network.Network
import java.net.URL

class TransactionSource(val name: String, val type: SourceType) {

    sealed class SourceType {
        data class TronGrid(val url: URL, val apiKeys: List<String>) : SourceType()
        data class TronScan(val url: URL, val apiKey: String?) : SourceType()
    }

    companion object {
        private val tronScanUrl = URL("https://apilist.tronscanapi.com/api/")

        fun tronGrid(network: Network, apiKeys: List<String>): TransactionSource =
            TransactionSource("TronGrid", SourceType.TronGrid(network.tronGridUrl, apiKeys))

        fun tronGrid(network: Network, apiKey: String?): TransactionSource =
            TransactionSource("TronGrid", SourceType.TronGrid(network.tronGridUrl, listOfNotNull(apiKey)))

        fun tronScan(apiKey: String? = null): TransactionSource =
            TransactionSource("TronScan", SourceType.TronScan(tronScanUrl, apiKey))
    }
}
