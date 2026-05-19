package life.pilot.partner.sdk.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps every request with the headers required by every partner-API route:
 *   - `X-API-Key`            (always)
 *   - `X-Organization-UUID`  (always)
 *   - `X-Gateway-Secret`     (only on dev/sandbox without Oathkeeper)
 */
internal class RequiredHeadersInterceptor(
    private val apiKey: String,
    private val organizationUuid: String,
    private val gatewaySecret: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .header("X-Organization-UUID", organizationUuid)
        if (gatewaySecret != null) {
            builder.header("X-Gateway-Secret", gatewaySecret)
        }
        return chain.proceed(builder.build())
    }
}
