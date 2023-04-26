package io.horizontalsystems.tronkit

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.logging.Logger

class TronGridService(
    network: Network,
    apiKey: String?
) {
    private val baseUrl = when (network) {
        Network.Mainnet -> "https://api.trongrid.io/v1/"
        Network.ShastaTestnet -> "https://api.shasta.trongrid.io/v1/"
        Network.NileTestnet -> " https://nile.trongrid.io/v1/"
    }
    private val logger = Logger.getLogger("TronGridService")
    private val service: TronGridServiceAPI

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

        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
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

    private interface TronGridServiceAPI {

        @GET("accounts/{address}")
        suspend fun accountInfo(
            @Path("address") address: String
        ): AccountInfoResponse

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
