package io.horizontalsystems.tronkit

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.network.Network
import kotlinx.coroutines.launch
import java.math.BigInteger

class MainViewModel(
    private val kit: TronKit,
    private val signer: Signer
) : ViewModel() {

    var balance: String by mutableStateOf(kit.trxBalance.toBigDecimal().movePointLeft(6).toPlainString())
        private set

    var lastBlockHeight: Long by mutableStateOf(kit.lastBlockHeight)
        private set

    var syncState: TronKit.SyncState by mutableStateOf(kit.syncState)
        private set

    var transactions: List<FullTransaction> by mutableStateOf(listOf())
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
                val allTransactions = kit.getFullTransactions(emptyList(), null, null)
                Log.e("e", "onTxUpdate, allTransactions: ${allTransactions.size}")
                transactions = allTransactions
            }
        }
    }

    fun sendTrxTest() {
        viewModelScope.launch {
            try {
                val transferContract = TransferContract(
                    amount = BigInteger.valueOf(890_000),
                    ownerAddress = kit.address,
                    toAddress = Address.fromBase58("TDoRr9CQsGoVb66CJRBsaWbBLRaZmLpfMr")
                )

                val fees = kit.estimateFee(transferContract)
                Log.e("e", "fees: ${fees.size}")
                fees.forEach {
                    Log.e("e", "fee $it")
                }

                val sendResult = kit.send(transferContract, signer)
                Log.e("e", "sendResult: $sendResult")

            } catch (error: Throwable) {
                Log.e("e", "send tx error", error)
                error.printStackTrace()
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

        return MainViewModel(kit, signer) as T
    }
}
