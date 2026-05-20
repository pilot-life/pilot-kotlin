package life.pilot.partner.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

/**
 * Mock-engine helpers for the SDK's KMP tests.
 *
 * Each test enqueues a sequence of canned responses. The handler closure
 * pops the next response per request. `respondJson(...)` is the
 * most-used shortcut.
 */
internal class MockResponses {
    private val queue: ArrayDeque<MockRequestHandleScope.(HttpRequestData) -> HttpResponseData> = ArrayDeque()
    val requests: MutableList<HttpRequestData> = mutableListOf()

    fun enqueue(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        queue.add(handler)
    }

    fun engine(): MockEngine = MockEngine { request ->
        requests.add(request)
        val handler = queue.removeFirstOrNull()
            ?: error("MockEngine ran out of enqueued responses for ${request.method.value} ${request.url}")
        handler(request)
    }
}

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    extraHeaders: Map<String, String> = emptyMap(),
): HttpResponseData {
    val headerPairs = mutableListOf<Pair<String, List<String>>>(
        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
    )
    extraHeaders.forEach { (k, v) -> headerPairs.add(k to listOf(v)) }
    return respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(*headerPairs.toTypedArray()),
    )
}

internal fun mockClient(
    responses: MockResponses,
): PilotPartnerClient = PilotPartnerClient.builder()
    .apiKey("test-key")
    .organizationUuid("11111111-1111-1111-1111-111111111111")
    .baseUrl("https://mock.test/partner/v1/")
    .maxRateLimitRetries(2)
    .engine(responses.engine())
    .build()
