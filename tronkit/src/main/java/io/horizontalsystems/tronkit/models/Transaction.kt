package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
data class Transaction(
    @PrimaryKey
    val hash: ByteArray,
    val timestamp: Long,
    val isFailed: Boolean = false,

    val blockNumber: Long? = null,
    val fee: Long? = null,
    val netUsage: Long? = null,
    val netFee: Long? = null,
    val energyUsage: Long? = null,
    val energyFee: Long? = null,
    val energyUsageTotal: Long? = null,

    val contractsRaw: String? = null,

    val processed: Boolean = false
) {

    @delegate:Ignore
    val contract: Contract? by lazy {
        contractsRaw?.let { Contract.from(it) }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Transaction && hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        return hash.contentHashCode()
    }
}