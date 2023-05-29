package io.horizontalsystems.tronkit.decoration.trc20

import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.decoration.TokenInfo
import io.horizontalsystems.tronkit.decoration.TransactionDecoration
import io.horizontalsystems.tronkit.models.TransactionTag
import java.math.BigInteger

class OutgoingTrc20Decoration(
    val contractAddress: Address,
    val to: Address,
    val value: BigInteger,
    val sentToSelf: Boolean,
    val tokenInfo: TokenInfo?
) : TransactionDecoration() {

    override fun tags(userAddress: Address): List<String> =
        listOf(contractAddress.hex, TransactionTag.TRC20_TRANSFER, TransactionTag.trc20Outgoing(contractAddress.hex), TransactionTag.OUTGOING)

}
