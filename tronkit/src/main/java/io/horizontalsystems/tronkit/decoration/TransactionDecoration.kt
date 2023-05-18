package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.Address

open class TransactionDecoration {
    open fun tags(userAddress: Address): List<String> = listOf()
}
