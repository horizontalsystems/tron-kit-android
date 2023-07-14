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
import io.horizontalsystems.tronkit.rpc.*
import io.horizontalsystems.tronkit.toRawHexString
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.math.BigInteger
import java.util.concurrent.TimeUnit
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
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }.setLevel(HttpLoggingInterceptor.Level.BASIC)
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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)

        gsonRpc = gson(isHex = true)
        rpcService = retrofit(httpClient, baseUrl, gsonRpc).create(TronGridRpcAPI::class.java)

        gson = gson(isHex = false)
        service = retrofit(httpClient, baseUrl, gson).create(TronGridExtensionAPI::class.java)
    }

    suspend fun getBlockHeight(): Long {
        val rpc = BlockNumberJsonRpc()
        rpc.id = currentRpcId.incrementAndGet()

        val rpcResponse = rpcService.rpc(gsonRpc.toJson(rpc)).await()
        return rpc.parseResponse(rpcResponse, gsonRpc)
    }

    suspend fun estimateEnergy(
        ownerAddress: String,
        contractAddress: String,
        value: BigInteger,
        data: String
    ): Long {
        val rpc = EstimateGasJsonRpc(
            from = ownerAddress,
            to = contractAddress,
            amount = value,
            gasLimit = 1,
            gasPrice = 1,
            data = data
        )

        rpc.id = currentRpcId.incrementAndGet()

        val rpcResponse = rpcService.rpc(gsonRpc.toJson(rpc)).await()
        return rpc.parseResponse(rpcResponse, gsonRpc)
    }


    suspend fun ethCall(
        contractAddress: String,
        data: String
    ): ByteArray {
        val rpc = CallJsonRpc(
            contractAddress = contractAddress,
            data = data,
            defaultBlockParameter = DefaultBlockParameter.Latest.raw
        )
        rpc.id = currentRpcId.incrementAndGet()

        val rpcResponse = rpcService.rpc(gsonRpc.toJson(rpc)).await()

        return rpc.parseResponse(rpcResponse, gsonRpc)
    }

    suspend fun getAccountInfo(address: String): AccountInfo {
        val response = service.accountInfo(address).await()
        val data = response.data.firstOrNull() ?: throw TronGridServiceError.NoAccountInfoData

        val trc20Balances = data.trc20.map { balanceMap ->
            balanceMap.map { (contractAddress, balance) ->
                Trc20Balance(contractAddress, BigInteger(balance))
            }
        }.flatten()

        return AccountInfo(data.balance ?: BigInteger.ZERO, trc20Balances)
    }

    suspend fun getTransactions(
        address: String,
        startBlockTimestamp: Long,
        fingerprint: String?,
        onlyConfirmed: Boolean,
        limit: Int,
        orderBy: String
    ): Pair<List<TransactionData>, String?> {
        val response = service.transactions(address, startBlockTimestamp, fingerprint, onlyConfirmed, limit, orderBy).await()

        check(response.success) { "transactions" }

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
        fingerprint: String?,
        onlyConfirmed: Boolean,
        limit: Int,
        orderBy: String
    ): Pair<List<ContractTransactionData>, String?> {
        val response = service.contractTransactions(address, startBlockTimestamp, fingerprint, onlyConfirmed, limit, orderBy).await()

        check(response.success) { "contractTransactions error" }

        return Pair(response.data, response.meta.fingerprint)
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
        ).await()

        check(response.Error == null) { "createTransaction error: ${response.Error?.let { hexStringToUtf8String(it) }}" }

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
        ).await()

        check(response.result.result) { "triggerSmartContract error: ${response.result.code} - ${hexStringToUtf8String(response.result.message)}" }

        return response.transaction
    }

    suspend fun broadcastTransaction(
        createdTransaction: CreatedTransaction,
        signature: ByteArray
    ): BroadcastTransactionResponse {

        val response = service.broadcastTransaction(
            SignedTransaction(
                visible = createdTransaction.visible,
                txID = createdTransaction.txID,
                raw_data = createdTransaction.raw_data,
                raw_data_hex = createdTransaction.raw_data_hex,
                signature = listOf(signature.toRawHexString())
            )
        ).await()

        check(response.result) { "broadcastTransaction error: ${response.code} - ${hexStringToUtf8String(response.message)}" }

        return response
    }

    suspend fun getChainParameters(): List<ChainParameter> {
        val response = service.getChainParameters().await()

        return response.chainParameter
    }

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

    private fun retrofit(httpClient: OkHttpClient.Builder, baseUrl: String, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(httpClient.build())
        .build()

    private interface TronGridRpcAPI {
        @POST("jsonrpc")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun rpc(@Body jsonRpc: String): Single<RpcResponse>
    }

    private interface TronGridExtensionAPI {

        @GET("v1/accounts/{address}")
        fun accountInfo(
            @Path("address") address: String
        ): Single<AccountInfoResponse>

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

        @POST("wallet/createtransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun createTransaction(
            @Body request: CreateTransactionRequest
        ): Single<CreatedTransaction>

        @POST("wallet/triggersmartcontract")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun triggerSmartContract(
            @Body request: TriggerSmartContractRequest
        ): Single<TriggerSmartContractResponse>

        @POST("wallet/estimateenergy")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun estimateEnergy(
            @Body request: EstimateEnergyRequest
        ): Single<EstimateEnergyResponse>

        @POST("wallet/broadcasttransaction")
        @Headers("Content-Type: application/json", "Accept: application/json")
        fun broadcastTransaction(
            @Body signedTransaction: SignedTransaction
        ): Single<BroadcastTransactionResponse>

        @GET("wallet/getchainparameters")
        fun getChainParameters(): Single<ChainParametersResponse>
    }

    sealed class TronGridServiceError : Throwable() {
        object NoAccountInfoData : TronGridServiceError()
    }
}

data class ChainParametersResponse(
    val chainParameter: List<ChainParameter>
)

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
    val raw_data_hex: String,
    val Error: String?
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

data class EstimateEnergyRequest(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val visible: Boolean = false
)

data class EstimateEnergyResponse(
    val result: Result,
    val energy_required: Long
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
    val balance: BigInteger?,
    val trc20: List<Map<String, String>>
)

data class AccountResource(
    val energy_usage: Int,
    val latest_consume_time_for_energy: Long,
    val energy_window_size: Int
)
