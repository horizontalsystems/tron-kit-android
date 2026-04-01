package io.horizontalsystems.tronkit.network

import io.horizontalsystems.tronkit.rpc.JsonRpc

interface IRpcApiProvider {
    suspend fun <T> fetch(rpc: JsonRpc<T>): T
}
