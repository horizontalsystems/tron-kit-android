package io.horizontalsystems.tronkit.sync

import io.horizontalsystems.tronkit.TronKit.SyncError
import io.horizontalsystems.tronkit.TronKit.SyncState
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.IHistoryProvider
import io.horizontalsystems.tronkit.transaction.TransactionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TransactionSyncer(
    private val historyProvider: IHistoryProvider,
    private val transactionManager: TransactionManager,
    private val storage: Storage,
    private val address: Address
) {
    companion object {
        private const val MAX_TRANSACTION_COUNT = 1000
    }

    private val syncing = AtomicBoolean(false)
    private var scope: CoroutineScope? = null

    var syncState: SyncState = SyncState.NotSynced(SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                _syncStateFlow.update { value }
            }
        }

    private val _syncStateFlow = MutableStateFlow(syncState)
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    fun stop() {
        syncState = SyncState.NotSynced(SyncError.NotStarted())
    }

    fun sync() {
        val scope = this.scope ?: return
        if (!syncing.compareAndSet(false, true)) return

        scope.launch {
            try {
                doSync()
            } finally {
                syncing.set(false)
            }
        }
    }

    private suspend fun doSync() {
        syncState = SyncState.Syncing()
        try {
            val transactionSyncTimestamp = storage.getTransactionSyncBlockTimestamp() ?: 0
            val contractTransactionSyncTimestamp = storage.getContractTransactionSyncBlockTimestamp() ?: 0
            val initial = transactionSyncTimestamp == 0L || contractTransactionSyncTimestamp == 0L

            syncTransactions(transactionSyncTimestamp)
            syncContractTransactions(contractTransactionSyncTimestamp)

            transactionManager.process(initial)

            syncState = SyncState.Synced()
        } catch (error: Throwable) {
            error.printStackTrace()
            syncState = SyncState.NotSynced(error)
        }
    }

    private suspend fun syncTransactions(syncBlockTimestamp: Long) {
        var cursor: String? = null
        var totalFetched = 0

        do {
            val (transactions, nextCursor) = historyProvider.fetchTransactions(
                address = address.base58,
                minTimestamp = syncBlockTimestamp,
                cursor = cursor
            )

            if (transactions.isNotEmpty()) {
                transactionManager.saveTransactionData(transactions, confirmed = true)
                storage.saveTransactionSyncTimestamp(transactions.last().block_timestamp)
            }

            totalFetched += transactions.size
            cursor = nextCursor
        } while (cursor != null && totalFetched < MAX_TRANSACTION_COUNT)
    }

    private suspend fun syncContractTransactions(syncBlockTimestamp: Long) {
        var cursor: String? = null
        var totalFetched = 0

        do {
            val (transactions, nextCursor) = historyProvider.fetchTrc20Transactions(
                address = address.base58,
                minTimestamp = syncBlockTimestamp,
                cursor = cursor
            )

            if (transactions.isNotEmpty()) {
                transactionManager.saveContractTransactionData(transactions, confirmed = true)
                storage.saveContractTransactionSyncTimestamp(transactions.last().block_timestamp)
            }

            totalFetched += transactions.size
            cursor = nextCursor
        } while (cursor != null && totalFetched < MAX_TRANSACTION_COUNT)
    }
}
