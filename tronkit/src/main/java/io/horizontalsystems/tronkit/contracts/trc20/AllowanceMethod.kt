package io.horizontalsystems.tronkit.contracts.trc20

import io.horizontalsystems.tronkit.contracts.ContractMethod
import io.horizontalsystems.tronkit.models.Address

class AllowanceMethod(val owner: Address, val spender: Address) : ContractMethod() {
    override val methodSignature = "allowance(address,address)"
    override fun getArguments() = listOf(owner, spender)
}
