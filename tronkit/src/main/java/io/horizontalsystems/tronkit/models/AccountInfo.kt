package io.horizontalsystems.tronkit.models

import java.math.BigInteger

data class AccountInfo(
    val balance: BigInteger,
    val trc20Balances: List<Trc20Balance>
)

data class Trc20Balance(
    val contractAddress: String,
    val balance: BigInteger
)