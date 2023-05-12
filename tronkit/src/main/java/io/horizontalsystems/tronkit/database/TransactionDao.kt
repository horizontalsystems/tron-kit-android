package io.horizontalsystems.tronkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState
import io.horizontalsystems.tronkit.models.Trc20Event

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactionSyncState: TransactionSyncState)

    @Query("SELECT * FROM TransactionSyncState where id=:id")
    fun getTransactionSyncState(id: String): TransactionSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(transactions: List<Transaction>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransactionsIfNotExists(transactions: List<Transaction>)

    @Query("SELECT * FROM `Transaction` ORDER BY timestamp DESC")
    fun getTransactions(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInternalTransactions(transactions: List<InternalTransaction>)

    @Query("SELECT * FROM InternalTransaction ORDER BY timestamp DESC")
    fun getInternalTransactions(): List<InternalTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrc20Events(trc20Events: List<Trc20Event>)

    @Query("SELECT * FROM Trc20Event")
    fun getTrc20Events(): List<Trc20Event>
}
