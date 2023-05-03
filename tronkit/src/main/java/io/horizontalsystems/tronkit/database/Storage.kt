package io.horizontalsystems.tronkit.database

import io.horizontalsystems.tronkit.models.Balance
import io.horizontalsystems.tronkit.models.LastBlockHeight
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState
import java.math.BigInteger

class Storage(
    private val database: MainDatabase
) {
    fun getLastBlockHeight(): Long? {
        return database.lastBlockHeightDao().getLastBlockHeight()?.height
    }

    fun saveLastBlockHeight(lastBlockHeight: Long) {
        database.lastBlockHeightDao().insert(LastBlockHeight(lastBlockHeight))
    }

    fun getTrxBalance(): BigInteger? {
        return database.balanceDao().getBalance(trxBalanceId())?.balance
    }

    fun saveTrxBalance(balance: BigInteger) {
        database.balanceDao().insert(Balance(trxBalanceId(), balance))
    }

    fun getTransactionSyncBlockTimestamp(): Long? {
        val syncState = database.transactionDao().getTransactionSyncState(TransactionSourceType.Native.id)
        return syncState?.blockTimestamp
    }

    fun saveTransactionSyncTimestamp(timestamp: Long) {
        database.transactionDao().insert(TransactionSyncState(TransactionSourceType.Native.id, timestamp))
    }

    fun getTransactions(): List<Transaction> {
        return database.transactionDao().getTransactions()
    }

    fun saveTransactions(transactions: List<Transaction>) {
        database.transactionDao().insert(transactions)
    }

    private fun trxBalanceId() = "TRX"
    private fun trc10BalanceId(assetId: String) = "TRC10|$assetId"
    private fun trc20BalanceId(contractAddress: String) = "TRC20|$contractAddress"

    private enum class TransactionSourceType(val id: String) {
        Native("native"), Trc20("trc20")
    }

}
