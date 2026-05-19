package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.http.RateLimitRetryInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RateLimitRetryTest {
    private lateinit var server: MockWebServer
    private val slept = mutableListOf<Long>()

    @BeforeEach fun setUp() { server = MockWebServer().also { it.start() }; slept.clear() }
    @AfterEach fun tearDown() { server.shutdown() }

    @Test fun `retries on 429 honoring Retry-After then succeeds`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                .setBody("""{"code":"RATE_LIMITED","message":"slow","retryAfterSeconds":1}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"events":[],"nextCursor":null}"""))

        val client = OkHttpClient.Builder()
            .addInterceptor(RateLimitRetryInterceptor(maxRetries = 2, sleeper = { slept.add(it) }))
            .build()

        val resp = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

        assertThat(resp.code).isEqualTo(200)
        assertThat(slept).isEqualTo(listOf(1000L))
        resp.close()
    }
}
