package io.horizontalsystems.tronkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState
import io.horizontalsystems.tronkit.models.Trc20EventRecord

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactionSyncState: TransactionSyncState)

    @Query("SELECT * FROM TransactionSyncState where id=:id")
    fun getTransactionSyncState(id: String): TransactionSyncState?

    @Query("SELECT * FROM `Transaction` WHERE hash=:hash")
    fun getTransaction(hash: ByteArray): Transaction

    @Query("SELECT * FROM `Transaction` WHERE hash IN (:hashes)")
    fun getTransactions(hashes: List<ByteArray>): List<Transaction>

    @RawQuery
    suspend fun getTransactionsBefore(query: SupportSQLiteQuery): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(transactions: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransactionsIfNotExists(transactions: List<Transaction>)

    @Query("SELECT * FROM `Transaction` WHERE processed = 0 ORDER BY timestamp DESC")
    fun getUnprocessedTransactions(): List<Transaction>

    @Query("UPDATE `Transaction` SET processed = 1")
    fun markTransactionsAsProcessed()

    @Query("SELECT * FROM `Transaction` ORDER BY timestamp DESC")
    fun getTransactions(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInternalTransactions(transactions: List<InternalTransaction>)

    @Query("SELECT * FROM InternalTransaction ORDER BY timestamp DESC")
    fun getInternalTransactions(): List<InternalTransaction>

    @Query("SELECT * FROM `InternalTransaction` WHERE transactionHash IN (:hashes)")
    fun getInternalTransactionsByHashes(hashes: List<ByteArray>): List<InternalTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrc20Events(trc20Events: List<Trc20EventRecord>)

    @Query("SELECT * FROM Trc20EventRecord")
    fun getTrc20Events(): List<Trc20EventRecord>

    @Query("SELECT * FROM Trc20EventRecord WHERE transactionHash IN (:hashes)")
    fun getTrc20EventsByHashes(hashes: List<ByteArray>): List<Trc20EventRecord>
}
