package life.pilot.partner.sdk.http

import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import kotlinx.coroutines.CancellationException
import life.pilot.partner.sdk.error.PartnerException

/**
 * Installs typed-exception mapping on the [HttpClientConfig].
 *
 *   - Non-2xx (and non-304) responses are read as JSON and rethrown as a
 *     domain-typed PartnerException (NotFound, SoldOut, ClaimExpired, …).
 *   - Transport failures (connect/read timeout, DNS, broken pipe) are
 *     caught and rewrapped as [PartnerException.Network] so consumers
 *     only ever need to catch PartnerException.
 *
 * Uses Ktor's `HttpResponseValidator` block — the documented extension
 * point for response/exception handling in Ktor 3.x.
 */
internal fun HttpClientConfig<*>.installPartnerErrorMapping() {
    expectSuccess = false
    HttpResponseValidator {
        validateResponse { response: HttpResponse ->
            val status = response.status.value
            if (status in 200..299 || status == 304) return@validateResponse
            val body = try { response.bodyAsText() } catch (_: Throwable) { null }
            throw ErrorMapping.toException(response, body)
        }
        handleResponseExceptionWithRequest { cause, request ->
            when (cause) {
                is CancellationException -> throw cause
                is PartnerException -> throw cause
                is HttpRequestTimeoutException ->
                    throw PartnerException.Network(
                        "Request timeout (${request.method.value} ${request.url})", cause,
                    )
                is ConnectTimeoutException ->
                    throw PartnerException.Network(
                        "Connection timeout (${request.method.value} ${request.url})", cause,
                    )
                is SocketTimeoutException ->
                    throw PartnerException.Network(
                        "Network timeout (${request.method.value} ${request.url})", cause,
                    )
                else -> throw PartnerException.Network(
                    "${cause::class.simpleName ?: "Network error"}: ${cause.message ?: ""} (${request.method.value} ${request.url})",
                    cause,
                )
            }
        }
    }
}
