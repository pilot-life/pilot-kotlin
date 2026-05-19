package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventsApiTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer().also { it.start() } }
    @AfterEach fun tearDown() { server.shutdown() }

    @Test fun `list parses events and cursor`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "events": [
                    {"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z","venueName":"Venue X"}
                  ],
                  "nextCursor": "cur-2"
                }
                """,
            ),
        )

        val list = mockClient(server).events.list()
        assertThat(list.events).hasSize(1)
        assertThat(list.events[0].venueName).isEqualTo("Venue X")
        assertThat(list.nextCursor).isEqualTo("cur-2")
    }

    @Test fun `inventory returns ETag and snapshot`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("ETag", "\"W/abc\"")
                .setBody(
                    """
                    {
                      "event": {"eventUUID":"e","name":"Show","startDate":"2026-06-01T20:00:00Z"},
                      "ticketTypes": [
                        {"ticketTypeUUID":"tt-1","name":"GA","price":"50.00","totalCapacity":100,"remaining":42,"soldOut":false,"availableFrom":"2026-05-01T00:00:00Z","availableTo":"2026-06-01T20:00:00Z"}
                      ]
                    }
                    """,
                ),
        )

        val resp = mockClient(server).events.inventory("e")
        assertThat(resp.code()).isEqualTo(200)
        assertThat(resp.headers()["ETag"]).isNotNull().isEqualTo("\"W/abc\"")
        val snap = resp.body()!!
        assertThat(snap.ticketTypes).hasSize(1)
        assertThat(snap.ticketTypes[0].remaining).isEqualTo(42)
    }

    @Test fun `inventory 304 returns empty body and forwards If-None-Match`() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))

        val resp = mockClient(server).events.inventory("e", ifNoneMatch = "\"W/abc\"")
        assertThat(resp.code()).isEqualTo(304)

        val req = server.takeRequest()
        assertThat(req.getHeader("If-None-Match")).isEqualTo("\"W/abc\"")
    }
}
