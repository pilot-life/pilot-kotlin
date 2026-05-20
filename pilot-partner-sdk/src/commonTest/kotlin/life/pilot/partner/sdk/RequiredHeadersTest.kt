package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.model.ClaimCreateRequest
import life.pilot.partner.sdk.model.ClaimItemRequest
import kotlin.test.Test

class RequiredHeadersTest {

    @Test fun every_request_carries_X_API_Key_and_X_Organization_UUID() = runTest {
        val responses = MockResponses()
        responses.enqueue { respondJson(EVENT_LIST_JSON) }
        val client = mockClient(responses)

        client.events.list()

        val req = responses.requests.single()
        assertThat(req.headers["X-API-Key"]).isEqualTo("test-key")
        assertThat(req.headers["X-Organization-UUID"]).isEqualTo("11111111-1111-1111-1111-111111111111")
    }

    @Test fun gateway_secret_omitted_by_default_and_included_when_configured() = runTest {
        val r1 = MockResponses().also { it.enqueue { respondJson(EVENT_LIST_JSON) } }
        mockClient(r1).events.list()
        assertThat(r1.requests.single().headers["X-Gateway-Secret"]).isEqualTo(null)

        val r2 = MockResponses().also { it.enqueue { respondJson(EVENT_LIST_JSON) } }
        PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("https://mock.test/partner/v1/")
            .gatewaySecret("gw-secret")
            .engine(r2.engine())
            .build()
            .events.list()
        assertThat(r2.requests.single().headers["X-Gateway-Secret"]).isNotNull().isEqualTo("gw-secret")
    }

    @Test fun claim_create_sends_Idempotency_Key_header() = runTest {
        val responses = MockResponses()
        responses.enqueue { respondJson(CLAIM_CREATE_JSON, HttpStatusCode.Created) }
        val client = mockClient(responses)

        client.claims.create(
            eventUuid = "e",
            idempotencyKey = "abc-key",
            body = ClaimCreateRequest(items = listOf(ClaimItemRequest("tt", 1))),
        )

        assertThat(responses.requests.single().headers["Idempotency-Key"]).isEqualTo("abc-key")
    }

    companion object {
        const val EVENT_LIST_JSON = """{"events":[],"nextCursor":null}"""
        const val CLAIM_CREATE_JSON = """
            {
              "claimId": "c1",
              "claimIds": ["c1"],
              "expiresAt": "2026-05-19T11:00:00Z",
              "items": []
            }
        """
    }
}
