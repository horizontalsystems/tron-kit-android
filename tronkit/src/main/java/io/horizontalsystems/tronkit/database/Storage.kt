package io.horizontalsystems.tronkit.database

import io.horizontalsystems.tronkit.models.Balance
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.LastBlockHeight
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState
import io.horizontalsystems.tronkit.models.Trc20Event
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

    fun getContractTransactionSyncBlockTimestamp(): Long? {
        val syncState = database.transactionDao().getTransactionSyncState(TransactionSourceType.Contract.id)
        return syncState?.blockTimestamp
    }

    fun saveContractTransactionSyncTimestamp(timestamp: Long) {
        database.transactionDao().insert(TransactionSyncState(TransactionSourceType.Contract.id, timestamp))
    }

    fun getTransactions(): List<Transaction> {
        return database.transactionDao().getTransactions()
    }

    fun saveTransactions(transactions: List<Transaction>) {
        database.transactionDao().insertTransactions(transactions)
    }

    fun saveTransactionsIfNotExists(transactions: List<Transaction>) {
        database.transactionDao().insertTransactionsIfNotExists(transactions)
    }

    fun getInternalTransactions(): List<InternalTransaction> {
        return database.transactionDao().getInternalTransactions()
    }

    fun saveInternalTransactions(transactions: List<InternalTransaction>) {
        database.transactionDao().insertInternalTransactions(transactions)
    }

    fun getTrc20Events(): List<Trc20Event> {
        return database.transactionDao().getTrc20Events()
    }

    fun saveTrc20Events(trc20Events: List<Trc20Event>) {
        database.transactionDao().insertTrc20Events(trc20Events)
    }

    private fun trxBalanceId() = "TRX"
    private fun trc10BalanceId(assetId: String) = "TRC10|$assetId"
    private fun trc20BalanceId(contractAddress: String) = "TRC20|$contractAddress"

    private enum class TransactionSourceType(val id: String) {
        Native("native"), Contract("contract")
    }

}
