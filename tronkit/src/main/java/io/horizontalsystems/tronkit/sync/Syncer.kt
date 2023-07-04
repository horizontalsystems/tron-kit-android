package io.horizontalsystems.tronkit.sync

import io.horizontalsystems.tronkit.TronKit.SyncError
import io.horizontalsystems.tronkit.TronKit.SyncState
import io.horizontalsystems.tronkit.account.AccountInfoManager
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.TronGridService
import io.horizontalsystems.tronkit.network.TronGridService.TronGridServiceError
import io.horizontalsystems.tronkit.transaction.TransactionManager
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

    companion object {
        private const val onlyConfirmed = true
        private const val limit = 200
        private const val orderBy = "block_timestamp,asc"
    }

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

    fun refresh() {
        when (syncTimer.state) {
            SyncTimer.State.Ready -> {
                sync()
            }

            is SyncTimer.State.NotReady -> {
                scope?.let { syncTimer.start(this, it) }
            }
        }
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
        try {
            val lastBlockHeight = tronGridService.getBlockHeight()

            if (this.lastBlockHeight == lastBlockHeight) return

            storage.saveLastBlockHeight(lastBlockHeight)

            this.lastBlockHeight = lastBlockHeight

            onUpdateLastBlockHeight(lastBlockHeight)
        } catch (error: Throwable) {
            error.printStackTrace()
            syncState = SyncState.NotSynced(error)
        }
    }

    private suspend fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        val transactionSyncTimestamp = storage.getTransactionSyncBlockTimestamp() ?: 0
        val contractTransactionSyncTimestamp = storage.getContractTransactionSyncBlockTimestamp() ?: 0
        val initial = transactionSyncTimestamp == 0L || contractTransactionSyncTimestamp == 0L

        syncAccountInfo()
        syncTransactions(transactionSyncTimestamp)
        syncContractTransactions(contractTransactionSyncTimestamp)

        transactionManager.process(initial)

        syncState = SyncState.Synced()
    }

    private suspend fun syncAccountInfo() {
        try {
            val accountInfo = tronGridService.getAccountInfo(address.base58)
            accountInfoManager.handle(accountInfo)
        } catch (error: TronGridServiceError.NoAccountInfoData) {
            accountInfoManager.handleInactiveAccount()
        }
    }

    private suspend fun syncTransactions(syncBlockTimestamp: Long) {
        var fingerprint: String? = null
        do {
            val response = tronGridService.getTransactions(
                address = address.base58,
                startBlockTimestamp = syncBlockTimestamp + 1000,
                fingerprint = fingerprint,
                onlyConfirmed = onlyConfirmed,
                limit = limit,
                orderBy = orderBy
            )
            val transactionData = response.first
            fingerprint = response.second

            if (transactionData.isNotEmpty()) {
                transactionManager.saveTransactionData(transactionData, onlyConfirmed)

                storage.saveTransactionSyncTimestamp(transactionData.last().block_timestamp)
            }
        } while (fingerprint != null)
    }

    private suspend fun syncContractTransactions(syncBlockTimestamp: Long) {
        var fingerprint: String? = null
        do {
            val response = tronGridService.getContractTransactions(
                address = address.base58,
                startBlockTimestamp = syncBlockTimestamp + 1000,
                fingerprint = fingerprint,
                onlyConfirmed = onlyConfirmed,
                limit = limit,
                orderBy = orderBy
            )
            val transactionData = response.first
            fingerprint = response.second

            if (transactionData.isNotEmpty()) {
                transactionManager.saveContractTransactionData(transactionData, onlyConfirmed)

                storage.saveContractTransactionSyncTimestamp(transactionData.last().block_timestamp)
            }
        } while (fingerprint != null)
    }
}
