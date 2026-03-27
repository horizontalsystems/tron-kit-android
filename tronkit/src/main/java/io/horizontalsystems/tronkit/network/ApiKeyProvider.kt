package io.horizontalsystems.tronkit.network

class ApiKeyProvider(val apiKeys: List<String>) {
    init {
        check(apiKeys.isNotEmpty()) { "No API keys" }
    }
}