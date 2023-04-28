package io.horizontalsystems.tronkit.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.tronkit.rpc.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class TronGridService(
    network: Network,
    apiKey: String?
) {
    private var currentRpcId = AtomicInteger(0)

    private val baseUrl = when (network) {
        Network.Mainnet -> "https://api.trongrid.io/"
        Network.ShastaTestnet -> "https://api.shasta.trongrid.io/"
        Network.NileTestnet -> " https://nile.trongrid.io/"
    }
    private val logger = Logger.getLogger("TronGridService")
    private val service: TronGridServiceAPI
    private val gson: Gson

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        val headersInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()

            apiKey?.let {
                requestBuilder.header("TRON-PRO-API-KEY", it)
            }
            chain.proceed(requestBuilder.build())
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(headersInterceptor)

        gson = GsonBuilder()
            .setLenient()
            .registerTypeAdapter(BigInteger::class.java, BigIntegerTypeAdapter())
            .registerTypeAdapter(Long::class.java, LongTypeAdapter())
            .registerTypeAdapter(object : TypeToken<Long?>() {}.type, LongTypeAdapter())
            .registerTypeAdapter(Int::class.java, IntTypeAdapter())
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClient.build())
            .build()

        service = retrofit.create(TronGridServiceAPI::class.java)
    }

    suspend fun getAccountInfo(address: String): AccountInfo {
        val response = service.accountInfo(address)
        val data = response.data.firstOrNull() ?: throw TronGridServiceError.NoAccountInfoData

        return AccountInfo(balance = data.balance)
    }

    suspend fun getBlockHeight(): Long {
        val rpc = BlockNumberJsonRpc()
        rpc.id = currentRpcId.incrementAndGet()

        val rpcResponse = service.rpc(gson.toJson(rpc))
        return rpc.parseResponse(rpcResponse, gson)
    }

    private interface TronGridServiceAPI {

        @GET("v1/accounts/{address}")
        suspend fun accountInfo(
            @Path("address") address: String
        ): AccountInfoResponse

        @POST("jsonrpc")
        @Headers("Content-Type: application/json", "Accept: application/json")
        suspend fun rpc(@Body jsonRpc: String): RpcResponse

        data class AccountInfoResponse(
            val data: List<AccountInfoData>
        )

        data class AccountInfoData(
            val account_resource: AccountResource,
            val address: String,
            val create_time: Long,
            val latest_opration_time: Long,
            val balance: Long
        )

        data class AccountResource(
            val energy_usage: Int,
            val latest_consume_time_for_energy: Long,
            val energy_window_size: Int
        )
    }

    data class AccountInfo(
        val balance: Long
    )

    sealed class TronGridServiceError : Throwable() {
        object NoAccountInfoData : TronGridServiceError()
    }
}
