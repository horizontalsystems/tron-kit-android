package io.horizontalsystems.tronkit.rpc

import io.horizontalsystems.tronkit.contracts.ContractMethodHelper
import io.horizontalsystems.tronkit.contracts.trc20.DecimalsMethod
import io.horizontalsystems.tronkit.contracts.trc20.NameMethod
import io.horizontalsystems.tronkit.contracts.trc20.SymbolMethod
import io.horizontalsystems.tronkit.decoration.TokenInfo
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.RpcSource
import io.horizontalsystems.tronkit.network.IRpcApiProvider
import io.horizontalsystems.tronkit.network.TronGridProvider
import io.horizontalsystems.tronkit.toBigInteger
import io.horizontalsystems.tronkit.toHexString

class Trc20Provider(
    private val rpcApiProvider: IRpcApiProvider
) {
    class TokenNotFoundException : Throwable()

    suspend fun getTokenInfo(contractAddress: Address): TokenInfo {
        val name = getTokenName(contractAddress)
        val symbol = getTokenSymbol(contractAddress)
        val decimals = getDecimals(contractAddress)

        return TokenInfo(name, symbol, decimals)
    }

    suspend fun getDecimals(contractAddress: Address): Int {
        val rpc = CallJsonRpc(
            contractAddress = "0x${contractAddress.hex}",
            data = DecimalsMethod().encodedABI().toHexString(),
            defaultBlockParameter = DefaultBlockParameter.Latest.raw
        )
        val response = rpcApiProvider.fetch(rpc)
        if (response.isEmpty()) throw TokenNotFoundException()

        return response.sliceArray(IntRange(0, 31)).toBigInteger().toInt()
    }

    suspend fun getTokenSymbol(contractAddress: Address): String {
        val rpc = CallJsonRpc(
            contractAddress = "0x${contractAddress.hex}",
            data = SymbolMethod().encodedABI().toHexString(),
            defaultBlockParameter = DefaultBlockParameter.Latest.raw
        )
        val response = rpcApiProvider.fetch(rpc)
        if (response.isEmpty()) throw TokenNotFoundException()

        val argumentTypes = listOf(ByteArray::class)
        val parsedArguments = ContractMethodHelper.decodeABI(response, argumentTypes)
        val stringBytes = parsedArguments[0] as? ByteArray ?: throw TokenNotFoundException()

        return String(stringBytes)
    }

    suspend fun getTokenName(contractAddress: Address): String {
        val rpc = CallJsonRpc(
            contractAddress = "0x${contractAddress.hex}",
            data = NameMethod().encodedABI().toHexString(),
            defaultBlockParameter = DefaultBlockParameter.Latest.raw
        )
        val response = rpcApiProvider.fetch(rpc)
        if (response.isEmpty()) throw TokenNotFoundException()

        val argumentTypes = listOf(ByteArray::class)
        val parsedArguments = ContractMethodHelper.decodeABI(response, argumentTypes)
        val stringBytes = parsedArguments[0] as? ByteArray ?: throw TokenNotFoundException()

        return String(stringBytes)
    }

    companion object {
        fun getInstance(rpcSource: RpcSource): Trc20Provider {
            val provider = TronGridProvider(rpcSource.urls.first(), rpcSource.apiKeys)
            return Trc20Provider(provider)
        }
    }
}
