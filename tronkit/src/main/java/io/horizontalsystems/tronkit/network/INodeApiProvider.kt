package io.horizontalsystems.tronkit.network

import java.math.BigInteger

interface INodeApiProvider {
    suspend fun fetchAccount(address: String): NodeAccountResponse?
    suspend fun fetchChainParameters(): List<ChainParameterResponse>
    suspend fun createTransaction(ownerAddress: String, toAddress: String, amount: BigInteger): CreatedTransaction
    suspend fun triggerSmartContract(
        ownerAddress: String,
        contractAddress: String,
        functionSelector: String,
        parameter: String,
        callValue: Long,
        feeLimit: Long
    ): CreatedTransaction
    suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signature: ByteArray)

    /** Whether a block holds the transaction. One that is still waiting for a block is not found. */
    suspend fun isTransactionOnChain(txID: String): Boolean
}

data class NodeAccountResponse(val balance: BigInteger)

data class ChainParameterResponse(val key: String, val value: Long)
