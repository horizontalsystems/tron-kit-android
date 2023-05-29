package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.horizontalsystems.tronkit.toRawHexString
import java.math.BigInteger

@Entity
data class InternalTransaction(
    @PrimaryKey
    val transactionHash: ByteArray,
    val timestamp: Long,
    val from: Address,
    val to: Address,
    val value: BigInteger,
    val internalTxId: String
) {

    @delegate:Ignore
    val hashString: String by lazy {
        transactionHash.toRawHexString()
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Transaction && transactionHash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        return transactionHash.contentHashCode()
    }
}
