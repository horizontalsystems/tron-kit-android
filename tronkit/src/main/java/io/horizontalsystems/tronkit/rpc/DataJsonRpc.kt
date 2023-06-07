package io.horizontalsystems.tronkit.rpc

open class DataJsonRpc(
        method: String,
        params: List<Any>
) : JsonRpc<ByteArray>(method, params) {
    @Transient
    override val typeOfResult = ByteArray::class.java
}
