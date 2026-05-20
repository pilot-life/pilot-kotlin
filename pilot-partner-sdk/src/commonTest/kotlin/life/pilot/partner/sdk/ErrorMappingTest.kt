package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.error.PartnerException
import life.pilot.partner.sdk.model.CheckoutPatron
import life.pilot.partner.sdk.model.CheckoutRequest
import life.pilot.partner.sdk.model.ClaimCreateRequest
import life.pilot.partner.sdk.model.ClaimItemRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ErrorMappingTest {

    @Test fun fourOhFour_maps_to_NotFound() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("""{"code":"NOT_FOUND","message":"Event not found"}""", HttpStatusCode.NotFound) }
        }
        val ex = assertFailsWith<PartnerException.NotFound> {
            mockClient(responses).events.get("missing")
        }
        assertThat(ex.message).isEqualTo("Event not found")
    }

    @Test fun fourOhNine_SOLD_OUT_maps_to_SoldOut_with_ticketTypeUUID() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("""{"code":"SOLD_OUT","message":"Ticket type is sold out.","ticketTypeUUID":"tt-1"}""", HttpStatusCode.Conflict) }
        }
        val ex = assertFailsWith<PartnerException.SoldOut> {
            mockClient(responses).claims.create(
                eventUuid = "e",
                idempotencyKey = "k",
                body = ClaimCreateRequest(items = listOf(ClaimItemRequest("tt-1", 1))),
            )
        }
        assertThat(ex.ticketTypeUUID).isEqualTo("tt-1")
    }

    @Test fun fourOhNine_IDEMPOTENCY_CONFLICT_maps_to_IdempotencyConflict() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("""{"code":"IDEMPOTENCY_CONFLICT","message":"replayed with different body"}""", HttpStatusCode.Conflict) }
        }
        assertFailsWith<PartnerException.IdempotencyConflict> {
            mockClient(responses).claims.create(
                eventUuid = "e",
                idempotencyKey = "k",
                body = ClaimCreateRequest(items = listOf(ClaimItemRequest("tt", 1))),
            )
        }
    }

    @Test fun fourTen_CLAIM_EXPIRED_maps_with_status() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("""{"code":"CLAIM_EXPIRED","message":"claim expired","status":"EXPIRED","ticketTypeUUID":"tt-2"}""", HttpStatusCode.Gone) }
        }
        val ex = assertFailsWith<PartnerException.ClaimExpired> {
            mockClient(responses).claims.checkout(
                claimId = "c",
                idempotencyKey = "k",
                body = CheckoutRequest(patron = CheckoutPatron(email = "x@y.z")),
            )
        }
        assertThat(ex.status).isEqualTo("EXPIRED")
        assertThat(ex.ticketTypeUUID).isEqualTo("tt-2")
    }

    @Test fun fourTwoNine_maps_to_RateLimited_with_retryAfterSeconds() = runTest {
        val responses = MockResponses()
        // 3x 429 enqueued; with maxRetries=2 the 3rd one is what the SDK throws on.
        repeat(3) {
            responses.enqueue {
                respondJson(
                    """{"code":"RATE_LIMITED","message":"slow down","retryAfterSeconds":10}""",
                    HttpStatusCode.TooManyRequests,
                    extraHeaders = mapOf("Retry-After" to "10"),
                )
            }
        }
        val ex = assertFailsWith<PartnerException.RateLimited> {
            mockClient(responses).events.list()
        }
        assertThat(ex.retryAfterSeconds).isEqualTo(10)
    }

    @Test fun unknown_server_error_maps_to_Server() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("""{"code":"BOOM","message":"oops"}""", HttpStatusCode.InternalServerError) }
        }
        val ex = assertFailsWith<PartnerException> { mockClient(responses).events.list() }
        assertThat(ex).isInstanceOf(PartnerException.Server::class)
        assertThat(ex.httpStatus).isEqualTo(500)
    }
}
