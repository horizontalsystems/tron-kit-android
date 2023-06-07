package io.horizontalsystems.tronkit.rpc

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

class EstimateGasJsonRpc(
    @Transient val from: String,
    @Transient val to: String,
    @Transient val amount: BigInteger,
    @Transient val gasLimit: Long,
    @Transient val gasPrice: Long,
    @Transient val data: String
) : LongJsonRpc(
    method = "eth_estimateGas",
    params = listOf(EstimateGasParams(from, to, amount, gasLimit, gasPrice, data))
)

data class EstimateGasParams(
    val from: String,
    val to: String,
    @SerializedName("value")
    val amount: BigInteger,
    @SerializedName("gas")
    val gasLimit: Long?,
    val gasPrice: Long?,
    val data: String
)
