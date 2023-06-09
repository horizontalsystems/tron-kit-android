package io.horizontalsystems.tronkit

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.rpc.Trc20Provider
import io.horizontalsystems.tronkit.transaction.Fee
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.launch
import java.math.BigInteger

class MainViewModel(
    private val kit: TronKit,
    private val signer: Signer,
    private val trc20Provider: Trc20Provider
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

    fun trc20TokenInfoTest() {
        viewModelScope.launch {
            try {
                val decimals = trc20Provider.getDecimals(Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
                val symbol = trc20Provider.getTokenSymbol(Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
                val name = trc20Provider.getTokenName(Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))

                Log.e("e", "decimals = $decimals, symbol = $symbol, name = $name")
            } catch (error: Throwable) {
                Log.e("e", "trc20TokenInfoTest error", error)
                error.printStackTrace()
            }
        }
    }

}

class MainViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val network = Network.NileTestnet
        val apiKey = ""
        val words = " ".split(" ")
        val kit = TronKit.getInstance(App.instance, words, "", network, apiKey, "tron-demo-app")
        val seed = Mnemonic().toSeed(words)
        val signer = Signer.getInstance(seed, network)
        val trc20Provider = Trc20Provider.getInstance(network, apiKey)

        return MainViewModel(kit, signer, trc20Provider) as T
    }
}
