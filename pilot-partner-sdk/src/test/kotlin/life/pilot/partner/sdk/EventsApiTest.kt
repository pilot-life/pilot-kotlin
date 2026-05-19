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
                    {"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z","venueName":"Venue X","imageUrl":"https://cdn.pilot.life/evt/a.jpg"},
                    {"eventUUID":"b","name":"NoArt","startDate":"2026-07-01T20:00:00Z","endDate":"2026-07-02T00:00:00Z","venueName":null,"imageUrl":null}
                  ],
                  "nextCursor": "cur-2"
                }
                """,
            ),
        )

        val list = mockClient(server).events.list()
        assertThat(list.events).hasSize(2)
        assertThat(list.events[0].venueName).isEqualTo("Venue X")
        assertThat(list.events[0].imageUrl).isEqualTo("https://cdn.pilot.life/evt/a.jpg")
        assertThat(list.events[1].imageUrl).isEqualTo(null)
        assertThat(list.nextCursor).isEqualTo("cur-2")
    }

    @Test fun `list tolerates events with imageUrl omitted entirely`() = runTest {
        // Backwards-compat path: older deployments may not yet emit imageUrl.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"events":[{"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z"}],"nextCursor":null}""",
            ),
        )

        val list = mockClient(server).events.list()
        assertThat(list.events[0].imageUrl).isEqualTo(null)
    }

    @Test fun `event detail parses imageUrl`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z",
                  "venueName":"Echo","imageUrl":"https://cdn.pilot.life/evt/a-hero.jpg",
                  "description":"Long blurb","shortDescription":"Short"
                }
                """,
            ),
        )

        val detail = mockClient(server).events.get("a")
        assertThat(detail.imageUrl).isEqualTo("https://cdn.pilot.life/evt/a-hero.jpg")
        assertThat(detail.shortDescription).isEqualTo("Short")
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
