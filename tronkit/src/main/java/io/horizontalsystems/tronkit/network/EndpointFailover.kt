package io.horizontalsystems.tronkit.network

import kotlinx.coroutines.CancellationException

/**
 * Runs a request against a list of endpoints, falling over to the next one when an endpoint
 * fails. A failure that [isAnswer] accepts is the chain's answer, not a dead endpoint: every
 * other node would give the same one, so it is thrown as it is.
 *
 * When no endpoint gives a result, [report] chooses which of the failures to throw, in the
 * order they happened. By default it is the last one.
 *
 * The endpoint that last answered stays preferred, so a dead first endpoint does not cost a
 * timeout on every request. After [preferredTtlMs] the first endpoint is tried first again: it
 * is the one the user selected, and it is usually back by then.
 */
internal class EndpointFailover<E>(
    private val endpoints: List<E>,
    private val isAnswer: (Throwable) -> Boolean,
    private val preferredTtlMs: Long = PREFERRED_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val report: (List<Throwable>) -> Throwable = { it.last() },
) {
    init {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required" }
    }

    @Volatile
    private var preferredIndex = 0

    @Volatile
    private var preferredSince = 0L

    val preferred: E
        get() = endpoints[currentPreferredIndex()]

    suspend fun <T> execute(attempt: suspend (E) -> T): T {
        val errors = mutableListOf<Throwable>()

        for (index in order()) {
            try {
                val result = attempt(endpoints[index])
                onAnswer(index)
                return result
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                if (isAnswer(throwable)) {
                    // The endpoint is alive: it answered, only with an error.
                    onAnswer(index)
                    throw throwable
                }
                errors.add(throwable)
            }
        }
        throw report(errors)
    }

    private fun currentPreferredIndex(): Int {
        val index = preferredIndex
        if (index != 0 && clock() - preferredSince >= preferredTtlMs) {
            preferredIndex = 0
            return 0
        }
        return index
    }

    private fun order(): List<Int> {
        val start = currentPreferredIndex()
        return endpoints.indices.map { (start + it) % endpoints.size }
    }

    private fun onAnswer(index: Int) {
        if (index != preferredIndex) {
            preferredIndex = index
            preferredSince = clock()
        }
    }

    companion object {
        const val PREFERRED_TTL_MS = 5 * 60 * 1000L
    }
}
