package io.horizontalsystems.tronkit.sync

import android.util.Log
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TransactionManager(
    private val storage: Storage
) {

    private val _transactionsFlow = MutableStateFlow(storage.getTransactions())
    val transactionsFlow: StateFlow<List<Transaction>> = _transactionsFlow

    fun handle(transactions: List<Transaction>) {
        Log.e("e", "TransactionManager handle(): ${transactions.size}")

        storage.saveTransactions(transactions)

        _transactionsFlow.tryEmit(storage.getTransactions())
    }

}