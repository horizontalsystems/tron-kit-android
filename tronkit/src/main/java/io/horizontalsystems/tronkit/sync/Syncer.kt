package io.horizontalsystems.tronkit.sync

import io.horizontalsystems.tronkit.TronKit.SyncError
import io.horizontalsystems.tronkit.TronKit.SyncState
import io.horizontalsystems.tronkit.account.AccountInfoManager
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.IHistoryProvider
import io.horizontalsystems.tronkit.network.INodeApiProvider
import io.horizontalsystems.tronkit.network.IRpcApiProvider
import io.horizontalsystems.tronkit.rpc.BlockNumberJsonRpc
import io.horizontalsystems.tronkit.rpc.CallJsonRpc
import io.horizontalsystems.tronkit.rpc.DefaultBlockParameter
import io.horizontalsystems.tronkit.contracts.trc20.BalanceOfMethod
import io.horizontalsystems.tronkit.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean

class Syncer(
    private val address: Address,
    private val syncTimer: SyncTimer,
    private val rpcApiProvider: IRpcApiProvider,
    private val nodeApiProvider: INodeApiProvider,
    private val historyProvider: IHistoryProvider?,
    private val accountInfoManager: AccountInfoManager,
    private val chainParameterManager: ChainParameterManager,
    private val storage: Storage,
) : SyncTimer.Listener {

    private val syncing = AtomicBoolean(false)
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
        scope.launch { chainParameterManager.sync() }
        syncTimer.start(this, scope)
    }

    fun stop() {
        syncState = SyncState.NotSynced(SyncError.NotStarted())
        syncTimer.stop()
    }

    fun pause() {
        syncTimer.pause()
    }

    fun resume() {
        syncTimer.resume()
    }

    fun refresh() {
        when (syncTimer.state) {
            SyncTimer.State.Ready -> sync()
            is SyncTimer.State.NotReady -> scope?.let { syncTimer.start(this, it) }
        }
    }

    override fun onUpdateSyncTimerState(state: SyncTimer.State) {
        syncState = when (state) {
            is SyncTimer.State.NotReady -> SyncState.NotSynced(state.error)
            SyncTimer.State.Ready -> SyncState.Syncing()
        }
    }

    override fun sync() {
        if (!syncing.compareAndSet(false, true)) return

        scope?.launch {
            try {
                syncBlockHeight()
            } finally {
                syncing.set(false)
            }
        }
    }

    private suspend fun syncBlockHeight() {
        try {
            val blockHeight = rpcApiProvider.fetch(BlockNumberJsonRpc())

            if (this.lastBlockHeight == blockHeight) {
                syncState = SyncState.Synced()
                return
            }

            storage.saveLastBlockHeight(blockHeight)
            this.lastBlockHeight = blockHeight
            onNewBlockHeight()
        } catch (error: Throwable) {
            error.printStackTrace()
            syncState = SyncState.NotSynced(error)
        }
    }

    private suspend fun onNewBlockHeight() {
        if (historyProvider != null) {
            try {
                syncAccountViaHistory(historyProvider)
            } catch (_: Throwable) {
                syncAccountViaRpc()
            }
        } else {
            syncAccountViaRpc()
        }

        syncState = SyncState.Synced()
    }

    private suspend fun syncAccountViaHistory(provider: IHistoryProvider) {
        try {
            val accountInfo = provider.fetchAccountInfo(address.base58)
            accountInfoManager.handle(accountInfo)

            // For watched tokens absent from the response, explicitly store zero
            for (tokenAddress in accountInfoManager.trc20AddressesToSync()) {
                if (accountInfo.trc20Balances.none { it.contractAddress == tokenAddress.hex }) {
                    accountInfoManager.handle(BigInteger.ZERO, tokenAddress)
                }
            }
        } catch (_: IHistoryProvider.RequestError.FailedToFetchAccountInfo) {
            accountInfoManager.handleInactiveAccount()
        }
    }

    private suspend fun syncAccountViaRpc() {
        val account = nodeApiProvider.fetchAccount(address.hex)
        if (account == null) {
            accountInfoManager.handleInactiveAccount()
            return
        }

        accountInfoManager.handle(account.balance)

        for (tokenAddress in accountInfoManager.trc20AddressesToSync()) {
            try {
                val methodData = BalanceOfMethod(address).encodedABI()
                val rpc = CallJsonRpc(
                    contractAddress = "0x${tokenAddress.hex}",
                    data = methodData.toHexString(),
                    defaultBlockParameter = DefaultBlockParameter.Latest.raw
                )
                val response = rpcApiProvider.fetch(rpc)
                if (response.size >= 32) {
                    val balance = BigInteger(1, response.sliceArray(0..31))
                    accountInfoManager.handle(balance, tokenAddress)
                }
            } catch (_: Exception) {
                // Skip failed balanceOf calls
            }
        }
    }
}
