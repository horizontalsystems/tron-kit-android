package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.rpc.JsonRpc
import io.horizontalsystems.tronkit.rpc.RpcResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Type
import java.math.BigInteger

class FailoverNodeProviderTest {

    private val first = FakeNode("first")
    private val second = FakeNode("second")
    private val provider = FailoverNodeProvider(listOf(first, second))

    private fun failure(block: suspend () -> Unit): Throwable = runBlocking {
        try {
            block()
        } catch (error: Throwable) {
            return@runBlocking error
        }
        fail("expected a failure")
        throw IllegalStateException()
    }

    @Test
    fun deadNodeFallsOverForEveryKindOfCall() = runBlocking {
        first.onCall = { throw IOException("first is down") }

        assertEquals("second", provider.createTransaction("owner", "to", BigInteger.ONE).txID)
        assertEquals("second", provider.triggerSmartContract("owner", "contract", "f()", "", 0, 1).txID)
        assertEquals("second", provider.fetchChainParameters().single().key)
        assertEquals(BigInteger.ONE, provider.fetchAccount("address")?.balance)
        provider.broadcastTransaction(createdTransaction(), ByteArray(65))

        // Only the first call tried the dead node; the rest went straight to the one that answered
        assertEquals(listOf("createTransaction"), first.calls)
        assertEquals(
            listOf("createTransaction", "triggerSmartContract", "fetchChainParameters", "fetchAccount", "broadcastTransaction"),
            second.calls
        )
    }

    @Test
    fun refusalByTheChainIsNotRetriedOnAnotherNode() {
        val refusal = NodeResponseError("broadcastTransaction error: CONTRACT_VALIDATE_ERROR - balance is not sufficient", "CONTRACT_VALIDATE_ERROR")
        first.onCall = { throw refusal }

        assertSame(refusal, failure { provider.broadcastTransaction(createdTransaction(), ByteArray(65)) })
        assertTrue(second.calls.isEmpty())
    }

    @Test
    fun nodeReportingItsOwnStateFallsOver() = runBlocking {
        first.onCall = { throw NodeResponseError("broadcastTransaction error: SERVER_BUSY - ", "SERVER_BUSY") }

        provider.broadcastTransaction(createdTransaction(), ByteArray(65))

        assertEquals(listOf("broadcastTransaction"), second.calls)
    }

    @Test
    fun rateLimitedNodeFallsOver() = runBlocking {
        // What Retrofit throws for HTTP 429 is not a chain answer either
        first.onCall = { throw RuntimeException("HTTP 429") }

        assertEquals("second", provider.createTransaction("owner", "to", BigInteger.ONE).txID)
    }

    // Seen on Pocket Network: some of its nodes have contract calls switched off, which breaks
    // TRC20 balances, allowances and fee estimates unless another node is asked.
    @Test
    fun nodeWithoutContractCallsFallsOver() = runBlocking {
        first.onCall = { throw JsonRpc.ResponseError.RpcError(RpcResponse.Error(-32600, "this node does not support constant")) }

        assertEquals("second", provider.fetch(stringRpc("eth_call")))
        assertEquals(listOf("fetch"), second.calls)
    }

    @Test
    fun nodeWithoutTheMethodFallsOver() = runBlocking {
        first.onCall = { throw JsonRpc.ResponseError.RpcError(RpcResponse.Error(-32601, "the method eth_estimateGas does not exist")) }

        assertEquals("second", provider.fetch(stringRpc("eth_estimateGas")))
    }

    @Test
    fun revertedCallIsNotRetriedOnAnotherNode() {
        val reverted = JsonRpc.ResponseError.RpcError(RpcResponse.Error(-32000, "REVERT opcode executed"))
        first.onCall = { throw reverted }

        assertSame(reverted, failure { provider.fetch(stringRpc("eth_call")) })
        assertTrue(second.calls.isEmpty())
    }

    private fun stringRpc(method: String) = object : JsonRpc<String>(method, emptyList()) {
        override val typeOfResult: Type = String::class.java
    }

    @Test
    fun rpcErrorIsNotRetriedOnAnotherNode() {
        val rpcError = JsonRpc.ResponseError.RpcError(RpcResponse.Error(-32000, "execution reverted"))

        assertTrue(FailoverNodeProvider.isAnswer(rpcError))
        assertTrue(FailoverNodeProvider.isAnswer(NodeResponseError("createTransaction error: no account")))
    }
}
