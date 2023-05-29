package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.models.Address

open class TransactionDecoration {
    open fun tags(userAddress: Address): List<String> = listOf()
}
