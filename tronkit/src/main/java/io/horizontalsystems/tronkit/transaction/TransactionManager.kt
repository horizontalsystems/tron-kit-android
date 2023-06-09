package io.horizontalsystems.tronkit.transaction

import android.util.Log
import com.google.gson.Gson
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.decoration.DecorationManager
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransactionTag
import io.horizontalsystems.tronkit.models.Trc20EventRecord
import io.horizontalsystems.tronkit.network.ContractRaw
import io.horizontalsystems.tronkit.network.ContractTransactionData
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.InternalTransactionData
import io.horizontalsystems.tronkit.network.RegularTransactionData
import io.horizontalsystems.tronkit.network.TransactionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.math.BigInteger

class TransactionManager(
    private val userAddress: Address,
    private val storage: Storage,
    private val decorationManager: DecorationManager,
    private val gson: Gson
) {
    private val _transactionsFlow = MutableStateFlow<Pair<List<FullTransaction>, Boolean>>(Pair(listOf(), false))
    val transactionsFlow: StateFlow<Pair<List<FullTransaction>, Boolean>> = _transactionsFlow

    private val _transactionsWithTagsFlow = MutableStateFlow<List<TransactionWithTags>>(listOf())
    val transactionsWithTagsFlow: StateFlow<List<TransactionWithTags>> = _transactionsWithTagsFlow

    fun getFullTransactionsFlow(tags: List<List<String>>): Flow<List<FullTransaction>> {
        return _transactionsWithTagsFlow.map { transactions ->
            transactions.mapNotNull { transactionWithTags ->
                for (andTags in tags) {
                    if (transactionWithTags.tags.all { !andTags.contains(it) }) {
                        return@mapNotNull null
                    }
                }
                return@mapNotNull transactionWithTags.transaction
            }
        }.filter { it.isNotEmpty() }
    }

    suspend fun getFullTransactions(tags: List<List<String>>, fromHash: ByteArray? = null, limit: Int? = null): List<FullTransaction> {
        val transactions = storage.getTransactionsBefore(tags, fromHash, limit)
        return decorationManager.decorateTransactions(transactions)
    }

    fun getFullTransactions(hashes: List<ByteArray>): List<FullTransaction> {
        val transactions = storage.getTransactions(hashes)
        return decorationManager.decorateTransactions(transactions)
    }

    fun handle(createdTransaction: CreatedTransaction) {
        val transaction = Transaction(
            hash = createdTransaction.txID.hexStringToByteArray(),
            timestamp = createdTransaction.raw_data.timestamp,
            contractsRaw = gson.toJson(createdTransaction.raw_data.contract),
            confirmed = false
        )
        storage.saveTransactions(listOf(transaction))

        process(false)
    }

    fun process(initial: Boolean) {
        val transactions = storage.getUnprocessedTransactions()

        if (transactions.isEmpty()) return

        val fullTransactions = decorationManager.decorateTransactions(transactions)

        val transactionWithTags = mutableListOf<TransactionWithTags>()
        val allTags: MutableList<TransactionTag> = mutableListOf()

        fullTransactions.forEach { fullTransaction ->
            val tags = fullTransaction.decoration.tags(userAddress).map { TransactionTag(it, fullTransaction.transaction.hash) }
            allTags.addAll(tags)
            transactionWithTags.add(TransactionWithTags(fullTransaction, tags.map { it.name }))
        }

        storage.saveTags(allTags)

        _transactionsFlow.tryEmit(Pair(fullTransactions, initial))
        _transactionsWithTagsFlow.tryEmit(transactionWithTags)

        storage.markTransactionsAsProcessed()
    }

    fun saveTransactionData(transactionData: List<TransactionData>, confirmed: Boolean) {
        val transactions = mutableListOf<Transaction>()
        val internalTransactions = mutableListOf<InternalTransaction>()

        transactionData.forEach { txData ->
            try {
                when (txData) {
                    is InternalTransactionData -> {
                        val callValueDouble = (txData.data["call_value"] as? Map<String, Any>)?.get("_") as? Double
                        val value = callValueDouble?.toBigDecimal()?.toBigInteger()

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
                                        timestamp = txData.block_timestamp,
                                        confirmed = confirmed
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
                                contractsRaw = gson.toJson(contractsWithWithdrawAmount(txData)),
                                confirmed = confirmed
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                Log.w("e", "TransactionData parsing error", error)
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

    fun saveContractTransactionData(transactionData: List<ContractTransactionData>, confirmed: Boolean) {
        val trc20Events = mutableListOf<Trc20EventRecord>()
        val transactions = mutableListOf<Transaction>()

        transactionData.forEach {
            try {
                transactions.add(
                    Transaction(
                        hash = it.transaction_id.hexStringToByteArray(),
                        timestamp = it.block_timestamp,
                        confirmed = confirmed
                    )
                )

                trc20Events.add(
                    Trc20EventRecord(
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
                Log.w("e", "Contract TransactionData parsing error: ${it.transaction_id}", error)
            }
        }

        storage.saveTrc20Events(trc20Events)
        storage.saveTransactionsIfNotExists(transactions)
    }

    data class TransactionWithTags(
        val transaction: FullTransaction,
        val tags: List<String>
    )
}