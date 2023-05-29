package io.horizontalsystems.tronkit.sync

import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.database.Storage
import io.horizontalsystems.tronkit.network.TronGridService

class ChainParameterManager(
    private val tronGridService: TronGridService,
    private val storage: Storage
) {
    var chainParameters: Map<String, Long> = emptyMap()
        private set

    var syncState: TronKit.SyncState = TronKit.SyncState.Syncing()
        private set

    val transactionFee: Long // bandwidth point price
        get() = chainParameters["getTransactionFee"] ?: 1000

    val energyFee: Long // energy unit price
        get() = chainParameters["getEnergyFee"] ?: 420

    val createAccountFee: Long // bandwidth points for creating account
        get() = (chainParameters["getCreateAccountFee"] ?: 100_000) / transactionFee

    val createNewAccountFeeInSystemContract: Long // account activation fee in TRX/TRC10 transfer
        get() = chainParameters["getCreateNewAccountFeeInSystemContract"] ?: 1_000_000

    suspend fun sync() {
        syncState = try {
            tronGridService.getChainParameters().let { parameters ->
                chainParameters = parameters.associateBy({ it.key }, { it.value })

                storage.saveChainParameters(parameters)
            }
            TronKit.SyncState.Synced()
        } catch (error: Throwable) {
            TronKit.SyncState.NotSynced(error)
        }
    }
}
