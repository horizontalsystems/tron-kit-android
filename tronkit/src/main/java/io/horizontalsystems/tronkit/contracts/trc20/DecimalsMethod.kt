package io.horizontalsystems.tronkit.contracts.trc20

import io.horizontalsystems.tronkit.contracts.ContractMethod


class DecimalsMethod: ContractMethod() {
    override var methodSignature = "decimals()"
    override fun getArguments() = listOf<Any>()
}
