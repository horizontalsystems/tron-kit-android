package io.horizontalsystems.tronkit.network

class ApiKeyProvider(
    private val apiKeys: List<String>
) {
    private var currentIndex = 0

    init {
        check(apiKeys.isNotEmpty()) { "No API keys" }
    }

    fun apiKey(): String {
        currentIndex = currentIndex.inc().mod(apiKeys.size)

        return apiKeys[currentIndex]
    }
}
