package io.horizontalsystems.tronkit.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.models.Transaction
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
    private val service: TronGridExtensionAPI
    private val rpcService: TronGridRpcAPI
    private val gsonRpc: Gson

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

        gsonRpc = gson(isHex = true)
        rpcService = retrofit(httpClient, baseUrl, gsonRpc).create(TronGridRpcAPI::class.java)

        val gson = gson(isHex = false)
        service = retrofit(httpClient, baseUrl, gson).create(TronGridExtensionAPI::class.java)
    }

    suspend fun getBlockHeight(): Long {
        val rpc = BlockNumberJsonRpc()
        rpc.id = currentRpcId.incrementAndGet()

        val rpcResponse = rpcService.rpc(gsonRpc.toJson(rpc))
        return rpc.parseResponse(rpcResponse, gsonRpc)
    }

    suspend fun getAccountInfo(address: String): AccountInfo {
        val response = service.accountInfo(address)
        val data = response.data.firstOrNull() ?: throw TronGridServiceError.NoAccountInfoData

        return AccountInfo(data.balance)
    }

    suspend fun getTransactions(address: String, startBlockTimestamp: Long, limit: Int, fingerprint: String?): Pair<List<Transaction>, String?> {
        val response = service.transactions(address, startBlockTimestamp, limit, fingerprint)
        check(response.success) { "Response with success = false" }

        val transactions = response.data.map { Transaction(it.txID, it.blockNumber, it.block_timestamp, it.raw_data.contract.firstOrNull()?.type) }
        return Pair(transactions, response.meta.fingerprint)
    }

    private fun gson(isHex: Boolean): Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(BigInteger::class.java, BigIntegerTypeAdapter(isHex))
        .registerTypeAdapter(Long::class.java, LongTypeAdapter(isHex))
        .registerTypeAdapter(object : TypeToken<Long?>() {}.type, LongTypeAdapter(isHex))
        .registerTypeAdapter(Int::class.java, IntTypeAdapter(isHex))
        .create()

    private fun retrofit(httpClient: OkHttpClient.Builder, baseUrl: String, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(httpClient.build())
        .build()

    private interface TronGridRpcAPI {
        @POST("jsonrpc")
        @Headers("Content-Type: application/json", "Accept: application/json")
        suspend fun rpc(@Body jsonRpc: String): RpcResponse
    }

    private interface TronGridExtensionAPI {

        @GET("v1/accounts/{address}")
        suspend fun accountInfo(
            @Path("address") address: String
        ): AccountInfoResponse

        @GET("v1/accounts/{address}/transactions")
        suspend fun transactions(
            @Path("address") address: String,
            @Query("min_timestamp") startBlockTimestamp: Long,
            @Query("limit") limit: Int,
            @Query("fingerprint") fingerprint: String?,
            @Query("order_by") orderBy: String = "block_timestamp,asc"
        ): TransactionsResponse

        data class TransactionsResponse(
            val data: List<TransactionData>,
            val success: Boolean,
            val meta: Meta
        )

        data class Meta(
            val at: Long,
            val fingerprint: String?,
            val page_size: Int
        )

        data class TransactionData(
            val ret: List<TransactionRet>,
            val withdraw_amount: Long,
            val txID: String,
            val net_usage: Int,
            val raw_data_hex: String,
            val net_fee: Int,
            val energy_usage: Int,
            val blockNumber: Long,
            val block_timestamp: Long,
            val energy_fee: Int,
            val energy_usage_total: Int,
            val raw_data: RawData
        )

        data class RawData(
            val contract: List<Contract>,
            val ref_block_bytes: String,
            val ref_block_hash: String,
            val expiration: Long,
            val timestamp: Long
        )

        data class Contract(
            val type: String
        )

        data class TransactionRet(
            val contractRet: String,
            val fee: Long
        )

        data class AccountInfoResponse(
            val data: List<AccountInfoData>
        )

        data class AccountInfoData(
            val account_resource: AccountResource,
            val address: String,
            val create_time: Long,
            val latest_opration_time: Long,
            val balance: BigInteger
        )

        data class AccountResource(
            val energy_usage: Int,
            val latest_consume_time_for_energy: Long,
            val energy_window_size: Int
        )
    }

    sealed class TronGridServiceError : Throwable() {
        object NoAccountInfoData : TronGridServiceError()
    }
}
