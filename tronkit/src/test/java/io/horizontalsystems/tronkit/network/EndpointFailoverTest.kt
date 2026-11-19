package io.horizontalsystems.tronkit.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class EndpointFailoverTest {

    private class Refused : Exception("refused by the chain")

    private var now = 0L
    private val calls = mutableListOf<String>()

    private fun failover(vararg endpoints: String) =
        EndpointFailover(endpoints.toList(), isAnswer = { it is Refused }, preferredTtlMs = 1_000, clock = { now })

    private fun answer(dead: Set<String>): suspend (String) -> String = { endpoint ->
        calls.add(endpoint)
        if (endpoint in dead) throw IOException("$endpoint is down")
        endpoint
    }

    @Test
    fun firstEndpointAnswers() = runBlocking {
        val failover = failover("a", "b", "c")

        assertEquals("a", failover.execute(answer(emptySet())))
        assertEquals(listOf("a"), calls)
    }

    @Test
    fun deadEndpointFallsOverToTheNext() = runBlocking {
        val failover = failover("a", "b", "c")

        assertEquals("b", failover.execute(answer(setOf("a"))))
        assertEquals(listOf("a", "b"), calls)
    }

    @Test
    fun answeringEndpointStaysPreferred() = runBlocking {
        val failover = failover("a", "b", "c")
        failover.execute(answer(setOf("a")))
        calls.clear()

        assertEquals("b", failover.execute(answer(setOf("a"))))
        assertEquals(listOf("b"), calls)
        assertEquals("b", failover.preferred)
    }

    @Test
    fun preferredEndpointFailingWrapsAround() = runBlocking {
        val failover = failover("a", "b", "c")
        failover.execute(answer(setOf("a", "b")))
        calls.clear()

        assertEquals("a", failover.execute(answer(setOf("c"))))
        assertEquals(listOf("c", "a"), calls)
    }

    @Test
    fun firstEndpointIsRetriedAfterTtl() = runBlocking {
        val failover = failover("a", "b")
        failover.execute(answer(setOf("a")))
        now = 1_000
        calls.clear()

        assertEquals("a", failover.execute(answer(emptySet())))
        assertEquals(listOf("a"), calls)
    }

    @Test
    fun chainAnswerDoesNotFallOver() = runBlocking {
        val failover = failover("a", "b")

        try {
            failover.execute<String> { endpoint ->
                calls.add(endpoint)
                throw Refused()
            }
            fail("expected Refused")
        } catch (e: Refused) {
            assertEquals(listOf("a"), calls)
        }
    }

    // An error that is the chain's answer still shows the endpoint is alive, so the next
    // request must start there, not at the dead one before it.
    @Test
    fun endpointGivingAChainAnswerBecomesPreferred() = runBlocking {
        val failover = failover("a", "b", "c")

        try {
            failover.execute<String> { endpoint ->
                calls.add(endpoint)
                if (endpoint == "a") throw IOException("a is down")
                throw Refused()
            }
            fail("expected Refused")
        } catch (e: Refused) {
            assertEquals(listOf("a", "b"), calls)
        }
        calls.clear()

        assertEquals("b", failover.execute(answer(setOf("a"))))
        assertEquals(listOf("b"), calls)
    }

    @Test
    fun allDeadThrowsLastError() = runBlocking {
        val failover = failover("a", "b")

        try {
            failover.execute(answer(setOf("a", "b")))
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("b is down", e.message)
        }
    }

    @Test
    fun reportChoosesWhichFailureIsThrown() = runBlocking {
        val failover = EndpointFailover(listOf("a", "b"), isAnswer = { false }, report = { it.first() })

        try {
            failover.execute(answer(setOf("a", "b")))
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("a is down", e.message)
            assertEquals(listOf("a", "b"), calls)
        }
    }

    @Test
    fun preferenceReadWhileItIsSetIsKept() = runBlocking {
        // Later than the TTL, as any real clock is
        now = 5_000
        var reading = false
        lateinit var failover: EndpointFailover<String>
        // Another request reads the preference at the moment this one stores it
        failover = EndpointFailover(listOf("a", "b"), isAnswer = { it is Refused }, preferredTtlMs = 1_000, clock = {
            if (!reading) {
                reading = true
                failover.preferred
                reading = false
            }
            now
        })

        failover.execute(answer(setOf("a")))

        assertEquals("b", failover.preferred)
    }
}
