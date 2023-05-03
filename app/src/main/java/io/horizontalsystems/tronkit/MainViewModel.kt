package io.horizontalsystems.tronkit

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.network.Network
import kotlinx.coroutines.launch

class MainViewModel(
    private val kit: TronKit
) : ViewModel() {

    var balance: String by mutableStateOf(kit.trxBalance.toBigDecimal().movePointLeft(6).toPlainString())
        private set

    var lastBlockHeight: Long by mutableStateOf(kit.lastBlockHeight)
        private set

    var syncState: TronKit.SyncState by mutableStateOf(kit.syncState)
        private set

    var transactions: List<Transaction> by mutableStateOf(listOf())
        private set

    init {
        viewModelScope.launch {
            kit.start()
        }

        viewModelScope.launch {
            kit.trxBalanceFlow.collect {
                balance = it.toBigDecimal().movePointLeft(6).toPlainString()
            }
        }

        viewModelScope.launch {
            kit.lastBlockHeightFlow.collect {
                lastBlockHeight = it
            }
        }

        viewModelScope.launch {
            kit.syncStateFlow.collect {
                syncState = it
            }
        }

        viewModelScope.launch {
            kit.transactionsFlow.collect {
                Log.e("e", "transactionsFlow: ${it.size}")
                transactions = it
            }
        }
    }
}

class MainViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val network = Network.Mainnet
        val apiKey = ""
        val words = "".split(" ")
        val kit = TronKit.getInstance(App.instance, words, "", network, apiKey, "tron-sample-wallet")

        return MainViewModel(kit) as T
    }
}
