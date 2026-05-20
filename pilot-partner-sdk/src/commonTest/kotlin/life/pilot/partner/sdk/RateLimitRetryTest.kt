package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RateLimitRetryTest {

    @Test fun retries_on_429_then_succeeds() = runTest {
        // 1x 429 then 200 — within the SDK's default maxRetries=2, this
        // exercises the retry path end-to-end through the public API.
        val responses = MockResponses()
        responses.enqueue {
            respondJson(
                """{"code":"RATE_LIMITED","message":"slow","retryAfterSeconds":1}""",
                HttpStatusCode.TooManyRequests,
                extraHeaders = mapOf("Retry-After" to "1"),
            )
        }
        responses.enqueue { respondJson("""{"events":[],"nextCursor":null}""") }

        val page = mockClient(responses).events.list()

        assertThat(page.events).hasSize(0)
        assertThat(responses.requests).hasSize(2)
    }
}
