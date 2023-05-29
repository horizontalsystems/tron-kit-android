package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransactionTag
import io.horizontalsystems.tronkit.models.TransferAssetContract
import io.horizontalsystems.tronkit.models.TransferContract

class NativeTransactionDecoration(
    val contract: Contract
) : TransactionDecoration() {

    override fun tags(userAddress: Address): List<String> {
        val tags = mutableListOf<String>()

        when (contract) {
            is TransferContract -> {
                if (contract.ownerAddress == userAddress) {
                    tags.add(TransactionTag.TRX_COIN_OUTGOING)
                    tags.add(TransactionTag.OUTGOING)
                }
                if (contract.toAddress == userAddress) {
                    tags.add(TransactionTag.TRX_COIN_INCOMING)
                    tags.add(TransactionTag.INCOMING)
                }
            }

            is TransferAssetContract -> {
                if (contract.ownerAddress == userAddress) {
                    tags.add(TransactionTag.trc10Outgoing(contract.assetName))
                    tags.add(TransactionTag.OUTGOING)
                }
                if (contract.toAddress == userAddress) {
                    tags.add(TransactionTag.trc10Incoming(contract.assetName))
                    tags.add(TransactionTag.INCOMING)
                }
            }

            else -> {}
        }

        return tags
    }

}
