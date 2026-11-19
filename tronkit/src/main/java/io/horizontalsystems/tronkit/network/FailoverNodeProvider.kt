package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.models.RpcSource
import io.horizontalsystems.tronkit.rpc.JsonRpc
import io.horizontalsystems.tronkit.rpc.RpcResponse
import kotlinx.coroutines.CancellationException
import java.math.BigInteger

/**
 * One node provider over several: each request goes to the provider that last answered and
 * falls over to the next when that one fails. See [EndpointFailover].
 */
internal class FailoverNodeProvider<P>(
    private val providers: List<P>,
    preferredTtlMs: Long = EndpointFailover.PREFERRED_TTL_MS,
    clock: () -> Long = System::currentTimeMillis,
) : IRpcApiProvider, INodeApiProvider where P : IRpcApiProvider, P : INodeApiProvider {

    private val failover = EndpointFailover(providers, ::isAnswer, preferredTtlMs, clock, ::mostTelling)

    override suspend fun <T> fetch(rpc: JsonRpc<T>): T =
        failover.execute { it.fetch(rpc) }

    override suspend fun fetchAccount(address: String): NodeAccountResponse? =
        failover.execute { it.fetchAccount(address) }

    override suspend fun fetchChainParameters(): List<ChainParameterResponse> =
        failover.execute { it.fetchChainParameters() }

    override suspend fun createTransaction(ownerAddress: String, toAddress: String, amount: BigInteger): CreatedTransaction =
        failover.execute { it.createTransaction(ownerAddress, toAddress, amount) }

    override suspend fun triggerSmartContract(
        ownerAddress: String,
        contractAddress: String,
        functionSelector: String,
        parameter: String,
        callValue: Long,
        feeLimit: Long
    ): CreatedTransaction =
        failover.execute {
            it.triggerSmartContract(ownerAddress, contractAddress, functionSelector, parameter, callValue, feeLimit)
        }

    override suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signature: ByteArray) =
        failover.execute { it.broadcastTransaction(createdTransaction, signature) }

    // A node that does not have the transaction says nothing about the others: another node
    // may be the one that took it, and a node can be a few blocks behind. So every node is
    // asked, and one that has it is enough. A node that cannot be asked counts as not having it.
    override suspend fun isTransactionOnChain(txID: String): Boolean {
        for (provider in providers) {
            if (hasTransaction(provider, txID)) return true
        }
        return false
    }

    private suspend fun hasTransaction(provider: P, txID: String): Boolean =
        try {
            provider.isTransactionOnChain(txID)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            false
        }

    companion object {
        /** A provider per endpoint, each with its own client, so credentials stay with their endpoint. */
        fun forSource(
            rpcSource: RpcSource,
            readTimeoutMs: Long = NODE_READ_TIMEOUT_MS,
        ): FailoverNodeProvider<TronGridProvider> =
            FailoverNodeProvider(rpcSource.endpoints.map { TronGridProvider(it.url, it.apiKeys, it.auth, readTimeoutMs) })

        // Node calls are small and answer within a second or two. A node that accepts the
        // connection and then stays silent should cost this much, not a minute, before the
        // next one is asked. Same as ethereum-kit, which runs on OkHttp's default.
        private const val NODE_READ_TIMEOUT_MS = 10_000L

        // What every node would answer the same way: a refusal to create or broadcast that is
        // about the request, not about the node. Anything else is worth another node: no
        // connection, an HTTP error such as a rate limit, a node reporting its own state.
        //
        // A JSON-RPC error is worth another node too. It may be the chain's answer (a
        // reverted call) or only this node's (Pocket Network routes to nodes that have
        // contract calls switched off), and the wording does not tell the two apart reliably.
        // Every JSON-RPC call here is a read, sending goes through broadcastTransaction, so
        // asking again costs a request and nothing else.
        internal fun isAnswer(error: Throwable): Boolean =
            error is NodeResponseError && !error.isNodeState

        // What to report when no node gave a result. An error a node gave for the call says
        // more than a failed connection to another node, and a node's own limit says least.
        internal fun mostTelling(errors: List<Throwable>): Throwable {
            val rpcErrors = errors.filterIsInstance<JsonRpc.ResponseError.RpcError>()
            return rpcErrors.firstOrNull { !isNodeLimit(it.error) }
                ?: rpcErrors.firstOrNull()
                ?: errors.last()
        }

        // "this node does not support constant" on Pocket Network, or a missing method
        private fun isNodeLimit(error: RpcResponse.Error): Boolean {
            val message: String? = error.message
            return error.code == METHOD_NOT_FOUND || message?.contains("does not support", ignoreCase = true) == true
        }

        private const val METHOD_NOT_FOUND = -32601
    }
}
