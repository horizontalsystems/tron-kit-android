package io.horizontalsystems.tronkit.account

import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.AccountInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger

class AccountInfoManager(
    private val storage: Storage
) {
    var trxBalance: BigInteger = storage.getTrxBalance() ?: BigInteger.ZERO
        private set(value) {
            if (value != field) {
                field = value
                _trxBalanceFlow.update { value }
            }
        }

    private val _trxBalanceFlow = MutableStateFlow(trxBalance)
    val trxBalanceFlow: StateFlow<BigInteger> = _trxBalanceFlow

    fun handle(accountInfo: AccountInfo) {
        storage.saveTrxBalance(accountInfo.balance)

        trxBalance = accountInfo.balance
    }
}
