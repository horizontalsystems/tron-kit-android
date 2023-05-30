package io.horizontalsystems.tronkit.database

import androidx.sqlite.db.SimpleSQLiteQuery
import io.horizontalsystems.tronkit.models.Balance
import io.horizontalsystems.tronkit.models.ChainParameter
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.LastBlockHeight
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState
import io.horizontalsystems.tronkit.models.TransactionTag
import io.horizontalsystems.tronkit.models.Trc20Balance
import io.horizontalsystems.tronkit.models.Trc20EventRecord
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

    fun saveBalances(trxBalance: BigInteger, balances: List<Trc20Balance>) {
        database.runInTransaction {
            database.balanceDao().deleteAll()

            database.balanceDao().insert(Balance(trxBalanceId(), trxBalance))
            database.balanceDao().insert(balances.map { (contractAddress, balance) -> Balance(trc20BalanceId(contractAddress), balance) })
        }
    }

    fun getTrc20Balance(contractAddress: String): BigInteger? {
        return database.balanceDao().getBalance(trc20BalanceId(contractAddress))?.balance
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

    fun getUnprocessedTransactions(): List<Transaction> {
        return database.transactionDao().getUnprocessedTransactions()
    }

    fun getTransactions(hashes: List<ByteArray>): List<Transaction> =
        database.transactionDao().getTransactions(hashes)

    fun getTransactions(): List<Transaction> {
        return database.transactionDao().getTransactions()
    }

    fun saveTransactions(transactions: List<Transaction>) {
        database.transactionDao().insertTransactions(transactions)
    }

    fun saveTransactionsIfNotExists(transactions: List<Transaction>) {
        database.transactionDao().insertTransactionsIfNotExists(transactions)
    }

    suspend fun getTransactionsBefore(tags: List<List<String>>, hash: ByteArray?, limit: Int?): List<Transaction> {
        val whereConditions = mutableListOf<String>()

        if (tags.isNotEmpty()) {
            val tagConditions = tags
                .mapIndexed { index, andTags ->
                    val tagsString = andTags.joinToString(", ") { "'$it'" }
                    "transaction_tags_$index.name IN ($tagsString)"
                }
                .joinToString(" AND ")

            whereConditions.add(tagConditions)
        }

        hash?.let { database.transactionDao().getTransaction(hash) }?.let { fromTransaction ->
            val fromCondition = """
                           (
                                tx.timestamp < ${fromTransaction.timestamp} OR 
                                (
                                    tx.timestamp = ${fromTransaction.timestamp} AND 
                                    LOWER(HEX(tx.hash)) < "${fromTransaction.hashString.lowercase()}"
                                )
                           )
                           """

            whereConditions.add(fromCondition)
        }

        val transactionTagJoinStatements = tags
            .mapIndexed { index, _ ->
                "INNER JOIN TransactionTag AS transaction_tags_$index ON tx.hash = transaction_tags_$index.hash"
            }
            .joinToString("\n")

        val whereClause = if (whereConditions.isNotEmpty()) "WHERE ${whereConditions.joinToString(" AND ")}" else ""
        val orderClause = "ORDER BY tx.timestamp DESC, HEX(tx.hash) DESC"
        val limitClause = limit?.let { "LIMIT $limit" } ?: ""

        val sqlQuery = """
                      SELECT tx.*
                      FROM `Transaction` as tx
                      $transactionTagJoinStatements
                      $whereClause
                      $orderClause
                      $limitClause
                      """

        return database.transactionDao().getTransactionsBefore(SimpleSQLiteQuery(sqlQuery))
    }

    fun getInternalTransactions(): List<InternalTransaction> {
        return database.transactionDao().getInternalTransactions()
    }

    fun getInternalTransactionsByHashes(hashes: List<ByteArray>): List<InternalTransaction> {
        return database.transactionDao().getInternalTransactionsByHashes(hashes)
    }

    fun saveInternalTransactions(transactions: List<InternalTransaction>) {
        database.transactionDao().insertInternalTransactions(transactions)
    }

    fun getTrc20Events(): List<Trc20EventRecord> {
        return database.transactionDao().getTrc20Events()
    }

    fun getTrc20EventsByHashes(hashes: List<ByteArray>): List<Trc20EventRecord> {
        return database.transactionDao().getTrc20EventsByHashes(hashes)
    }

    fun saveTrc20Events(trc20Events: List<Trc20EventRecord>) {
        database.transactionDao().insertTrc20Events(trc20Events)
    }

    fun saveTags(tags: List<TransactionTag>) {
        database.tagsDao().insert(tags)
    }

    fun markTransactionsAsProcessed() {
        database.transactionDao().markTransactionsAsProcessed()
    }

    fun getChainParameter(key: String): ChainParameter? {
        return database.chainParameterDao().getChainParameter(key)
    }

    fun saveChainParameters(chainParameters: List<ChainParameter>) {
        database.chainParameterDao().insert(chainParameters)
    }

    private fun trxBalanceId() = "TRX"
    private fun trc10BalanceId(assetId: String) = "TRC10|$assetId"
    private fun trc20BalanceId(contractAddress: String) = "TRC20|$contractAddress"

    private enum class TransactionSourceType(val id: String) {
        Native("native"), Contract("contract")
    }

}
