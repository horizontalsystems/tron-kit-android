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

    private suspend fun sendTrx() {
        val transferContract = kit.transferContract(
            amount = BigInteger.valueOf(890_000),
            toAddress = Address.fromBase58("TDoRr9CQsGoVb66CJRBsaWbBLRaZmLpfMr")
        )

        val fees = kit.estimateFee(transferContract)
        Log.e("e", "fees: ${fees.size}")
        fees.forEach {
            Log.e("e", "fee $it")
        }

        val feeLimit = fees.sumOf { it.feeInSuns }
        Log.e("e", "total feeLimit: $feeLimit ")

        val sendResult = kit.send(
            contract = transferContract,
            signer = signer
        )

        Log.e("e", "sendResult: $sendResult")
    }

    private suspend fun sendTrc20() {
        val triggerSmartContract = kit.transferTrc20TriggerSmartContract(
            contractAddress = Address.fromBase58("TXLAQ63Xg1NAzckPwKHvzw7CSEmLMEqcdj"),
            toAddress = Address.fromBase58("TDoRr9CQsGoVb66CJRBsaWbBLRaZmLpfMr"),
            amount = BigInteger.valueOf(12_000_000)
        )

        val fees = kit.estimateFee(triggerSmartContract)
        Log.e("e", "fees: ${fees.size}")
        fees.forEach {
            Log.e("e", "fee $it")
        }

        val feeLimit = fees.sumOf { it.feeInSuns }
        Log.e("e", "total feeLimit: $feeLimit ")

        val energyFeeLimit = (fees.find { it is Fee.Energy } as? Fee.Energy)?.feeInSuns
        Log.e("e", "energy feeLimit: $energyFeeLimit ")

        val sendResult = kit.send(
            contract = triggerSmartContract,
            signer = signer,
            feeLimit = energyFeeLimit
        )
        Log.e("e", "sendResult: $sendResult")
    }

    fun sendTrxTest() {
        viewModelScope.launch {
            try {
                sendTrc20()
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
