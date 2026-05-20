package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EventsApiTest {

    @Test fun list_parses_events_and_cursor() = runTest {
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "events": [
                        {"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z","venueName":"Venue X","imageUrl":"https://cdn.pilot.life/evt/a.jpg"},
                        {"eventUUID":"b","name":"NoArt","startDate":"2026-07-01T20:00:00Z","endDate":"2026-07-02T00:00:00Z","venueName":null,"imageUrl":null}
                      ],
                      "nextCursor": "cur-2"
                    }
                    """,
                )
            }
        }

        val list = mockClient(responses).events.list()
        assertThat(list.events).hasSize(2)
        assertThat(list.events[0].venueName).isEqualTo("Venue X")
        assertThat(list.events[0].imageUrl).isEqualTo("https://cdn.pilot.life/evt/a.jpg")
        assertThat(list.events[1].imageUrl).isEqualTo(null)
        assertThat(list.nextCursor).isEqualTo("cur-2")
    }

    @Test fun list_tolerates_events_with_imageUrl_omitted_entirely() = runTest {
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """{"events":[{"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z"}],"nextCursor":null}""",
                )
            }
        }
        val list = mockClient(responses).events.list()
        assertThat(list.events[0].imageUrl).isEqualTo(null)
    }

    @Test fun event_detail_parses_imageUrl() = runTest {
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z",
                      "venueName":"Echo","imageUrl":"https://cdn.pilot.life/evt/a-hero.jpg",
                      "description":"Long blurb","shortDescription":"Short"
                    }
                    """,
                )
            }
        }
        val detail = mockClient(responses).events.get("a")
        assertThat(detail.imageUrl).isEqualTo("https://cdn.pilot.life/evt/a-hero.jpg")
        assertThat(detail.shortDescription).isEqualTo("Short")
    }

    @Test fun inventory_returns_ETag_and_snapshot() = runTest {
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "event": {"eventUUID":"e","name":"Show","startDate":"2026-06-01T20:00:00Z"},
                      "ticketTypes": [
                        {"ticketTypeUUID":"tt-1","name":"GA","price":"50.00","totalCapacity":100,"remaining":42,"soldOut":false,"availableFrom":"2026-05-01T00:00:00Z","availableTo":"2026-06-01T20:00:00Z"}
                      ]
                    }
                    """,
                    extraHeaders = mapOf("ETag" to "\"W/abc\""),
                )
            }
        }
        val resp = mockClient(responses).events.inventory("e")
        assertThat(resp.code).isEqualTo(200)
        assertThat(resp.etag).isNotNull().isEqualTo("\"W/abc\"")
        val snap = resp.body!!
        assertThat(snap.ticketTypes).hasSize(1)
        assertThat(snap.ticketTypes[0].remaining).isEqualTo(42)
    }

    @Test fun inventory_304_forwards_If_None_Match() = runTest {
        val responses = MockResponses().also {
            it.enqueue { respondJson("", HttpStatusCode.NotModified) }
        }
        val resp = mockClient(responses).events.inventory("e", ifNoneMatch = "\"W/abc\"")
        assertThat(resp.code).isEqualTo(304)
        assertThat(responses.requests.single().headers["If-None-Match"]).isEqualTo("\"W/abc\"")
    }
}
