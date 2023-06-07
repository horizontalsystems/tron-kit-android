package io.horizontalsystems.tronkit.contracts.trc20

import io.horizontalsystems.tronkit.contracts.ContractMethod


class SymbolMethod: ContractMethod() {
    override var methodSignature = "symbol()"
    override fun getArguments() = listOf<Any>()
}
