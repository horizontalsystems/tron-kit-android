package io.horizontalsystems.tronkit.sync

import android.util.Log
import com.google.gson.Gson
import io.horizontalsystems.tronkit.Address
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.Trc20Event
import io.horizontalsystems.tronkit.network.ContractRaw
import io.horizontalsystems.tronkit.network.ContractTransactionData
import io.horizontalsystems.tronkit.network.InternalTransactionData
import io.horizontalsystems.tronkit.network.RegularTransactionData
import io.horizontalsystems.tronkit.network.TransactionData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigInteger

class TransactionManager(
    private val storage: Storage,
    private val gson: Gson
) {
    private val _transactionsFlow = MutableStateFlow(storage.getTransactions())
    val transactionsFlow: StateFlow<List<Transaction>> = _transactionsFlow

    fun process() {
//        storage.getTransactions()

        _transactionsFlow.tryEmit(storage.getTransactions())
    }

    fun saveTransactionData(transactionData: List<TransactionData>) {
        Log.e("e", "TransactionManager handleTransactions(): ${transactionData.size}")

        val transactions = mutableListOf<Transaction>()
        val internalTransactions = mutableListOf<InternalTransaction>()

        transactionData.forEach { txData ->
            try {
                when (txData) {
                    is InternalTransactionData -> {
                        val callValueDouble = (txData.data["call_value"] as? Map<String, Any>)?.get("_") as? Double
                        val value = callValueDouble?.toBigDecimal()?.toBigInteger()?.toLong()
                        Log.e("e", "internal call_value: $value")

                        if (value != null) {
                            internalTransactions.add(
                                InternalTransaction(
                                    transactionHash = txData.tx_id.hexStringToByteArray(),
                                    timestamp = txData.block_timestamp,
                                    from = Address.fromHex(txData.from_address),
                                    to = Address.fromHex(txData.to_address),
                                    value = value,
                                    internalTxId = txData.internal_tx_id
                                )
                            )

                            storage.saveTransactionsIfNotExists(
                                listOf(
                                    Transaction(
                                        hash = txData.tx_id.hexStringToByteArray(),
                                        timestamp = txData.block_timestamp
                                    )
                                )
                            )
                        }
                    }

                    is RegularTransactionData -> {
                        val isFailed = txData.ret.any { !it.contractRet.equals("SUCCESS", ignoreCase = true) }
                        transactions.add(
                            Transaction(
                                hash = txData.txID.hexStringToByteArray(),
                                isFailed = isFailed,
                                blockNumber = txData.blockNumber,
                                timestamp = txData.block_timestamp,
                                fee = txData.ret.firstOrNull()?.fee,
                                netUsage = txData.net_usage,
                                netFee = txData.net_fee,
                                energyUsage = txData.energy_usage,
                                energyFee = txData.energy_fee,
                                energyUsageTotal = txData.energy_usage_total,
                                contractsRaw = gson.toJson(contractsWithWithdrawAmount(txData))
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                Log.e("e", "TransactionData parsing error", error)
            }
        }

        storage.saveInternalTransactions(internalTransactions)
        storage.saveTransactions(transactions)
    }

    private fun contractsWithWithdrawAmount(txData: RegularTransactionData): List<ContractRaw> {
        return if (txData.withdraw_amount == null) {
            txData.raw_data.contract
        } else {
            txData.raw_data.contract.map { contract ->
                val parameter = contract.parameter
                val value = parameter.value
                contract.copy(
                    parameter = parameter.copy(
                        value = value.copy(
                            withdraw_amount = txData.withdraw_amount
                        )
                    )
                )
            }
        }
    }

    fun saveContractTransactionData(transactionData: List<ContractTransactionData>) {
        Log.e("e", "TransactionManager handleContractTransactions(): ${transactionData.size}")

        //TODO handle TRC721 transactions
        val trc20Events = mutableListOf<Trc20Event>()
        val transactions = mutableListOf<Transaction>()

        transactionData.forEach {
            try {
                transactions.add(
                    Transaction(
                        hash = it.transaction_id.hexStringToByteArray(),
                        timestamp = it.block_timestamp
                    )
                )

                trc20Events.add(
                    Trc20Event(
                        it.transaction_id.hexStringToByteArray(),
                        it.block_timestamp,
                        contractAddress = Address.fromBase58(it.token_info.address),
                        from = Address.fromBase58(it.from),
                        to = Address.fromBase58(it.to),
                        value = BigInteger(it.value),
                        type = it.type,
                        tokenName = it.token_info.name,
                        tokenSymbol = it.token_info.symbol,
                        tokenDecimal = it.token_info.decimals
                    )
                )
            } catch (error: Throwable) {
                Log.e("e", "Contract TransactionData parsing error: ${it.transaction_id}", error)
            }
        }

        storage.saveTrc20Events(trc20Events)
        storage.saveTransactionsIfNotExists(transactions)
    }

}