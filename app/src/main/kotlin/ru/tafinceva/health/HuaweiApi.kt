package ru.tafinceva.health

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ──────────────────────────────────────────────────────────────
// Retrofit interfaces
// ──────────────────────────────────────────────────────────────

interface HuaweiAuthApi {

    /** Exchange authorization code for tokens */
    @FormUrlEncoded
    @POST("oauth2/v3/token")
    suspend fun exchangeCode(
        @Field("grant_type")    grantType: String    = "authorization_code",
        @Field("client_id")     clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code")          code: String,
        @Field("redirect_uri")  redirectUri: String
    ): HuaweiTokenResponse

    /** Refresh access token */
    @FormUrlEncoded
    @POST("oauth2/v3/token")
    suspend fun refreshToken(
        @Field("grant_type")    grantType: String    = "refresh_token",
        @Field("client_id")     clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String
    ): HuaweiTokenResponse
}

interface HuaweiHealthApi {

    @POST("healthkit/v1/sampleset/query")
    suspend fun querySampleSet(
        @Header("Authorization") authorization: String,
        @Body request: HealthQueryRequest
    ): HealthQueryResponse
}

// ──────────────────────────────────────────────────────────────
// Factory
// ──────────────────────────────────────────────────────────────

object HuaweiApiFactory {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val authApi: HuaweiAuthApi = Retrofit.Builder()
        .baseUrl("https://oauth2.cloud.huawei.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HuaweiAuthApi::class.java)

    val healthApi: HuaweiHealthApi = Retrofit.Builder()
        .baseUrl("https://health-api.cloud.huawei.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(HuaweiHealthApi::class.java)
}
