package io.horizontalsystems.tronkit.contracts.trc20

import io.horizontalsystems.tronkit.contracts.ContractMethod
import io.horizontalsystems.tronkit.models.Address

class BalanceOfMethod(val owner: Address) : ContractMethod() {
    override val methodSignature = "balanceOf(address)"
    override fun getArguments() = listOf(owner)
}
