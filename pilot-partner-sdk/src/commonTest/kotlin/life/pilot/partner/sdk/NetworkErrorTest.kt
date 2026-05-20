package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isNotNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.error.PartnerException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NetworkErrorTest {

    @Test fun engine_throwing_IOException_maps_to_PartnerException_Network() = runTest {
        // Ktor's MockEngine lets us throw transport-level exceptions
        // directly. That's the same path SocketTimeoutException /
        // UnknownHostException flow through in production — they all
        // surface as IOException-shaped throws inside the engine.
        val engine = MockEngine { throw IOException("network down") }

        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("https://mock.test/partner/v1/")
            .engine(engine)
            .build()

        val ex = assertFailsWith<PartnerException.Network> { client.events.list() }
        assertThat(ex.cause).isNotNull()
    }

    @Test fun engine_throwing_RuntimeException_also_wraps_as_Network() = runTest {
        // Defensive: anything weird inside the chain should still hit
        // PartnerException.Network rather than escape to the consumer.
        val engine = MockEngine {
            throw RuntimeException("broken pipe")
        }

        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("https://mock.test/partner/v1/")
            .engine(engine)
            .build()

        val ex = assertFailsWith<PartnerException.Network> { client.events.list() }
        assertThat(ex.cause).isNotNull()
    }

    @Test fun successful_call_returns_normally_no_wrapping() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"events":[],"nextCursor":null}"""),
                status = io.ktor.http.HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(
                    io.ktor.http.HttpHeaders.ContentType,
                    "application/json",
                ),
            )
        }
        val client = PilotPartnerClient.builder()
            .apiKey("k").organizationUuid("o")
            .baseUrl("https://mock.test/partner/v1/")
            .engine(engine)
            .build()
        // Must not throw — this is the sanity check that the mapping
        // plugin doesn't accidentally wrap successes.
        client.events.list()
    }
}
