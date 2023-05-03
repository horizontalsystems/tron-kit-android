package io.horizontalsystems.tronkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionSyncState

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactionSyncState: TransactionSyncState)

    @Query("SELECT * FROM TransactionSyncState where id=:id")
    fun getTransactionSyncState(id: String): TransactionSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactions: List<Transaction>)

    @Query("SELECT * FROM `Transaction` ORDER BY blockTimestamp DESC")
    fun getTransactions(): List<Transaction>

}
