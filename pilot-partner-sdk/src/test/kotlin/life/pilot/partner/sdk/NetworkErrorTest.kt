package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.error.PartnerException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class NetworkErrorTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer().also { it.start() } }
    @AfterEach fun tearDown() { server.shutdown() }

    @Test fun `read timeout maps to PartnerException Network with cause`() = runTest {
        // NO_RESPONSE keeps the socket open without ever writing. With a
        // short readTimeout, OkHttp throws SocketTimeoutException from
        // inside the interceptor chain — the exact production failure mode.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl(server.url("/").toString())
            .configureHttpClient { it.readTimeout(500, TimeUnit.MILLISECONDS) }
            .build()

        val ex = assertThrows<PartnerException.Network> { client.events.list() }
        assertThat(ex.cause).isNotNull().isInstanceOf(SocketTimeoutException::class)
        assertThat(ex.message ?: "").contains("Network timeout")
    }

    @Test fun `unknown host maps to PartnerException Network`() = runTest {
        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("http://no-such-host-pilot-test.invalid/")
            .connectTimeout(1)
            .build()

        val ex = assertThrows<PartnerException.Network> { client.events.list() }
        assertThat(ex.cause).isNotNull().isInstanceOf(UnknownHostException::class)
    }

    @Test fun `connection refused maps to PartnerException Network`() = runTest {
        // Bind a server then shut it down so the port is closed.
        val closedPort = server.port
        server.shutdown()

        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("http://127.0.0.1:$closedPort/")
            .connectTimeout(1)
            .build()

        assertThrows<PartnerException.Network> { client.events.list() }
    }
}
