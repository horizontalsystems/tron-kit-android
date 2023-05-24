package io.horizontalsystems.tronkit.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.tronkit.Address
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.rpc.*
import io.horizontalsystems.tronkit.toRawHexString
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
    private val gson: Gson

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }.setLevel(HttpLoggingInterceptor.Level.BODY)
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

        gson = gson(isHex = false)
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
        val data = response.data.firstOrNull() //TODO handle inactive account // ?: throw TronGridServiceError.NoAccountInfoData

        return AccountInfo(data?.balance ?: BigInteger.ZERO)
    }

    suspend fun getTransactions(
        address: String,
        startBlockTimestamp: Long,
        fingerprint: String?
    ): Pair<List<TransactionData>, String?> {
        val response = service.transactions(address, startBlockTimestamp, fingerprint)
        check(response.success) { "Response with success = false" }

        val transactionData = response.data.map {
            if (it.has("internal_tx_id")) {
                gson.fromJson(it, InternalTransactionData::class.java)
            } else {
                gson.fromJson(it, RegularTransactionData::class.java)
            }
        }

        return Pair(transactionData, response.meta.fingerprint)
    }

    suspend fun getContractTransactions(
        address: String,
        startBlockTimestamp: Long,
        fingerprint: String?
    ): Pair<List<ContractTransactionData>, String?> {
        val response = service.contractTransactions(address, startBlockTimestamp, fingerprint)
        check(response.success) { "Response with success = false" }

        return Pair(response.data, response.meta.fingerprint)
    }

    fun estimateEnergy(ownerAddress: String, contractAddress: String, functionSelector: String, parameter: ByteArray): Long {
        TODO("not implemented")
    }

    suspend fun createTransaction(
        fromAddress: Address,
        toAddress: Address,
        amount: BigInteger
    ): CreatedTransaction {
        val response = service.createTransaction(
            CreateTransactionRequest(
                owner_address = fromAddress.hex,
                to_address = toAddress.hex,
                amount = amount
            )
        )
        Log.e("e", "to json: ${gson.toJson(response)}")

        return response
    }

    suspend fun triggerSmartContract(
        ownerAddress: Address,
        contractAddress: Address,
        functionSelector: String,
        parameter: String,
        feeLimit: Long,
        callValue: Long
    ): CreatedTransaction {
        val response = service.triggerSmartContract(
            TriggerSmartContractRequest(
                owner_address = ownerAddress.hex,
                contract_address = contractAddress.hex,
                function_selector = functionSelector,
                parameter = parameter,
                fee_limit = feeLimit,
                call_value = callValue
            )
        )

        check(response.result.result) { "Response with result = false" }

        return response.createdTransaction
    }

    suspend fun broadcastTransaction(
        createdTransaction: CreatedTransaction,
        signature: ByteArray
    ): BroadcastTransactionResponse {
        Log.e("e", "broadcast with signature: ${signature.toRawHexString()}")

        val response = service.broadcastTransaction(
            SignedTransaction(
                visible = createdTransaction.visible,
                txID = createdTransaction.txID,
                raw_data = createdTransaction.raw_data,
                raw_data_hex = createdTransaction.raw_data_hex,
                signature = listOf(signature.toRawHexString())
            )
        )

        Log.e("e", "to json: ${gson.toJson(response)}")

        return response
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
        companion object {
            private const val limit = 200
            private const val orderBy = "block_timestamp,asc"
        }

        @GET("v1/accounts/{address}")
        suspend fun accountInfo(
            @Path("address") address: String
        ): AccountInfoResponse

        @GET("v1/accounts/{address}/transactions")
        suspend fun transactions(
            @Path("address") address: String,
            @Query("min_timestamp") startBlockTimestamp: Long,
            @Query("fingerprint") fingerprint: String?,
            @Query("limit") limit: Int = Companion.limit,
            @Query("order_by") orderBy: String = Companion.orderBy
        ): TransactionsResponse

        @GET("v1/accounts/{address}/transactions/trc20")
        suspend fun contractTransactions(
            @Path("address") address: String,
            @Query("min_timestamp") startBlockTimestamp: Long,
            @Query("fingerprint") fingerprint: String?,
            @Query("limit") limit: Int = Companion.limit,
            @Query("order_by") orderBy: String = Companion.orderBy
        ): ContractTransactionsResponse

        @POST("wallet/createtransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        suspend fun createTransaction(
            @Body request: CreateTransactionRequest
        ): CreatedTransaction

        @POST("wallet/triggersmartcontract")
        @Headers("Content-Type: application/json", "Accept: application/json")
        suspend fun triggerSmartContract(
            @Body request: TriggerSmartContractRequest
        ): TriggerSmartContractResponse

        @POST("wallet/broadcasttransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        suspend fun broadcastTransaction(
            @Body signedTransaction: SignedTransaction
        ): BroadcastTransactionResponse
    }

    sealed class TronGridServiceError : Throwable() {
        object NoAccountInfoData : TronGridServiceError()
    }
}

data class CreateTransactionRequest(
    val owner_address: String,
    val to_address: String,
    val amount: BigInteger,
    val visible: Boolean = false
)

data class CreatedTransaction(
    val visible: Boolean,
    val txID: String,
    val raw_data: RawData,
    val raw_data_hex: String
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
    val createdTransaction: CreatedTransaction
)

data class Result(
    val result: Boolean
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

sealed class TransactionData(val block_timestamp: Long)

class InternalTransactionData(
    block_timestamp: Long,
    val internal_tx_id: String,
    val data: Map<String, Any>,
    val to_address: String,
    val tx_id: String,
    val from_address: String,
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
