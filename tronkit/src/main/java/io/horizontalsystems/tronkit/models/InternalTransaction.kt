package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tronkit.Address

@Entity
data class InternalTransaction(
    @PrimaryKey
    val transactionHash: ByteArray,
    val timestamp: Long,
    val from: Address,
    val to: Address,
    val value: Long,
    val internalTxId: String
) {
    override fun equals(other: Any?): Boolean {
        return this === other || other is Transaction && transactionHash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        return transactionHash.contentHashCode()
    }
}
