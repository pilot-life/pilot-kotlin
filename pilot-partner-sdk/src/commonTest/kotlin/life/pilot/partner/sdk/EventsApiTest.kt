package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import life.pilot.partner.sdk.model.EventTime
import kotlin.test.Test

class EventsApiTest {

    @Test fun list_parses_events_and_cursor() = runTest {
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "events": [
                        {"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z","times":[{"startAt":"2026-06-01T20:00:00Z","endAt":"2026-06-02T00:00:00Z"}],"venueName":"Venue X","eventType":"Runway Show","featuredEvent":true,"imageUrl":"https://cdn.pilot.life/evt/a.jpg"},
                        {"eventUUID":"b","name":"NoArt","startDate":"2026-07-01T20:00:00Z","endDate":"2026-07-02T00:00:00Z","times":[],"venueName":"Hosted By Us","eventType":null,"featuredEvent":false,"imageUrl":null}
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
        assertThat(list.events[0].eventType).isEqualTo("Runway Show")
        assertThat(list.events[0].featuredEvent).isEqualTo(true)
        assertThat(list.events[0].times).isEqualTo(listOf(EventTime("2026-06-01T20:00:00Z", "2026-06-02T00:00:00Z")))
        assertThat(list.events[1].eventType).isEqualTo(null)
        assertThat(list.events[1].featuredEvent).isEqualTo(false)
        assertThat(list.events[1].imageUrl).isEqualTo(null)
        assertThat(list.nextCursor).isEqualTo("cur-2")
    }

    @Test fun list_tolerates_pre_featured_deployments_omitting_new_fields() = runTest {
        // times / eventType / featuredEvent absent → defaults, not a crash.
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """{"events":[{"eventUUID":"a","name":"Show","startDate":"2026-06-01T20:00:00Z","endDate":"2026-06-02T00:00:00Z","venueName":"V"}],"nextCursor":null}""",
                )
            }
        }
        val list = mockClient(responses).events.list()
        assertThat(list.events[0].times).isEqualTo(emptyList())
        assertThat(list.events[0].eventType).isEqualTo(null)
        assertThat(list.events[0].featuredEvent).isEqualTo(false)
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
                      "times":[{"startAt":"2026-06-01T20:00:00Z","endAt":"2026-06-02T00:00:00Z"}],
                      "venueName":"Echo","eventType":"Showroom","featuredEvent":true,
                      "imageUrl":"https://cdn.pilot.life/evt/a-hero.jpg",
                      "description":"Long blurb","shortDescription":"Short"
                    }
                    """,
                )
            }
        }
        val detail = mockClient(responses).events.get("a")
        assertThat(detail.imageUrl).isEqualTo("https://cdn.pilot.life/evt/a-hero.jpg")
        assertThat(detail.shortDescription).isEqualTo("Short")
        assertThat(detail.eventType).isEqualTo("Showroom")
        assertThat(detail.featuredEvent).isEqualTo(true)
        assertThat(detail.times).isEqualTo(listOf(EventTime("2026-06-01T20:00:00Z", "2026-06-02T00:00:00Z")))
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

    @Test fun inventory_parses_rta_and_registrationTicketTypes() = runTest {
        // Real JSON shape from feat/PIL-2370-pr1-foundation (April 2026).
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "event": {
                        "eventUUID": "f00d78d9-fda7-4c0a-8d6f-fabf0cbbb7cc",
                        "name": "Fusion Fashion",
                        "startDate": "2042-05-26T17:00:00.000Z",
                        "rta": {
                          "enabled": true,
                          "banner": "",
                          "showBanner": false,
                          "additionalGuestLimit": 5,
                          "enableAdditionalGuest": true,
                          "designers": [],
                          "occupations": [
                            {"id": 7, "name": "Entrepreneur"},
                            {"id": 3, "name": "Influencer"},
                            {"id": 1, "name": "Press / Media"}
                          ],
                          "socialMedia": [
                            {"id": 5, "name": "Facebook", "url": "https://facebook.com"},
                            {"id": 2, "name": "Instagram", "url": "https://instagram.com"}
                          ]
                        }
                      },
                      "ticketTypes": [
                        {"ticketTypeUUID":"9cd7acca","name":"GA L","description":"row 2 left","price":"50","totalCapacity":150,"remaining":110,"soldOut":false,"availableFrom":"2025-12-11T20:14:00.000Z","availableTo":"2042-05-28T07:00:00.000Z"}
                      ],
                      "registrationTicketTypes": [
                        {"ticketTypeUUID":"9ce7c538","name":"Registration test","description":"reg desc","price":"0","totalCapacity":10000,"remaining":9956,"soldOut":false,"availableFrom":"2026-03-17T20:00:00.000Z","availableTo":"2026-12-17T20:00:00.000Z"},
                        {"ticketTypeUUID":"9ce825c8","name":"Test Add Ons","description":"YESSS","price":"0","totalCapacity":200,"remaining":199,"soldOut":false,"availableFrom":"2026-03-26T11:52:00.000Z","availableTo":"2030-05-28T07:00:00.000Z"}
                      ]
                    }
                    """,
                )
            }
        }

        val resp = mockClient(responses).events.inventory("f00d78d9-fda7-4c0a-8d6f-fabf0cbbb7cc")
        val snap = resp.body!!

        // RTA block
        val rta = snap.event.rta!!
        assertThat(rta.enabled).isEqualTo(true)
        assertThat(rta.additionalGuestLimit).isEqualTo(5)
        assertThat(rta.enableAdditionalGuest).isEqualTo(true)
        assertThat(rta.occupations.map { it.name })
            .isEqualTo(listOf("Entrepreneur", "Influencer", "Press / Media"))
        assertThat(rta.socialMedia.map { it.name })
            .isEqualTo(listOf("Facebook", "Instagram"))
        assertThat(rta.socialMedia[0].url).isEqualTo("https://facebook.com")

        // Registration tickets
        assertThat(snap.registrationTicketTypes).hasSize(2)
        assertThat(snap.registrationTicketTypes[0].name).isEqualTo("Registration test")
        assertThat(snap.registrationTicketTypes[1].name).isEqualTo("Test Add Ons")

        // Regular tickets unaffected
        assertThat(snap.ticketTypes).hasSize(1)
        assertThat(snap.ticketTypes[0].name).isEqualTo("GA L")
    }

    @Test fun inventory_tolerates_rta_omitted_and_registrationTicketTypes_omitted() = runTest {
        // Backwards-compat path: older deployments may not yet emit RTA
        // or registrationTicketTypes. SDK should default both gracefully.
        val responses = MockResponses().also {
            it.enqueue {
                respondJson(
                    """
                    {
                      "event": {"eventUUID":"e","name":"Show","startDate":"2026-06-01T20:00:00Z"},
                      "ticketTypes": []
                    }
                    """,
                )
            }
        }
        val resp = mockClient(responses).events.inventory("e")
        val snap = resp.body!!
        assertThat(snap.event.rta).isEqualTo(null)
        assertThat(snap.registrationTicketTypes).isEqualTo(emptyList())
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
