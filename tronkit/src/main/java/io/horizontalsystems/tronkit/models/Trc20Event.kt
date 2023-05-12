package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tronkit.Address
import java.math.BigInteger

@Entity
class Trc20Event(
    val transactionHash: ByteArray,
    val blockTimestamp: Long,
    val contractAddress: Address,
    val from: Address,
    val to: Address,
    val value: BigInteger,
    val type: String,

    val tokenName: String,
    val tokenSymbol: String,
    val tokenDecimal: Int,

    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
