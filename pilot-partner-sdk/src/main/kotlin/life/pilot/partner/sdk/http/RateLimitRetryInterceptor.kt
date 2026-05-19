package life.pilot.partner.sdk.http

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Honors `Retry-After` on 429 responses, up to `maxRetries` extra attempts.
 *
 * Reads either the `Retry-After` HTTP header (seconds) or the
 * `retryAfterSeconds` field on the JSON body. Per the API contract, both
 * mirror the same value (see openapi.yaml `RATE_LIMITED` examples).
 */
internal class RateLimitRetryInterceptor(
    private val maxRetries: Int = 2,
    private val maxWaitMs: Long = 5_000L,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response = chain.proceed(chain.request())
        while (response.code == 429 && attempt < maxRetries) {
            val waitMs = parseRetryAfterMs(response)
            response.close()
            if (waitMs > maxWaitMs) {
                return chain.proceed(chain.request())
            }
            sleeper(waitMs)
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }

    private fun parseRetryAfterMs(response: Response): Long {
        val header = response.header("Retry-After")?.toLongOrNull()
        return ((header ?: 1L).coerceAtLeast(1L)) * 1_000L
    }
}
