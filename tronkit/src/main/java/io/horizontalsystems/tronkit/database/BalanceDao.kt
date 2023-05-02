package io.horizontalsystems.tronkit.database

import androidx.room.*
import io.horizontalsystems.tronkit.models.Balance

@Dao
interface BalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: Balance)

    @Query("SELECT * FROM Balance where id=:id")
    fun getBalance(id: String): Balance?
}
