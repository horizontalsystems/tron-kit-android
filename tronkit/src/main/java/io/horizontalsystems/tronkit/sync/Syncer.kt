package io.horizontalsystems.tronkit.sync

import android.util.Log
import io.horizontalsystems.tronkit.Address
import io.horizontalsystems.tronkit.TronKit.SyncError
import io.horizontalsystems.tronkit.TronKit.SyncState
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.network.TronGridService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Syncer(
    private val address: Address,
    private val syncTimer: SyncTimer,
    private val tronGridService: TronGridService,
    private val accountInfoManager: AccountInfoManager,
    private val transactionManager: TransactionManager,
    private val storage: Storage
) : SyncTimer.Listener {

    private var scope: CoroutineScope? = null

    var syncState: SyncState = SyncState.NotSynced(SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                _syncStateFlow.update { value }
            }
        }

    var lastBlockHeight: Long = storage.getLastBlockHeight() ?: 0
        private set(value) {
            if (value != field) {
                field = value
                _lastBlockHeightFlow.update { value }
            }
        }


    private val _syncStateFlow = MutableStateFlow(syncState)
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow

    private val _lastBlockHeightFlow = MutableStateFlow(lastBlockHeight)
    val lastBlockHeightFlow: StateFlow<Long> = _lastBlockHeightFlow

    fun start(scope: CoroutineScope) {
        this.scope = scope

        syncTimer.start(this, scope)
    }

    fun stop() {
        syncState = SyncState.NotSynced(SyncError.NotStarted())

        syncTimer.stop()
    }

    override fun onUpdateSyncTimerState(state: SyncTimer.State) {
        syncState = when (state) {
            is SyncTimer.State.NotReady -> {
                SyncState.NotSynced(state.error)
            }

            SyncTimer.State.Ready -> {
                SyncState.Syncing()
            }
        }
    }

    override fun sync() {
        scope?.launch {
            syncLastBlockHeight()
        }
    }

    private suspend fun syncLastBlockHeight() {
        val lastBlockHeight = tronGridService.getBlockHeight()

        if (this.lastBlockHeight == lastBlockHeight) return

        storage.saveLastBlockHeight(lastBlockHeight)

        this.lastBlockHeight = lastBlockHeight

        onUpdateLastBlockHeight(lastBlockHeight)
    }

    private suspend fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        Log.e("e", "onUpdateLastBlockHeight: $lastBlockHeight")

        try {
            val transactionSyncTimestamp = storage.getTransactionSyncBlockTimestamp() ?: 0
            val contractTransactionSyncTimestamp = storage.getContractTransactionSyncBlockTimestamp() ?: 0

            syncAccountInfo()
            syncTransactions(transactionSyncTimestamp)
            syncContractTransactions(contractTransactionSyncTimestamp)

            transactionManager.process(initial = transactionSyncTimestamp == 0L || contractTransactionSyncTimestamp == 0L)

            syncState = SyncState.Synced()

        } catch (error: Throwable) {
            error.printStackTrace()
            syncState = SyncState.NotSynced(error)
        }
    }

    private suspend fun syncAccountInfo() {
        val accountInfo = tronGridService.getAccountInfo(address.base58)
        accountInfoManager.handle(accountInfo)
    }

    private suspend fun syncTransactions(syncBlockTimestamp: Long) {
        Log.e("e", "syncTransactions() syncBlockTimestamp: $syncBlockTimestamp")

        var fingerprint: String? = null
        do {
            val response = tronGridService.getTransactions(address.base58, syncBlockTimestamp + 1000, fingerprint)
            val transactionData = response.first
            fingerprint = response.second

            if (transactionData.isNotEmpty()) {
                transactionManager.saveTransactionData(transactionData)

                storage.saveTransactionSyncTimestamp(transactionData.last().block_timestamp)
            }
        } while (fingerprint != null)
    }

    private suspend fun syncContractTransactions(syncBlockTimestamp: Long) {
        Log.e("e", "syncContractTransactions() syncBlockTimestamp: $syncBlockTimestamp")

        var fingerprint: String? = null
        do {
            val response = tronGridService.getContractTransactions(address.base58, syncBlockTimestamp + 1000, fingerprint)
            val transactionData = response.first
            fingerprint = response.second

            if (transactionData.isNotEmpty()) {
                transactionManager.saveContractTransactionData(transactionData)

                storage.saveContractTransactionSyncTimestamp(transactionData.last().block_timestamp)
            }
        } while (fingerprint != null)
    }
}
