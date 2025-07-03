package io.horizontalsystems.tronkit.sample

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.rpc.Trc20Provider
import io.horizontalsystems.tronkit.transaction.Fee
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.Dispatchers
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
                val allTransactions = kit.getFullTransactionsBefore(emptyList(), null, null)
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
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val usdt = Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
                val decimals = trc20Provider.getDecimals(usdt)
                val symbol = trc20Provider.getTokenSymbol(usdt)
                val name = trc20Provider.getTokenName(usdt)

                Log.e("e", "decimals = $decimals, symbol = $symbol, name = $name")
            } catch (error: Throwable) {
                Log.e("e", "trc20TokenInfoTest error", error)
                error.printStackTrace()
            }
        }
    }

    fun trc20AllowanceTest() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val usdt = Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
                val sunSwapRouter = Address.fromBase58("TXF1xDbVGdxFGbovmmmXvBGu8ZiE3Lq4mR")

                val allowance = kit.getTrc20Allowance(usdt, sunSwapRouter)

                Log.e("e", "allowance: $allowance")

            } catch (error: Throwable) {
                Log.e("e", "trc20AllowanceTest error", error)
                error.printStackTrace()
            }
        }
    }

    fun trc20ApproveTest() {
        viewModelScope.launch(Dispatchers.Default) {
            val usdt = Address.fromBase58("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
            val sunSwapRouter = Address.fromBase58("TXF1xDbVGdxFGbovmmmXvBGu8ZiE3Lq4mR")

            val triggerSmartContract = kit.approveTrc20TriggerSmartContract(
                contract = usdt,
                spender = sunSwapRouter,
                amount = BigInteger.valueOf(37_000_000)
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
    }

    fun estimateFeeForCreatedTransaction() {
        viewModelScope.launch(Dispatchers.Default) {
            val createdTransactionJson = "{\n" +
                    "\"visible\": false,\n" +
                    "\"txID\": \"ff3ec6889b9ffff4cc80bd600a07a5281f0b9f9f7f3648da054a94d621494f79\",\n" +
                    "\"raw_data\": {\n" +
                    "\"contract\": [\n" +
                    "{\n" +
                    "\"parameter\": {\n" +
                    "\"value\": {\n" +
                    "\"data\": \"4cd480bd000000000000000000000000a614f803b6fd780986a42c78ec9c7f77e6ded13c00000000000000000000000000000000000000000000000000000000004c4b40000000000000000000000000c30b1fe97f5d82993b84b1ed25f8aa39983138f6000000000000000000000000000000000000000000000000000000000000000200000000000000000000000055d398326f99059ff775485246999027b3197955000000000000000000000000000000000000000000000000620c50bd716edaf200000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000066ea6\",\n" +
                    "\"owner_address\": \"41ce7ba9c4618bc93f00c6673f73042fc0a33f1b62\",\n" +
                    "\"contract_address\": \"410a38028ed6146aa29c687c052b233131468b6635\"\n" +
                    "},\n" +
                    "\"type_url\": \"type.googleapis.com/protocol.TriggerSmartContract\"\n" +
                    "},\n" +
                    "\"type\": \"TriggerSmartContract\"\n" +
                    "}\n" +
                    "],\n" +
                    "\"ref_block_bytes\": \"6358\",\n" +
                    "\"ref_block_hash\": \"a11ca6fc895e08d0\",\n" +
                    "\"expiration\": 1751525244000,\n" +
                    "\"fee_limit\": 150000000,\n" +
                    "\"timestamp\": 1751525186161\n" +
                    "},\n" +
                    "\"raw_data_hex\": \"0a0263582208a11ca6fc895e08d040e0f8a8f8fc325af002081f12eb020a31747970652e676f6f676c65617069732e636f6d2f70726f746f636f6c2e54726967676572536d617274436f6e747261637412b5020a1541ce7ba9c4618bc93f00c6673f73042fc0a33f1b621215410a38028ed6146aa29c687c052b233131468b66352284024cd480bd000000000000000000000000a614f803b6fd780986a42c78ec9c7f77e6ded13c00000000000000000000000000000000000000000000000000000000004c4b40000000000000000000000000c30b1fe97f5d82993b84b1ed25f8aa39983138f6000000000000000000000000000000000000000000000000000000000000000200000000000000000000000055d398326f99059ff775485246999027b3197955000000000000000000000000000000000000000000000000620c50bd716edaf200000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000066ea670f1b4a5f8fc32900180a3c347\"\n" +
                    "}"

            val gson = Gson()

            val createdTransaction: CreatedTransaction? = gson.fromJson(createdTransactionJson, CreatedTransaction::class.java)

            createdTransaction?.let {
                val fees = kit.estimateFee(it)
                Log.e("e", "fees: ${fees.size}")
                fees.forEach {
                    Log.e("e", "fee $it")
                }

                val feeLimit = fees.sumOf { it.feeInSuns }
                Log.e("e", "total feeLimit: $feeLimit ")

                val bandwidthFeeLimit = (fees.find { it is Fee.Bandwidth } as? Fee.Bandwidth)?.feeInSuns
                Log.e("e", "bandwidth feeLimit: $bandwidthFeeLimit ")

                val energyFeeLimit = (fees.find { it is Fee.Energy } as? Fee.Energy)?.feeInSuns
                Log.e("e", "energy feeLimit: $energyFeeLimit ")
            }
        }
    }

}

class MainViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val network = Network.Mainnet
        val apiKeys = listOf<String>()
        val words = " ".split(" ")
        val seed = Mnemonic().toSeed(words)
        val kit = TronKit.getInstance(App.instance, seed, network, apiKeys, "tron-demo-app")
        val signer = Signer.getInstance(seed, network)
        val trc20Provider = Trc20Provider.getInstance(network, apiKeys)

        return MainViewModel(kit, signer, trc20Provider) as T
    }
}
