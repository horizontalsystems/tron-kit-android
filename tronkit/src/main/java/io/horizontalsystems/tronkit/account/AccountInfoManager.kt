package io.horizontalsystems.tronkit.account

import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.models.Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger

class AccountInfoManager(
    private val storage: Storage
) {

    // In-memory watched tokens; re-registered on each app launch by the wallet adapter
    private val watchedTokens = mutableSetOf<Address>()

    var accountActive: Boolean = true
        private set(value) {
            if (value != field) {
                field = value
                _accountActiveFlow.update { value }
            }
        }

    private val _accountActiveFlow = MutableStateFlow(accountActive)
    val accountActiveFlow: StateFlow<Boolean> = _accountActiveFlow

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

    fun watchTrc20(contractAddress: Address) {
        watchedTokens.add(contractAddress)
    }

    fun trc20AddressesToSync(): List<Address> {
        val knownFromDb = storage.allTrc20Addresses()
            .mapNotNull { runCatching { Address.fromHex(it) }.getOrNull() }
            .toSet()
        return (knownFromDb + watchedTokens).toList()
    }

    fun getTrc20Balance(contractAddress: String): BigInteger {
        return storage.getTrc20Balance(contractAddress) ?: BigInteger.ZERO
    }

    fun getTrc20BalanceFlow(contractAddress: String): StateFlow<BigInteger> =
        _trc20BalancesMap.getOrPut(contractAddress) {
            MutableStateFlow(BigInteger.ZERO)
        }

    // Called when account info comes from IHistoryProvider
    fun handle(accountInfo: AccountInfo) {
        accountActive = true
        storage.saveTrxBalance(accountInfo.balance)
        storage.clearTrc20Balances()
        accountInfo.trc20Balances.forEach { trc20Balance ->
            storage.saveTrc20Balance(trc20Balance.balance, trc20Balance.contractAddress)
            updateTrc20Flow(trc20Balance.contractAddress, trc20Balance.balance)
        }
        trxBalance = accountInfo.balance
    }

    // Called when TRX balance comes from INodeApiProvider (RPC fallback path)
    fun handle(trxBalance: BigInteger) {
        accountActive = true
        storage.saveTrxBalance(trxBalance)
        this.trxBalance = trxBalance
    }

    // Called when a single TRC20 balance comes from RPC balanceOf call
    fun handle(trc20Balance: BigInteger, contractAddress: Address) {
        storage.saveTrc20Balance(trc20Balance, contractAddress.hex)
        updateTrc20Flow(contractAddress.hex, trc20Balance)
    }

    fun handleInactiveAccount() {
        accountActive = false
        trxBalance = BigInteger.ZERO
    }

    private fun updateTrc20Flow(contractAddress: String, balance: BigInteger) {
        val flow = _trc20BalancesMap[contractAddress]
        if (flow != null) {
            flow.update { balance }
        } else {
            _trc20BalancesMap[contractAddress] = MutableStateFlow(balance)
        }
    }
}
