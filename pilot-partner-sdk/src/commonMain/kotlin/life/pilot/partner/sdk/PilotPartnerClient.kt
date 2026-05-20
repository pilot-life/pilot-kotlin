package life.pilot.partner.sdk

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import life.pilot.partner.sdk.api.ClaimsApi
import life.pilot.partner.sdk.api.EventsApi
import life.pilot.partner.sdk.api.HealthApi
import life.pilot.partner.sdk.api.OrdersApi
import life.pilot.partner.sdk.http.installPartnerErrorMapping

/**
 * Public entrypoint for the Pilot Partner Inventory API.
 *
 * Kotlin Multiplatform: works on Android, JVM, and iOS (via the
 * `PilotPartnerSdk.xcframework`). Engine selection is automatic —
 * OkHttp on JVM/Android, Darwin on iOS.
 *
 * ```kotlin
 * val client = PilotPartnerClient.builder()
 *     .apiKey("pk_live_...")
 *     .organizationUuid("...")
 *     .environment(PartnerEnvironment.SANDBOX)
 *     .build()
 *
 * val list  = client.events.list()
 * val inv   = client.events.inventory(eventUuid)
 * val claim = client.claims.create(eventUuid, IdempotencyKey.generate(), req)
 * ```
 *
 * Thread-safe. Reuse a single instance per (apiKey, organization, environment).
 */
class PilotPartnerClient internal constructor(
    val http: HttpClient,
    val json: Json,
) {
    val events: EventsApi by lazy { EventsApi(http) }
    val claims: ClaimsApi by lazy { ClaimsApi(http) }
    val orders: OrdersApi by lazy { OrdersApi(http) }
    val health: HealthApi by lazy { HealthApi(http) }

    /** Release HTTP resources held by the underlying Ktor client. */
    fun close() {
        http.close()
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
        private var loggingLevel: LogLevel = LogLevel.NONE
        private var maxRateLimitRetries: Int = 2
        private var clientOverrides: (HttpClientConfig<*>.() -> Unit)? = null
        private var engineOverride: HttpClientEngine? = null
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
        fun logging(level: LogLevel) = apply { loggingLevel = level }
        fun maxRateLimitRetries(value: Int) = apply { maxRateLimitRetries = value }

        /**
         * Hook for partners that need to add their own Ktor plugins,
         * configure a cert pinner via the engine block, etc. Runs LAST
         * so it can replace anything the SDK installed.
         */
        fun configureHttpClient(block: HttpClientConfig<*>.() -> Unit) = apply {
            clientOverrides = block
        }

        /** Test hook: swap in a `MockEngine` for KMP `MockEngine`-based tests. */
        fun engine(engine: HttpClientEngine) = apply { engineOverride = engine }

        fun build(): PilotPartnerClient {
            val key = requireNotNull(apiKey) { "apiKey is required" }
            val org = requireNotNull(organizationUuid) { "organizationUuid is required" }
            val urlBase = baseUrl
            val callTimeout = callTimeoutSec
            val connectTimeout = connectTimeoutSec
            val retries = maxRateLimitRetries
            val level = loggingLevel
            val gateway = gatewaySecret
            val jsonCfg = json
            val overrides = clientOverrides

            val config: HttpClientConfig<*>.() -> Unit = {
                install(ContentNegotiation) { json(jsonCfg) }
                install(Logging) { this.level = level }
                install(HttpTimeout) {
                    requestTimeoutMillis = callTimeout * 1000
                    connectTimeoutMillis = connectTimeout * 1000
                }
                install(HttpRequestRetry) {
                    maxRetries = retries
                    retryIf { _, response -> response.status.value == 429 }
                    delayMillis { attempt ->
                        // Cap exponential backoff at ~16s. Retry-After
                        // header is surfaced to consumers via
                        // PartnerException.RateLimited once retries
                        // are exhausted.
                        val capped = attempt.coerceAtMost(4)
                        1000L * (1L shl capped)
                    }
                }
                defaultRequest {
                    url { takeFrom(URLBuilder().takeFrom(urlBase)) }
                    headers.append("X-API-Key", key)
                    headers.append("X-Organization-UUID", org)
                    gateway?.let { headers.append("X-Gateway-Secret", it) }
                }
                installPartnerErrorMapping()
                overrides?.invoke(this)
            }

            val http = engineOverride?.let { HttpClient(it, config) } ?: HttpClient(config)
            return PilotPartnerClient(http, jsonCfg)
        }
    }
}
