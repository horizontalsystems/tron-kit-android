package io.horizontalsystems.tronkit.models

import androidx.room.Entity

@Entity(primaryKeys = ["name", "hash"])
class TransactionTag(
    val name: String,
    val hash: ByteArray
) {
    companion object {
        const val TRX_COIN = "TRX"
        const val INCOMING = "incoming"
        const val OUTGOING = "outgoing"
        const val SWAP = "swap"
        const val TRX_COIN_INCOMING = "${TRX_COIN}_${INCOMING}"
        const val TRX_COIN_OUTGOING = "${TRX_COIN}_${OUTGOING}"
        const val TRC20_TRANSFER = "trc20Transfer"
        const val TRC20_APPROVE = "trc20Approve"

        fun trc20Incoming(contractAddress: String): String = "trc20_${contractAddress}_$INCOMING"
        fun trc20Outgoing(contractAddress: String): String = "trc20_${contractAddress}_$OUTGOING"

        fun trc10Incoming(assetId: String): String = "trc10_${assetId}_$INCOMING"
        fun trc10Outgoing(assetId: String): String = "trc10_${assetId}_$OUTGOING"
    }

}
