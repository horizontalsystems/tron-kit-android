package io.horizontalsystems.tronkit.account

import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.models.Trc20Balance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger

class AccountInfoManager(
    private val storage: Storage
) {
    var isAccountActive: Boolean = true
        private set

    var trxBalance: BigInteger = storage.getTrxBalance() ?: BigInteger.ZERO
        private set(value) {
            if (value != field) {
                field = value
                _trxBalanceFlow.update { value }
            }
        }

    private val _trxBalanceFlow = MutableStateFlow(trxBalance)
    val trxBalanceFlow: StateFlow<BigInteger> = _trxBalanceFlow

    private val _trc20BalancesMap = mutableMapOf<String, MutableStateFlow<BigInteger>>()

    fun getTrc20Balance(contractAddress: String): BigInteger {
        return storage.getTrc20Balance(contractAddress) ?: BigInteger.ZERO
    }

    fun getTrc20BalanceFlow(contractAddress: String): StateFlow<BigInteger> =
        when (val trc20BalanceFlow = _trc20BalancesMap[contractAddress]) {
            null -> {
                MutableStateFlow(BigInteger.ZERO).also {
                    _trc20BalancesMap[contractAddress] = it
                }
            }

            else -> {
                trc20BalanceFlow
            }
        }

    fun handle(accountInfo: AccountInfo) {
        storage.saveBalances(
            trxBalance = accountInfo.balance,
            balances = accountInfo.trc20Balances
        )

        accountInfo.trc20Balances.forEach { trc20Balance ->
            val trc20BalanceFlow = _trc20BalancesMap[trc20Balance.contractAddress]
            if (trc20BalanceFlow != null) {
                trc20BalanceFlow.update { trc20Balance.balance }
            } else {
                _trc20BalancesMap[trc20Balance.contractAddress] = MutableStateFlow(trc20Balance.balance)
            }
        }

        trxBalance = accountInfo.balance
    }

    fun handleInactiveAccount() {
        isAccountActive = false
    }
}
