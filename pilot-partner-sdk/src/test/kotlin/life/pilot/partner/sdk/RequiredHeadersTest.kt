package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequiredHeadersTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer().also { it.start() } }
    @AfterEach fun tearDown() { server.shutdown() }

    @Test fun `every request carries X-API-Key and X-Organization-UUID`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(EVENT_LIST_JSON))
        val client = mockClient(server)

        client.events.list()

        val req = server.takeRequest()
        assertThat(req.getHeader("X-API-Key")).isEqualTo("test-key")
        assertThat(req.getHeader("X-Organization-UUID")).isEqualTo("11111111-1111-1111-1111-111111111111")
    }

    @Test fun `gateway secret omitted by default and included when configured`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(EVENT_LIST_JSON))
        mockClient(server).events.list()
        val first = server.takeRequest()
        assertThat(first.getHeader("X-Gateway-Secret")).isEqualTo(null)

        server.enqueue(MockResponse().setResponseCode(200).setBody(EVENT_LIST_JSON))
        PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl(server.url("/").toString())
            .gatewaySecret("gw-secret")
            .build()
            .events.list()
        val second = server.takeRequest()
        assertThat(second.getHeader("X-Gateway-Secret")).isNotNull().isEqualTo("gw-secret")
    }

    @Test fun `claim create sends Idempotency-Key header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(CLAIM_CREATE_JSON))
        val client = mockClient(server)

        client.claims.create(
            eventUuid = "e",
            idempotencyKey = "abc-key",
            body = life.pilot.partner.sdk.model.ClaimCreateRequest(
                items = listOf(life.pilot.partner.sdk.model.ClaimItemRequest("tt", 1)),
            ),
        )

        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo("abc-key")
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
