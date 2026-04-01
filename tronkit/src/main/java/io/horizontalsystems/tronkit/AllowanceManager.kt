package io.horizontalsystems.tronkit

import io.horizontalsystems.tronkit.contracts.ContractMethodHelper
import io.horizontalsystems.tronkit.contracts.trc20.AllowanceMethod
import io.horizontalsystems.tronkit.contracts.trc20.ApproveMethod
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.IRpcApiProvider
import io.horizontalsystems.tronkit.rpc.CallJsonRpc
import io.horizontalsystems.tronkit.rpc.DefaultBlockParameter
import java.math.BigInteger

class AllowanceManager(
    private val owner: Address,
    private val rpcApiProvider: IRpcApiProvider
) {

    suspend fun allowance(contract: Address, spender: Address): BigInteger {
        val rpc = CallJsonRpc(
            contractAddress = "0x${contract.hex}",
            data = AllowanceMethod(owner, spender).encodedABI().toHexString(),
            defaultBlockParameter = DefaultBlockParameter.Latest.raw
        )
        val response = rpcApiProvider.fetch(rpc)
        if (response.isEmpty()) throw IllegalStateException()

        return BigInteger(response.sliceArray(0..31).toRawHexString(), 16)
    }

    fun approveTrc20TriggerSmartContract(contract: Address, spender: Address, amount: BigInteger): TriggerSmartContract {
        val approveMethod = ApproveMethod(spender, amount)
        val data = approveMethod.encodedABI().toRawHexString()
        val parameter = ContractMethodHelper
            .encodedABI(methodId = byteArrayOf(), arguments = approveMethod.getArguments())
            .toRawHexString()

        return TriggerSmartContract(
            data = data,
            ownerAddress = owner,
            contractAddress = contract,
            callTokenValue = null,
            callValue = null,
            tokenId = null,
            functionSelector = ApproveMethod.methodSignature,
            parameter = parameter
        )
    }

}
