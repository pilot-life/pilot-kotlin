package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.error.PartnerException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ErrorMappingTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer().also { it.start() } }
    @AfterEach fun tearDown() { server.shutdown() }

    @Test fun `404 maps to NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"NOT_FOUND","message":"Event not found"}"""))
        val ex = assertThrows<PartnerException.NotFound> {
            mockClient(server).events.get("missing")
        }
        assertThat(ex.message).isEqualTo("Event not found")
    }

    @Test fun `409 SOLD_OUT maps to SoldOut with ticketTypeUUID`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code":"SOLD_OUT","message":"Ticket type is sold out.","ticketTypeUUID":"tt-1"}""",
            ),
        )
        val ex = assertThrows<PartnerException.SoldOut> {
            mockClient(server).claims.create(
                eventUuid = "e",
                idempotencyKey = "k",
                body = life.pilot.partner.sdk.model.ClaimCreateRequest(
                    items = listOf(life.pilot.partner.sdk.model.ClaimItemRequest("tt-1", 1)),
                ),
            )
        }
        assertThat(ex.ticketTypeUUID).isEqualTo("tt-1")
    }

    @Test fun `409 IDEMPOTENCY_CONFLICT maps to IdempotencyConflict`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code":"IDEMPOTENCY_CONFLICT","message":"replayed with different body"}""",
            ),
        )
        assertThrows<PartnerException.IdempotencyConflict> {
            mockClient(server).claims.create(
                eventUuid = "e",
                idempotencyKey = "k",
                body = life.pilot.partner.sdk.model.ClaimCreateRequest(
                    items = listOf(life.pilot.partner.sdk.model.ClaimItemRequest("tt", 1)),
                ),
            )
        }
    }

    @Test fun `410 CLAIM_EXPIRED maps with status`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(410).setBody(
                """{"code":"CLAIM_EXPIRED","message":"claim expired","status":"EXPIRED","ticketTypeUUID":"tt-2"}""",
            ),
        )
        val ex = assertThrows<PartnerException.ClaimExpired> {
            mockClient(server).claims.checkout(
                claimId = "c",
                idempotencyKey = "k",
                body = life.pilot.partner.sdk.model.CheckoutRequest(
                    patron = life.pilot.partner.sdk.model.CheckoutPatron(email = "x@y.z"),
                ),
            )
        }
        assertThat(ex.status).isEqualTo("EXPIRED")
        assertThat(ex.ticketTypeUUID).isEqualTo("tt-2")
    }

    @Test fun `429 maps to RateLimited with retryAfterSeconds`() = runTest {
        // Two 429s then a 200, but max retries == 2 so the third call succeeds
        repeat(3) {
            server.enqueue(
                MockResponse().setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"code":"RATE_LIMITED","message":"slow down","retryAfterSeconds":10}"""),
            )
        }
        val ex = assertThrows<PartnerException.RateLimited> {
            mockClient(server).events.list()
        }
        assertThat(ex.retryAfterSeconds).isEqualTo(10)
    }

    @Test fun `unknown server error maps to Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"code":"BOOM","message":"oops"}"""))
        val ex = assertThrows<PartnerException> { mockClient(server).events.list() }
        assertThat(ex).isInstanceOf(PartnerException.Server::class)
        assertThat(ex.httpStatus).isEqualTo(500)
    }
}
