package life.pilot.partner.sdk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import life.pilot.partner.sdk.webhooks.HmacVerifier
import life.pilot.partner.sdk.webhooks.WebhookParser
import life.pilot.partner.sdk.webhooks.WebhookPayload
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WebhookTest {

    @Test fun `parses order_created envelope`() {
        val body = """
            {
              "eventId": "11111111-1111-1111-1111-111111111111",
              "eventType": "order.created",
              "createdAt": "2026-05-19T10:00:00Z",
              "data": {
                "orderUUID": "22222222-2222-2222-2222-222222222222",
                "eventUuid": "33333333-3333-3333-3333-333333333333",
                "total": "65.00",
                "items": [{"ticketTypeUUID":"tt-1","quantity":2}],
                "patron": {"userUuid":"44444444-4444-4444-4444-444444444444","emailHash":null,"phoneHash":null},
                "occurredAt": "2026-05-19T09:59:55Z"
              }
            }
        """.trimIndent()

        val parsed = WebhookParser().parse(body)
        assertThat(parsed).isInstanceOf(WebhookPayload.OrderCreated::class)
        val oc = parsed as WebhookPayload.OrderCreated
        assertThat(oc.data.orderUUID).isEqualTo("22222222-2222-2222-2222-222222222222")
        assertThat(oc.data.items[0].quantity).isEqualTo(2)
    }

    @Test fun `parses inventory_delta and hold_expired`() {
        val invDelta = WebhookParser().parse(
            """
            {"eventId":"e1","eventType":"inventory.delta","createdAt":"2026-05-19T10:00:00Z",
             "data":{"eventUuid":"u","occurredAt":"t","ticketTypes":[{"ticketTypeUUID":"tt","available":5,"capacity":10}]}}
            """.trimIndent(),
        )
        assertThat(invDelta).isInstanceOf(WebhookPayload.InventoryDelta::class)

        val holdExp = WebhookParser().parse(
            """
            {"eventId":"e2","eventType":"hold.expired","createdAt":"2026-05-19T10:00:00Z",
             "data":{"claimToken":"ct","ticketTypeUUID":"tt","quantity":0,"expiredAt":"t"}}
            """.trimIndent(),
        )
        assertThat(holdExp).isInstanceOf(WebhookPayload.HoldExpired::class)
    }

    @Test fun `unknown event type throws`() {
        assertFailsWith<IllegalArgumentException> {
            WebhookParser().parse(
                """{"eventId":"x","eventType":"weird.thing","createdAt":"t","data":{}}""",
            )
        }
    }

    @Test fun `HmacVerifier accepts a valid signature within tolerance`() {
        val secret = "whsec_test"
        val now = 1_700_000_000L
        val verifier = HmacVerifier(secret = secret, clock = { now })
        val body = """{"eventId":"x"}"""
        val sig = "t=$now,v1=" + verifier.sign("$now.$body")

        assertThat(verifier.verify(body, sig)).isTrue()
    }

    @Test fun `HmacVerifier rejects altered body`() {
        val secret = "whsec_test"
        val now = 1_700_000_000L
        val verifier = HmacVerifier(secret = secret, clock = { now })
        val sig = "t=$now,v1=" + verifier.sign("$now.original")

        assertThat(verifier.verify("tampered", sig)).isFalse()
    }

    @Test fun `HmacVerifier rejects stale timestamp`() {
        val secret = "whsec_test"
        val now = 1_700_000_000L
        val stale = now - 10_000
        val verifier = HmacVerifier(secret = secret, toleranceSeconds = 300, clock = { now })
        val body = "{}"
        val sig = "t=$stale,v1=" + verifier.sign("$stale.$body")

        assertThat(verifier.verify(body, sig)).isFalse()
    }

    @Test fun `HmacVerifier rejects malformed header`() {
        val verifier = HmacVerifier(secret = "s", clock = { 0L })
        assertThat(verifier.verify("{}", null)).isFalse()
        assertThat(verifier.verify("{}", "")).isFalse()
        assertThat(verifier.verify("{}", "v1=abc")).isFalse()
        assertThat(verifier.verify("{}", "t=notanumber,v1=abc")).isFalse()
    }
}
