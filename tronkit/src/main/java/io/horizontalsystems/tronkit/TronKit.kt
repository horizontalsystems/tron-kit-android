package io.horizontalsystems.tronkit

import android.app.Application
import com.google.gson.Gson
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.tronkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.tronkit.database.MainDatabase
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.decoration.DecorationManager
import io.horizontalsystems.tronkit.decoration.trc20.Trc20TransactionDecorator
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.network.ConnectionManager
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.network.TronGridService
import io.horizontalsystems.tronkit.sync.AccountInfoManager
import io.horizontalsystems.tronkit.sync.SyncTimer
import io.horizontalsystems.tronkit.sync.Syncer
import io.horizontalsystems.tronkit.sync.TransactionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.Security
import java.util.Objects

class TronKit(
    private val syncer: Syncer,
    private val accountInfoManager: AccountInfoManager,
    private val transactionManager: TransactionManager
) {
    private var started = false
    private var scope: CoroutineScope? = null

    val lastBlockHeight: Long
        get() = syncer.lastBlockHeight

    val lastBlockHeightFlow: StateFlow<Long>
        get() = syncer.lastBlockHeightFlow

    val trxBalance: BigInteger
        get() = accountInfoManager.trxBalance

    val trxBalanceFlow: StateFlow<BigInteger>
        get() = accountInfoManager.trxBalanceFlow

    val syncState: SyncState
        get() = syncer.syncState

    val syncStateFlow: StateFlow<SyncState>
        get() = syncer.syncStateFlow

    val transactionsFlow: StateFlow<Pair<List<FullTransaction>, Boolean>>
        get() = transactionManager.transactionsFlow

    fun start() {
        if (started) return
        started = true

        scope = CoroutineScope(Dispatchers.IO)
            .apply {
                syncer.start(this)
            }
    }

    fun stop() {
        started = false
        syncer.stop()

        scope?.cancel()
    }

    fun getFullTransactionsFlow(tags: List<List<String>>): Flow<List<FullTransaction>> {
        return transactionManager.getFullTransactionsFlow(tags)
    }

    suspend fun getFullTransactions(tags: List<List<String>>, fromHash: ByteArray? = null, limit: Int? = null): List<FullTransaction> {
        return transactionManager.getFullTransactions(tags, fromHash, limit)
    }

    fun getFullTransactions(hashes: List<ByteArray>): List<FullTransaction> {
        return transactionManager.getFullTransactions(hashes)
    }

    sealed class SyncState {
        class Synced : SyncState()
        class NotSynced(val error: Throwable) : SyncState()
        class Syncing(val progress: Double? = null) : SyncState()

        override fun toString(): String = when (this) {
            is Syncing -> "Syncing ${progress?.let { "${it * 100}" } ?: ""}"
            is NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
            else -> this.javaClass.simpleName
        }

        override fun equals(other: Any?): Boolean {
            if (other !is SyncState)
                return false

            if (other.javaClass != this.javaClass)
                return false

            if (other is Syncing && this is Syncing) {
                return other.progress == this.progress
            }

            return true
        }

        override fun hashCode(): Int {
            if (this is Syncing) {
                return Objects.hashCode(this.progress)
            }
            return Objects.hashCode(this.javaClass.name)
        }
    }

    open class SyncError : Exception() {
        class NotStarted : SyncError()
        class NoNetworkConnection : SyncError()
    }

    companion object {

        fun init() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(InternalBouncyCastleProvider.getInstance())
        }

        fun getInstance(
            application: Application,
            words: List<String>,
            passphrase: String = "",
            network: Network,
            tronGridApiKey: String,
            walletId: String
        ): TronKit {
            val seed = Mnemonic().toSeed(words, passphrase)
            val privateKey = Signer.privateKey(seed, network)
            val address = Signer.address(privateKey, network)

            return getInstance(application, address, network, tronGridApiKey, walletId)
        }

        fun getInstance(
            application: Application,
            address: Address,
            network: Network,
            tronGridApiKey: String,
            walletId: String
        ): TronKit {
            val syncTimer = SyncTimer(30, ConnectionManager(application))
            val tronGridService = TronGridService(network, tronGridApiKey)
            val databaseName = getDatabaseName(network, walletId)
            val storage = Storage(MainDatabase.getInstance(application, databaseName))
            val accountInfoManager = AccountInfoManager(storage)
            val decorationManager = DecorationManager(storage).apply {
                addTransactionDecorator(Trc20TransactionDecorator(address))
            }
            val transactionManager = TransactionManager(address, storage, decorationManager, Gson())
            val syncer = Syncer(address, syncTimer, tronGridService, accountInfoManager, transactionManager, storage)

            return TronKit(syncer, accountInfoManager, transactionManager)
        }

        private fun getDatabaseName(network: Network, walletId: String): String {
            return "Tron-${network.name}-$walletId"
        }
    }

}