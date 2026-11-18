package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.models.RpcSource
import io.horizontalsystems.tronkit.rpc.JsonRpc
import io.horizontalsystems.tronkit.rpc.RpcResponse
import java.math.BigInteger

/**
 * One node provider over several: each request goes to the provider that last answered and
 * falls over to the next when that one fails. See [EndpointFailover].
 */
internal class FailoverNodeProvider<P>(
    providers: List<P>,
    preferredTtlMs: Long = EndpointFailover.PREFERRED_TTL_MS,
    clock: () -> Long = System::currentTimeMillis,
) : IRpcApiProvider, INodeApiProvider where P : IRpcApiProvider, P : INodeApiProvider {

    private val failover = EndpointFailover(providers, ::isAnswer, preferredTtlMs, clock)

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

    override suspend fun isTransactionOnChain(txID: String): Boolean =
        failover.execute { it.isTransactionOnChain(txID) }

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

        // What every node would answer the same way. Anything else (no connection, an HTTP
        // error such as a rate limit, a node reporting its own state) is worth another node.
        internal fun isAnswer(error: Throwable): Boolean = when (error) {
            is JsonRpc.ResponseError.RpcError -> !isNodeLimit(error.error)
            is NodeResponseError -> !error.isNodeState
            else -> false
        }

        // A node that has contract calls switched off, or lacks the method, says so in a
        // JSON-RPC error. Pocket Network routes to such nodes at random ("this node does not
        // support constant"), which fails eth_call and eth_estimateGas. Another node runs them.
        private fun isNodeLimit(error: RpcResponse.Error): Boolean {
            val message: String? = error.message
            return error.code == METHOD_NOT_FOUND || message?.contains("does not support", ignoreCase = true) == true
        }

        private const val METHOD_NOT_FOUND = -32601
    }
}
