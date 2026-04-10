package io.horizontalsystems.tronkit.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.ChainParameter
import io.horizontalsystems.tronkit.models.Trc20Balance
import io.horizontalsystems.tronkit.rpc.BigIntegerTypeAdapter
import io.horizontalsystems.tronkit.rpc.ByteArrayTypeAdapter
import io.horizontalsystems.tronkit.rpc.IntTypeAdapter
import io.horizontalsystems.tronkit.rpc.JsonRpc
import io.horizontalsystems.tronkit.rpc.LongTypeAdapter
import io.horizontalsystems.tronkit.rpc.RpcResponse
import io.horizontalsystems.tronkit.toRawHexString
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.math.BigInteger
import java.net.URL
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.random.Random

class TronGridProvider(
    baseUrl: URL,
    apiKeys: List<String>,
    private val auth: String? = null
) : IRpcApiProvider, INodeApiProvider, IHistoryProvider {

    private var currentRpcId = AtomicInteger(0)
    private val logger = Logger.getLogger("TronGridProvider")
    private val extensionApi: TronGridExtensionAPI
    private val rpcApi: TronRpcAPI
    private val gsonRpc: Gson
    private val gson: Gson

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }
            .setLevel(HttpLoggingInterceptor.Level.BASIC)

        val apiKeyInterceptor: Interceptor = if (apiKeys.isEmpty()) {
            Interceptor { chain -> chain.proceed(chain.request()) }
        } else {
            val currentKeyIndex = AtomicInteger(Random.nextInt(apiKeys.size))
            Interceptor { chain ->
                val originalRequest = chain.request()
                val startIndex = currentKeyIndex.get()
                var previousResponse: Response? = null

                for (attempt in apiKeys.indices) {
                    val keyIndex = (startIndex + attempt) % apiKeys.size
                    val newRequest = originalRequest.newBuilder()
                        .header("TRON-PRO-API-KEY", apiKeys[keyIndex])
                        .build()
                    previousResponse?.close()
                    val response = chain.proceed(newRequest)

                    if (response.code != 429 || attempt == apiKeys.lastIndex) {
                        currentKeyIndex.set(keyIndex)
                        return@Interceptor response
                    }
                    currentKeyIndex.compareAndSet(keyIndex, (keyIndex + 1) % apiKeys.size)
                    previousResponse = response
                }

                chain.proceed(originalRequest)
            }
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(apiKeyInterceptor)
            .apply {
                auth?.let { credentials ->
                    val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Authorization", "Basic $encoded")
                            .build()
                        chain.proceed(request)
                    }
                }
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)

        val url = baseUrl.toString()
        gsonRpc = gson(isHex = true)
        rpcApi = retrofit(httpClient, url, gsonRpc).create(TronRpcAPI::class.java)

        gson = gson(isHex = false)
        extensionApi = retrofit(httpClient, url, gson).create(TronGridExtensionAPI::class.java)
    }

    // IRpcApiProvider

    override suspend fun <T> fetch(rpc: JsonRpc<T>): T {
        rpc.id = currentRpcId.incrementAndGet()
        val response = rpcApi.rpc(gsonRpc.toJson(rpc)).await()
        return rpc.parseResponse(response, gsonRpc)
    }

    // INodeApiProvider

    override suspend fun fetchAccount(address: String): NodeAccountResponse? {
        val response = extensionApi.getAccount(GetAccountRequest(address)).await()
        if (response["create_time"] == null)
            return null
        val balance = response["balance"]?.takeIf { !it.isJsonNull }?.asBigInteger ?: BigInteger.ZERO
        return NodeAccountResponse(balance)
    }

    override suspend fun fetchChainParameters(): List<ChainParameterResponse> {
        val response = extensionApi.getChainParameters().await()
        return response.chainParameter.map { ChainParameterResponse(it.key, it.value) }
    }

    override suspend fun createTransaction(
        ownerAddress: String,
        toAddress: String,
        amount: BigInteger
    ): CreatedTransaction {
        val response = extensionApi.createTransaction(
            CreateTransactionRequest(
                owner_address = ownerAddress,
                to_address = toAddress,
                amount = amount
            )
        ).await()

        check(response.Error == null) {
            "createTransaction error: ${response.Error?.let { hexStringToUtf8String(it) }}"
        }

        return response
    }

    override suspend fun triggerSmartContract(
        ownerAddress: String,
        contractAddress: String,
        functionSelector: String,
        parameter: String,
        callValue: Long,
        feeLimit: Long
    ): CreatedTransaction {
        val response = extensionApi.triggerSmartContract(
            TriggerSmartContractRequest(
                owner_address = ownerAddress,
                contract_address = contractAddress,
                function_selector = functionSelector,
                parameter = parameter,
                fee_limit = feeLimit,
                call_value = callValue
            )
        ).await()

        check(response.result.result) {
            "triggerSmartContract error: ${response.result.code} - ${hexStringToUtf8String(response.result.message)}"
        }

        return response.transaction
    }

    override suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signature: ByteArray) {
        val response = extensionApi.broadcastTransaction(
            SignedTransaction(
                visible = createdTransaction.visible,
                txID = createdTransaction.txID,
                raw_data = createdTransaction.raw_data,
                raw_data_hex = createdTransaction.raw_data_hex,
                signature = listOf(signature.toRawHexString())
            )
        ).await()

        check(response.result) {
            "broadcastTransaction error: ${response.code} - ${hexStringToUtf8String(response.message)}"
        }
    }

    // IHistoryProvider

    override suspend fun fetchAccountInfo(address: String): AccountInfo {
        val response = extensionApi.accountInfo(address).await()
        val data = response.data.firstOrNull()
            ?: throw IHistoryProvider.RequestError.FailedToFetchAccountInfo

        val trc20Balances = data.trc20.flatMap { balanceMap ->
            balanceMap.map { (contractAddress, balance) ->
                Trc20Balance(contractAddress, BigInteger(balance))
            }
        }

        return AccountInfo(data.balance ?: BigInteger.ZERO, trc20Balances)
    }

    override suspend fun fetchTransactions(
        address: String,
        minTimestamp: Long,
        cursor: String?
    ): Pair<List<TransactionData>, String?> {
        val response = extensionApi.transactions(
            address = address,
            startBlockTimestamp = minTimestamp,
            fingerprint = cursor,
            onlyConfirmed = true,
            limit = PAGE_LIMIT,
            orderBy = ORDER_BY
        ).await()

        check(response.success) { "fetchTransactions failed" }

        val transactions = response.data.map {
            if (it.has("internal_tx_id")) {
                gson.fromJson(it, InternalTransactionData::class.java)
            } else {
                gson.fromJson(it, RegularTransactionData::class.java)
            }
        }

        return Pair(transactions, response.meta.fingerprint)
    }

    override suspend fun fetchTrc20Transactions(
        address: String,
        minTimestamp: Long,
        cursor: String?
    ): Pair<List<ContractTransactionData>, String?> {
        val response = extensionApi.contractTransactions(
            address = address,
            startBlockTimestamp = minTimestamp,
            fingerprint = cursor,
            onlyConfirmed = true,
            limit = PAGE_LIMIT,
            orderBy = ORDER_BY
        ).await()

        check(response.success) { "fetchTrc20Transactions failed" }

        return Pair(response.data, response.meta.fingerprint)
    }

    // Helpers

    private fun hexStringToUtf8String(hexString: String) = try {
        String(hexString.hexStringToByteArray())
    } catch (_: Throwable) {
        hexString
    }

    private fun gson(isHex: Boolean): Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(BigInteger::class.java, BigIntegerTypeAdapter(isHex))
        .registerTypeAdapter(Long::class.java, LongTypeAdapter(isHex))
        .registerTypeAdapter(object : TypeToken<Long?>() {}.type, LongTypeAdapter(isHex))
        .registerTypeAdapter(Int::class.java, IntTypeAdapter(isHex))
        .registerTypeAdapter(ByteArray::class.java, ByteArrayTypeAdapter())
        .create()

    private fun retrofit(httpClient: OkHttpClient.Builder, baseUrl: String, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClient.build())
            .build()

    // Retrofit API interfaces

    private interface TronRpcAPI {
        @POST("jsonrpc")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun rpc(@Body jsonRpc: String): Single<RpcResponse>
    }

    private interface TronGridExtensionAPI {

        // Tron FullNode HTTP API

        @POST("wallet/getaccount")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun getAccount(@Body request: GetAccountRequest): Single<JsonObject>

        @POST("wallet/createtransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun createTransaction(@Body request: CreateTransactionRequest): Single<CreatedTransaction>

        @POST("wallet/triggersmartcontract")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun triggerSmartContract(@Body request: TriggerSmartContractRequest): Single<TriggerSmartContractResponse>

        @POST("wallet/broadcasttransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun broadcastTransaction(@Body signedTransaction: SignedTransaction): Single<BroadcastTransactionResponse>

        @GET("wallet/getchainparameters")
        fun getChainParameters(): Single<ChainParametersResponse>


        // TronGrid extension API

        @GET("v1/accounts/{address}")
        fun accountInfo(@Path("address") address: String): Single<AccountInfoResponse>

        @GET("v1/accounts/{address}/transactions")
        fun transactions(
            @Path("address") address: String,
            @Query("min_timestamp") startBlockTimestamp: Long,
            @Query("fingerprint") fingerprint: String?,
            @Query("only_confirmed") onlyConfirmed: Boolean,
            @Query("limit") limit: Int,
            @Query("order_by") orderBy: String
        ): Single<TransactionsResponse>

        @GET("v1/accounts/{address}/transactions/trc20")
        fun contractTransactions(
            @Path("address") address: String,
            @Query("min_timestamp") startBlockTimestamp: Long,
            @Query("fingerprint") fingerprint: String?,
            @Query("only_confirmed") onlyConfirmed: Boolean,
            @Query("limit") limit: Int,
            @Query("order_by") orderBy: String
        ): Single<ContractTransactionsResponse>
    }

    companion object {
        private const val PAGE_LIMIT = 200
        private const val ORDER_BY = "block_timestamp,asc"
    }
}

data class GetAccountRequest(
    val address: String,
    val visible: Boolean = false
)

data class ChainParametersResponse(
    val chainParameter: List<ChainParameter>
)

data class CreateTransactionRequest(
    val owner_address: String,
    val to_address: String,
    val amount: BigInteger,
    val visible: Boolean = false
)

data class TriggerSmartContractRequest(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val fee_limit: Long,
    val call_value: Long,
    val visible: Boolean = false
)

data class TriggerSmartContractResponse(
    val result: Result,
    val transaction: CreatedTransaction
)

data class Result(
    val result: Boolean,
    val code: String,
    val message: String
)

data class SignedTransaction(
    val visible: Boolean,
    val txID: String,
    val raw_data: RawData,
    val raw_data_hex: String,
    val signature: List<String>
)

data class BroadcastTransactionResponse(
    val result: Boolean,
    val txid: String,
    val code: String,
    val message: String
)

data class ContractTransactionsResponse(
    val data: List<ContractTransactionData>,
    val success: Boolean,
    val meta: Meta
)

data class TransactionsResponse(
    val data: List<JsonObject>,
    val success: Boolean,
    val meta: Meta
)

data class Meta(
    val at: Long,
    val fingerprint: String?,
    val page_size: Int
)

data class AccountInfoResponse(
    val data: List<AccountInfoData>
)

data class AccountInfoData(
    val account_resource: AccountResource?,
    val address: String,
    val balance: BigInteger?,
    val trc20: List<Map<String, String>>
)

data class AccountResource(
    val energy_usage: Int,
    val latest_consume_time_for_energy: Long,
    val energy_window_size: Int
)

data class CreatedTransaction(
    val visible: Boolean,
    val txID: String,
    val raw_data: RawData,
    val raw_data_hex: String,
    val Error: String?
)

data class RawData(
    val contract: List<ContractRaw>,
    val ref_block_bytes: String,
    val ref_block_hash: String,
    val expiration: Long,
    val timestamp: Long,
    val fee_limit: Long?
)

data class ContractRaw(
    val type: String,
    val parameter: Parameter
) {
    val amount: BigInteger?
        get() = parameter.value.amount

    val ownerAddress: Address?
        get() = parameter.value.owner_address?.let { Address.fromHex(it) }

    val toAddress: Address?
        get() = parameter.value.to_address?.let { Address.fromHex(it) }

    val assetName: String?
        get() = parameter.value.asset_name

    val withdrawAmount: Long?
        get() = parameter.value.withdraw_amount

    val data: String?
        get() = parameter.value.data

    val contractAddress: Address?
        get() = parameter.value.contract_address?.let { Address.fromHex(it) }
}

data class Parameter(
    val value: Value,
    val type_url: String
)

data class Value(
    val amount: BigInteger?,
    val owner_address: String?,
    val to_address: String?,
    val asset_name: String?,
    val withdraw_amount: Long?,
    val data: String?,
    val contract_address: String?,
    val call_value: BigInteger?,
    val call_token_value: BigInteger?,
    val token_id: Int?,
    val total_supply: Long?,
    val precision: Int?,
    val name: String?,
    val description: String?,
    val abbr: String?,
    val url: String?,
    val resource: String?,
    val unfreeze_balance: Long?,
    val frozen_balance: Long?,
    val votes: List<Vote>?
)

data class Vote(
    val vote_address: String,
    val vote_count: Long
)

data class TransactionRet(
    val contractRet: String,
    val fee: Long
)

sealed class TransactionData(val block_timestamp: Long)

class InternalTransactionData(
    block_timestamp: Long,
    val internal_tx_id: String,
    val data: Map<String, Any>,
    val to_address: String,
    val tx_id: String,
    val from_address: String
) : TransactionData(block_timestamp)

class RegularTransactionData(
    block_timestamp: Long,
    val ret: List<TransactionRet>,
    val withdraw_amount: Long?,
    val txID: String,
    val net_usage: Long,
    val raw_data_hex: String,
    val net_fee: Long,
    val energy_usage: Long,
    val blockNumber: Long,
    val energy_fee: Long,
    val energy_usage_total: Long,
    val raw_data: RawData
) : TransactionData(block_timestamp)

data class ContractTransactionData(
    val transaction_id: String,
    val token_info: TokenInfo,
    val block_timestamp: Long,
    val from: String,
    val to: String,
    val type: String,
    val value: String
)

data class TokenInfo(
    val symbol: String,
    val address: String,
    val decimals: Int,
    val name: String
)
