package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.horizontalsystems.tronkit.toRawHexString
import java.math.BigInteger

@Entity
class Trc20EventRecord(
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
) {

    @delegate:Ignore
    val hashString: String by lazy {
        transactionHash.toRawHexString()
    }

}
