package life.pilot.partner.sdk.webhooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses a webhook body (`{ eventId, eventType, createdAt, data }`) into a
 * typed [WebhookPayload]. Unknown event types throw — partners should ignore
 * them; this never delivers a silent miss.
 */
class WebhookParser(
    private val json: Json = DEFAULT_JSON,
) {
    fun parse(rawBody: String): WebhookPayload {
        val obj = json.parseToJsonElement(rawBody) as JsonObject
        val type = (obj["eventType"] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Webhook body missing eventType")
        val eventId = (obj["eventId"] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Webhook body missing eventId")
        val createdAt = (obj["createdAt"] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Webhook body missing createdAt")
        val data = obj["data"] ?: throw IllegalArgumentException("Webhook body missing data")

        return when (type) {
            "inventory.delta" -> WebhookPayload.InventoryDelta(
                eventId, createdAt,
                json.decodeFromJsonElement(WebhookInventoryDeltaData.serializer(), data),
            )
            "hold.expired" -> WebhookPayload.HoldExpired(
                eventId, createdAt,
                json.decodeFromJsonElement(WebhookHoldExpiredData.serializer(), data),
            )
            "order.created" -> WebhookPayload.OrderCreated(
                eventId, createdAt,
                json.decodeFromJsonElement(WebhookOrderCreatedData.serializer(), data),
            )
            else -> throw IllegalArgumentException("Unknown webhook eventType: $type")
        }
    }

    companion object {
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
