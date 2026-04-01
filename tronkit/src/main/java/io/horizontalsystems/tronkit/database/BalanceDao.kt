package io.horizontalsystems.tronkit.database

import androidx.room.*
import io.horizontalsystems.tronkit.models.Balance

@Dao
interface BalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: Balance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balances: List<Balance>)

    @Query("DELETE FROM Balance")
    fun deleteAll()

    @Query("DELETE FROM Balance WHERE id LIKE 'TRC20|%'")
    fun deleteTrc20Balances()

    @Query("SELECT * FROM Balance")
    fun getAll(): List<Balance>

    @Query("SELECT id FROM Balance WHERE id LIKE 'TRC20|%'")
    fun getTrc20Ids(): List<String>

    @Query("SELECT * FROM Balance where id=:id")
    fun getBalance(id: String): Balance?
}
