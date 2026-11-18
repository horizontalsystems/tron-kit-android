package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.rpc.JsonRpc
import java.math.BigInteger

internal fun createdTransaction(txID: String = "abc123") = CreatedTransaction(
    visible = false,
    txID = txID,
    raw_data = RawData(emptyList(), "0001", "0002", expiration = 60_000, timestamp = 0, fee_limit = null),
    raw_data_hex = "0a02",
    Error = null,
)

/** A node whose every call runs [onCall] first, so a test can make it fail. */
internal class FakeNode(private val name: String) : IRpcApiProvider, INodeApiProvider {
    val calls = mutableListOf<String>()

    var onCall: suspend (String) -> Unit = {}
    var onChain: suspend () -> Boolean = { false }

    private suspend fun call(method: String) {
        calls.add(method)
        onCall(method)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> fetch(rpc: JsonRpc<T>): T {
        call("fetch")
        return name as T
    }

    override suspend fun fetchAccount(address: String): NodeAccountResponse? {
        call("fetchAccount")
        return NodeAccountResponse(BigInteger.ONE)
    }

    override suspend fun fetchChainParameters(): List<ChainParameterResponse> {
        call("fetchChainParameters")
        return listOf(ChainParameterResponse(name, 1))
    }

    override suspend fun createTransaction(ownerAddress: String, toAddress: String, amount: BigInteger): CreatedTransaction {
        call("createTransaction")
        return createdTransaction(name)
    }

    override suspend fun triggerSmartContract(
        ownerAddress: String,
        contractAddress: String,
        functionSelector: String,
        parameter: String,
        callValue: Long,
        feeLimit: Long
    ): CreatedTransaction {
        call("triggerSmartContract")
        return createdTransaction(name)
    }

    override suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signature: ByteArray) {
        call("broadcastTransaction")
    }

    override suspend fun isTransactionOnChain(txID: String): Boolean {
        call("isTransactionOnChain")
        return onChain()
    }
}
