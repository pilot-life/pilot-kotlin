package life.pilot.partner.sdk.http

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reads non-2xx responses, converts the JSON body to a typed
 * [life.pilot.partner.sdk.error.PartnerException] and throws.
 *
 * Sits BEFORE retry/auth interceptors in the OkHttp chain so 2xx flows
 * pass through untouched and 5xx/429 still bubble out as exceptions.
 */
internal class ErrorMappingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful || response.code == 304) {
            return response
        }
        val body = response.peekBody(MAX_BODY_PEEK).string()
        throw ErrorMapping.toException(response, body).also { response.close() }
    }

    private companion object {
        const val MAX_BODY_PEEK = 1L * 1024L * 1024L
    }
}
