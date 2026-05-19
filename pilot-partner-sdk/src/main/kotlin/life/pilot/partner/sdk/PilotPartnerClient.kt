package life.pilot.partner.sdk

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import life.pilot.partner.sdk.api.ClaimsApi
import life.pilot.partner.sdk.api.EventsApi
import life.pilot.partner.sdk.api.HealthApi
import life.pilot.partner.sdk.api.OrdersApi
import life.pilot.partner.sdk.auth.RequiredHeadersInterceptor
import life.pilot.partner.sdk.http.ErrorMappingInterceptor
import life.pilot.partner.sdk.http.RateLimitRetryInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Public entrypoint for the Pilot Partner Inventory API.
 *
 * ```kotlin
 * val client = PilotPartnerClient.builder()
 *     .apiKey("pk_live_...")
 *     .organizationUuid("…")
 *     .environment(PartnerEnvironment.SANDBOX)
 *     .build()
 *
 * val list = client.events.list()
 * val inv  = client.events.inventory(eventUuid)
 * val claim = client.claims.create(eventUuid, IdempotencyKey.generate(), req)
 * ```
 *
 * Thread-safe. Reuse a single instance per (apiKey, organization, environment).
 */
class PilotPartnerClient private constructor(
    private val retrofit: Retrofit,
    val httpClient: OkHttpClient,
    val json: Json,
) {
    val events: EventsApi by lazy { retrofit.create(EventsApi::class.java) }
    val claims: ClaimsApi by lazy { retrofit.create(ClaimsApi::class.java) }
    val orders: OrdersApi by lazy { retrofit.create(OrdersApi::class.java) }
    val health: HealthApi by lazy { retrofit.create(HealthApi::class.java) }

    /** Release HTTP resources held by the underlying OkHttp client. */
    fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        httpClient.cache?.close()
    }

    companion object {
        fun builder(): Builder = Builder()
    }

    class Builder internal constructor() {
        private var apiKey: String? = null
        private var organizationUuid: String? = null
        private var gatewaySecret: String? = null
        private var baseUrl: String = PartnerEnvironment.PRODUCTION.baseUrl
        private var callTimeoutSec: Long = 30
        private var connectTimeoutSec: Long = 10
        private var loggingLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE
        private var maxRateLimitRetries: Int = 2
        private var clientOverrides: ((OkHttpClient.Builder) -> Unit)? = null
        private val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }

        fun apiKey(value: String) = apply { apiKey = value }
        fun organizationUuid(value: String) = apply { organizationUuid = value }
        fun gatewaySecret(value: String?) = apply { gatewaySecret = value }

        fun environment(env: PartnerEnvironment) = apply { baseUrl = env.baseUrl }
        fun baseUrl(url: String) = apply { baseUrl = PartnerEnvironment.custom(url) }

        fun callTimeout(seconds: Long) = apply { callTimeoutSec = seconds }
        fun connectTimeout(seconds: Long) = apply { connectTimeoutSec = seconds }
        fun logging(level: HttpLoggingInterceptor.Level) = apply { loggingLevel = level }
        fun maxRateLimitRetries(value: Int) = apply { maxRateLimitRetries = value }

        /** Hook for partners that need to add their own interceptors / cache / certificate pinner. */
        fun configureHttpClient(block: (OkHttpClient.Builder) -> Unit) = apply {
            clientOverrides = block
        }

        fun build(): PilotPartnerClient {
            val key = requireNotNull(apiKey) { "apiKey is required" }
            val org = requireNotNull(organizationUuid) { "organizationUuid is required" }

            // Order matters: outer→inner. Required adds headers; ErrorMapping
            // wraps RateLimit so retries get to inspect the 429 before mapping.
            val httpBuilder = OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
                .addInterceptor(RequiredHeadersInterceptor(key, org, gatewaySecret))
                .addInterceptor(ErrorMappingInterceptor())
                .addInterceptor(RateLimitRetryInterceptor(maxRetries = maxRateLimitRetries))
                .also { b ->
                    if (loggingLevel != HttpLoggingInterceptor.Level.NONE) {
                        b.addInterceptor(HttpLoggingInterceptor().apply { level = loggingLevel })
                    }
                }
            clientOverrides?.invoke(httpBuilder)
            val client = httpBuilder.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return PilotPartnerClient(retrofit, client, json)
        }
    }
}
