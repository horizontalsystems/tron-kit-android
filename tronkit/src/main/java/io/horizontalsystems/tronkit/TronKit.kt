package io.horizontalsystems.tronkit

import android.app.Application
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.tronkit.crypto.InternalBouncyCastleProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.*

class TronKit(
    private val network: Network,
    private val syncManager: SyncManager
) {
    // tron grid apikey: 2551eb81-3228-4c8c-889d-127d3bb73ad0


    suspend fun start() {
        syncManager.start()
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
            val tronGridService = TronGridService(network, tronGridApiKey)
            val syncManager = SyncManager(network, tronGridService)

            return TronKit(network, syncManager)
        }
    }

}