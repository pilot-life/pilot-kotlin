package life.pilot.partner.sdk.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import life.pilot.partner.sdk.error.PartnerException

/**
 * Configures the client to defer non-2xx handling to [partnerRequest].
 *
 * Ktor's natural place to map status codes is `HttpResponseValidator`,
 * but on Kotlin/Native + Darwin a throw from inside `validateResponse`
 * can escape the Darwin global-queue dispatcher's libdispatch bridge —
 * libdispatch is C, K/N can't propagate an exception across that
 * boundary, and the runtime SIGABRTs the process. (Stack signature:
 * the throw originates in our lambda but happens during the body
 * writer's `flushAndClose` resume, not on the caller's continuation.)
 *
 * Instead we let non-2xx responses come back normally and check status
 * inside [partnerRequest], which is called from the API method's own
 * suspend frame — the throw lands on the caller's continuation and
 * propagates through coroutine machinery the normal way.
 */
internal fun HttpClientConfig<*>.installPartnerErrorMapping() {
    expectSuccess = false
}

/**
 * Replacement for `http.get(...) / http.post(...)`. Executes the
 * request, wraps transport-level failures as [PartnerException.Network],
 * and on non-2xx (and non-304) responses reads the body and throws the
 * typed [PartnerException] derived from status + `ErrorResponse.code`.
 *
 * Both throws happen on the API method's coroutine — never inside a
 * Ktor-internal writer/dispatcher block — which is the property we
 * need to keep iOS from terminating on a bad API key.
 */
internal suspend inline fun HttpClient.partnerRequest(
    crossinline block: HttpRequestBuilder.() -> Unit,
): HttpResponse {
    val response: HttpResponse = try {
        request { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: PartnerException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw PartnerException.Network("Request timeout: ${e.message ?: ""}", e)
    } catch (e: ConnectTimeoutException) {
        throw PartnerException.Network("Connection timeout: ${e.message ?: ""}", e)
    } catch (e: SocketTimeoutException) {
        throw PartnerException.Network("Network timeout: ${e.message ?: ""}", e)
    } catch (e: Throwable) {
        throw PartnerException.Network(
            "${e::class.simpleName ?: "Network error"}: ${e.message ?: ""}",
            e,
        )
    }
    val status = response.status.value
    if (status in 200..299 || status == 304) return response
    val body: String? = try { response.bodyAsText() } catch (_: Throwable) { null }
    throw ErrorMapping.toException(response, body)
}

internal suspend inline fun HttpClient.partnerGet(
    urlString: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = partnerRequest {
    method = HttpMethod.Get
    url(urlString)
    block()
}

internal suspend inline fun HttpClient.partnerPost(
    urlString: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = partnerRequest {
    method = HttpMethod.Post
    url(urlString)
    block()
}

internal suspend inline fun HttpClient.partnerDelete(
    urlString: String,
    crossinline block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = partnerRequest {
    method = HttpMethod.Delete
    url(urlString)
    block()
}
