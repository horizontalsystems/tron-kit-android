package io.horizontalsystems.tronkit.sync

import android.util.Log
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
    private val syncTimer: SyncTimer,
    private val tronGridService: TronGridService,
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
                _blockHeightFlow.update { value }
            }
        }


    private val _syncStateFlow = MutableStateFlow(syncState)
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow

    private val _blockHeightFlow = MutableStateFlow(lastBlockHeight)
    val blockHeightFlow: StateFlow<Long> = _blockHeightFlow

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
        storage.saveLastBlockHeight(lastBlockHeight)

        this.lastBlockHeight = lastBlockHeight

        onUpdateLastBlockHeight(lastBlockHeight)
    }

    private fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        Log.e("e", "onUpdateLastBlockHeight: $lastBlockHeight")

        syncAccountInfo()
        syncTransactions()

        syncState = SyncState.Synced()
    }

    private fun syncTransactions() {
        //TODO("not implemented")
    }

    private fun syncAccountInfo() {
        //TODO("not implemented")
    }
}
