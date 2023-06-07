package io.horizontalsystems.tronkit.contracts.trc20

import io.horizontalsystems.tronkit.contracts.ContractMethod


class NameMethod: ContractMethod() {
    override var methodSignature = "name()"
    override fun getArguments() = listOf<Any>()
}
