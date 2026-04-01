package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.models.AccountInfo

interface IHistoryProvider {
    suspend fun fetchAccountInfo(address: String): AccountInfo
    suspend fun fetchTransactions(address: String, minTimestamp: Long, cursor: String?): Pair<List<TransactionData>, String?>
    suspend fun fetchTrc20Transactions(address: String, minTimestamp: Long, cursor: String?): Pair<List<ContractTransactionData>, String?>

    sealed class RequestError : Throwable() {
        object FailedToFetchAccountInfo : RequestError()
    }
}
