package io.horizontalsystems.tronkit.transaction

import io.horizontalsystems.tronkit.network.FakeNode
import io.horizontalsystems.tronkit.network.NodeResponseError
import io.horizontalsystems.tronkit.network.createdTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

// A broadcast that fails is only a failure if no node has the transaction. Reporting a
// delivered transaction as failed makes the user send again, and that is a second payment.
class TransactionSenderBroadcastTest {

    private val node = FakeNode("node")
    private val sender = TransactionSender(node, deliveryCheckTimeoutMs = 200)
    private val transaction = createdTransaction()
    private val signature = ByteArray(65)

    private fun broadcastFailsWith(error: Throwable) {
        node.onCall = { method -> if (method == "broadcastTransaction") throw error }
    }

    private fun nodeError(code: String) = NodeResponseError("broadcastTransaction error: $code - ", code)

    private fun broadcastFailure(): Throwable = runBlocking {
        try {
            sender.broadcast(transaction, signature)
        } catch (error: Throwable) {
            return@runBlocking error
        }
        fail("expected the broadcast to fail")
        throw IllegalStateException()
    }

    @Test
    fun acceptedTransactionIsNotLookedUp() = runBlocking {
        sender.broadcast(transaction, signature)

        assertEquals(listOf("broadcastTransaction"), node.calls)
    }

    @Test
    fun duplicateIsSuccessWithoutLookup() = runBlocking {
        broadcastFailsWith(nodeError("DUP_TRANSACTION_ERROR"))

        sender.broadcast(transaction, signature)

        assertEquals(listOf("broadcastTransaction"), node.calls)
    }

    // The first node took the transaction but its answer was lost; the retry reaches the next
    // node after the transaction's minute has passed, so that node calls it expired.
    @Test
    fun expiredForADeliveredTransactionIsSuccess() = runBlocking {
        broadcastFailsWith(nodeError("TRANSACTION_EXPIRATION_ERROR"))
        node.onChain = { true }

        sender.broadcast(transaction, signature)

        assertEquals(listOf("broadcastTransaction", "isTransactionOnChain"), node.calls)
    }

    @Test
    fun expiredForATransactionNobodyHasKeepsTheError() {
        val error = nodeError("TRANSACTION_EXPIRATION_ERROR")
        broadcastFailsWith(error)

        assertSame(error, broadcastFailure())
    }

    @Test
    fun rejectedTransactionKeepsTheError() {
        val error = nodeError("CONTRACT_VALIDATE_ERROR")
        broadcastFailsWith(error)

        assertSame(error, broadcastFailure())
    }

    @Test
    fun lostAnswerForADeliveredTransactionIsSuccess() = runBlocking {
        broadcastFailsWith(IOException("timeout"))
        node.onChain = { true }

        sender.broadcast(transaction, signature)
    }

    @Test
    fun unreachableNodesKeepTheBroadcastError() {
        val error = IOException("broadcast timeout")
        node.onCall = { method ->
            throw if (method == "broadcastTransaction") error else IOException("lookup timeout")
        }

        assertSame(error, broadcastFailure())
    }

    @Test
    fun lookupThatTakesTooLongKeepsTheBroadcastError() {
        val error = IOException("broadcast timeout")
        broadcastFailsWith(error)
        node.onChain = {
            delay(5_000)
            true
        }

        assertSame(error, broadcastFailure())
    }
}
