package io.horizontalsystems.tronkit.rpc


class CallJsonRpc(
    @Transient val contractAddress: String,
    @Transient val data: String,
    @Transient val defaultBlockParameter: String
) : DataJsonRpc(
    method = "eth_call",
    params = listOf(mapOf("to" to contractAddress, "data" to data), defaultBlockParameter)
)
