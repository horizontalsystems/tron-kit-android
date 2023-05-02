package io.horizontalsystems.tronkit.database

import io.horizontalsystems.tronkit.models.Balance
import io.horizontalsystems.tronkit.models.LastBlockHeight
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

    private fun trxBalanceId() = "TRX"
    private fun trc10BalanceId(assetId: String) = "TRC10|$assetId"
    private fun trc20BalanceId(contractAddress: String) = "TRC20|$contractAddress"
}
