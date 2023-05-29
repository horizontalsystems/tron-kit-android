package io.horizontalsystems.tronkit.decoration.trc20

import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.decoration.TransactionDecoration
import io.horizontalsystems.tronkit.models.TransactionTag
import java.math.BigInteger

class ApproveTrc20Decoration(
    val contractAddress: Address,
    val spender: Address,
    val value: BigInteger
) : TransactionDecoration() {

    override fun tags(userAddress: Address): List<String> =
        listOf(contractAddress.hex, TransactionTag.TRC20_APPROVE)
}
