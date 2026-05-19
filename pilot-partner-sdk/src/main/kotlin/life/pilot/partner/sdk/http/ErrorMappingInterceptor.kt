package life.pilot.partner.sdk.http

import life.pilot.partner.sdk.error.PartnerException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Maps HTTP failures into typed [PartnerException]s.
 *
 *   - Non-2xx responses are read as JSON and rethrown as a domain-typed
 *     PartnerException (NotFound, SoldOut, ClaimExpired, …).
 *   - Transport failures from below in the chain (connect timeout, read
 *     timeout, DNS, TLS, broken pipe, etc.) are caught and rewrapped as
 *     [PartnerException.Network] so consumers only ever need to catch
 *     PartnerException — they never see raw IOException leaking through.
 *
 * Sits OUTSIDE RateLimitRetryInterceptor in the OkHttp chain so 429
 * retries get to inspect the raw response before mapping.
 */
internal class ErrorMappingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response: Response = try {
            chain.proceed(request)
        } catch (e: PartnerException) {
            // Already typed (e.g. from RateLimit retry path). Let it through.
            throw e
        } catch (e: IOException) {
            throw PartnerException.Network(
                "${describe(e)} (${request.method} ${request.url})",
                e,
            )
        }
        if (response.isSuccessful || response.code == 304) {
            return response
        }
        val body = response.peekBody(MAX_BODY_PEEK).string()
        throw ErrorMapping.toException(response, body).also { response.close() }
    }

    private fun describe(e: IOException): String =
        when (e) {
            is java.net.SocketTimeoutException -> "Network timeout"
            is java.net.UnknownHostException -> "Unknown host"
            is java.net.ConnectException -> "Connection refused"
            is javax.net.ssl.SSLException -> "TLS error: ${e.message ?: e.javaClass.simpleName}"
            else -> e.message ?: e.javaClass.simpleName
        }

    private companion object {
        const val MAX_BODY_PEEK = 1L * 1024L * 1024L
    }
}
