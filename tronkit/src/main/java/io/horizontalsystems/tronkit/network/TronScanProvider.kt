package io.horizontalsystems.tronkit.network

import com.google.gson.annotations.SerializedName
import io.horizontalsystems.tronkit.models.AccountInfo
import io.horizontalsystems.tronkit.models.Trc20Balance
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigInteger
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TronScanProvider(
    baseUrl: URL,
    private val apiKey: String?
) : IHistoryProvider {

    private val logger = Logger.getLogger("TronScanProvider")
    private val api: TronScanAPI

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }
            .setLevel(HttpLoggingInterceptor.Level.BASIC)

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    apiKey?.let { header("TRON-PRO-API-KEY", it) }
                }.build()
                chain.proceed(request)
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.toString())
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(TronScanAPI::class.java)
    }

    override suspend fun fetchAccountInfo(address: String): AccountInfo {
        val response = try {
            api.getAccount(address)
        } catch (e: Throwable) {
            throw IHistoryProvider.RequestError.FailedToFetchAccountInfo
        }

        if (response.data.isEmpty()) {
            throw IHistoryProvider.RequestError.FailedToFetchAccountInfo
        }

        val account = response.data.first()
        val trxBalance = account.balance ?: BigInteger.ZERO
        val trc20Balances = account.trc20token_balances?.mapNotNull { token ->
            token.balance.toBigIntegerOrNull()?.let { Trc20Balance(token.tokenId, it) }
        } ?: emptyList()

        return AccountInfo(trxBalance, trc20Balances)
    }

    override suspend fun fetchTransactions(
        address: String,
        minTimestamp: Long,
        cursor: String?
    ): Pair<List<TransactionData>, String?> {
        val decoded = cursor?.let { Cursor.decode(it) } ?: Cursor(minTimestamp, 0)

        val response = api.getTransactions(
            address = address,
            start = decoded.start,
            limit = PAGE_SIZE,
            startTimestamp = decoded.effectiveMinTimestamp,
            sort = "timestamp"
        )

        val transactions = response.data.mapNotNull { it.toTransactionData() }
        val nextCursor = nextCursor(decoded, PAGE_SIZE, response.data.size, transactions.lastOrNull()?.block_timestamp)

        return Pair(transactions, nextCursor)
    }

    override suspend fun fetchTrc20Transactions(
        address: String,
        minTimestamp: Long,
        cursor: String?
    ): Pair<List<ContractTransactionData>, String?> {
        val decoded = cursor?.let { Cursor.decode(it) } ?: Cursor(minTimestamp, 0)

        val response = api.getTrc20Transfers(
            relatedAddress = address,
            start = decoded.start,
            limit = PAGE_SIZE,
            startTimestamp = decoded.effectiveMinTimestamp,
            direction = 0,
            dbVersion = 1
        )

        val transactions = response.token_transfers.mapNotNull { it.toContractTransactionData() }
        val nextCursor = nextCursor(decoded, PAGE_SIZE, response.token_transfers.size, transactions.lastOrNull()?.block_timestamp)

        return Pair(transactions, nextCursor)
    }

    // Cursor for TronScan offset-based pagination (10,000 record cap workaround)
    private data class Cursor(val effectiveMinTimestamp: Long, val start: Int) {
        fun encoded() = "$effectiveMinTimestamp:$start"

        companion object {
            fun decode(s: String): Cursor {
                val parts = s.split(":")
                return Cursor(parts[0].toLong(), parts[1].toInt())
            }
        }
    }

    private fun nextCursor(current: Cursor, pageSize: Int, receivedCount: Int, lastTimestamp: Long?): String? {
        if (receivedCount < pageSize) return null
        val nextStart = current.start + pageSize
        return if (nextStart >= 9950 && lastTimestamp != null) {
            Cursor(lastTimestamp, 0).encoded()
        } else {
            Cursor(current.effectiveMinTimestamp, nextStart).encoded()
        }
    }

    private fun contractTypeName(type: Int): String? = when (type) {
        1 -> "TransferContract"
        2 -> "TransferAssetContract"
        4 -> "VoteWitnessContract"
        11 -> "AssetIssueContract"
        13 -> "AccountUpdateContract"
        15 -> "FreezeBalanceContract"
        16 -> "UnfreezeBalanceContract"
        17 -> "WithdrawBalanceContract"
        31 -> "TriggerSmartContract"
        41 -> "FreezeBalanceV2Contract"
        42 -> "UnfreezeBalanceV2Contract"
        44 -> "DelegateResourceContract"
        45 -> "UnDelegateResourceContract"
        else -> null
    }

    private fun TronScanTransaction.toTransactionData(): RegularTransactionData? {
        val contractType = contractTypeName(this.contractType ?: 0) ?: return null
        val contractRaw = ContractRaw(
            type = contractType,
            parameter = Parameter(
                value = Value(
                    amount = this.contractData?.amount?.toBigIntegerOrNull(),
                    owner_address = this.ownerAddress,
                    to_address = this.toAddress,
                    asset_name = this.contractData?.asset_name,
                    withdraw_amount = null,
                    data = this.contractData?.data,
                    contract_address = this.contractData?.contract_address,
                    call_value = this.contractData?.call_value?.toBigIntegerOrNull(),
                    call_token_value = null,
                    token_id = null,
                    total_supply = null,
                    precision = null,
                    name = null,
                    description = null,
                    abbr = null,
                    url = null,
                    resource = null,
                    unfreeze_balance = null,
                    frozen_balance = null,
                    votes = null
                ),
                type_url = ""
            )
        )

        return RegularTransactionData(
            block_timestamp = this.timestamp ?: 0L,
            ret = listOf(TransactionRet(contractRet = this.contractRet ?: "", fee = this.fee ?: 0L)),
            withdraw_amount = null,
            txID = this.hash ?: "",
            net_usage = 0L,
            raw_data_hex = "",
            net_fee = 0L,
            energy_usage = 0L,
            blockNumber = this.block ?: 0L,
            energy_fee = 0L,
            energy_usage_total = 0L,
            raw_data = RawData(
                contract = listOf(contractRaw),
                ref_block_bytes = "",
                ref_block_hash = "",
                expiration = 0L,
                timestamp = this.timestamp ?: 0L,
                fee_limit = null
            )
        )
    }

    private fun TronScanTrc20Transfer.toContractTransactionData(): ContractTransactionData? {
        val address = this.contractAddress ?: return null
        return ContractTransactionData(
            transaction_id = this.transaction_id ?: "",
            token_info = TokenInfo(
                symbol = this.tokenInfo?.tokenAbbr ?: "",
                address = address,
                decimals = this.tokenInfo?.tokenDecimal ?: 18,
                name = this.tokenInfo?.tokenName ?: ""
            ),
            block_timestamp = this.block_ts ?: 0L,
            from = this.from_address ?: "",
            to = this.to_address ?: "",
            type = "Transfer",
            value = this.quant ?: "0"
        )
    }

    // Retrofit API interface

    private interface TronScanAPI {
        @GET("account")
        suspend fun getAccount(@Query("address") address: String): TronScanAccountResponse

        @GET("transaction")
        suspend fun getTransactions(
            @Query("address") address: String,
            @Query("start") start: Int,
            @Query("limit") limit: Int,
            @Query("start_timestamp") startTimestamp: Long,
            @Query("sort") sort: String
        ): TronScanTransactionsResponse

        @GET("token_trc20/transfers")
        suspend fun getTrc20Transfers(
            @Query("relatedAddress") relatedAddress: String,
            @Query("start") start: Int,
            @Query("limit") limit: Int,
            @Query("start_timestamp") startTimestamp: Long,
            @Query("direction") direction: Int,
            @Query("db_version") dbVersion: Int
        ): TronScanTrc20Response
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}

// TronScan response models

data class TronScanAccountResponse(
    val data: List<TronScanAccount>
)

data class TronScanAccount(
    val balance: BigInteger?,
    val trc20token_balances: List<TronScanTrc20TokenBalance>?
)

data class TronScanTrc20TokenBalance(
    @SerializedName("tokenId") val tokenId: String,
    val balance: String
)

data class TronScanTransactionsResponse(
    val data: List<TronScanTransaction>
)

data class TronScanTransaction(
    val hash: String?,
    val block: Long?,
    val timestamp: Long?,
    val ownerAddress: String?,
    val toAddress: String?,
    val contractType: Int?,
    val contractRet: String?,
    val fee: Long?,
    val contractData: TronScanContractData?
)

data class TronScanContractData(
    val amount: String?,
    val asset_name: String?,
    val data: String?,
    val contract_address: String?,
    val call_value: String?
)

data class TronScanTrc20Response(
    val token_transfers: List<TronScanTrc20Transfer>
)

data class TronScanTrc20Transfer(
    val transaction_id: String?,
    val block_ts: Long?,
    val from_address: String?,
    val to_address: String?,
    val quant: String?,
    @SerializedName("contract_address") val contractAddress: String?,
    val tokenInfo: TronScanTokenInfo?
)

data class TronScanTokenInfo(
    val tokenName: String?,
    val tokenAbbr: String?,
    val tokenDecimal: Int?
)
