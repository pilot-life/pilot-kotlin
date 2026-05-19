package life.pilot.partner.sdk.webhooks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebhookInventoryDeltaItem(
    val ticketTypeUUID: String,
    val available: Int,
    val capacity: Int,
)

@Serializable
data class WebhookInventoryDeltaData(
    val eventUuid: String,
    val ticketTypes: List<WebhookInventoryDeltaItem>,
    val occurredAt: String,
    val reason: String? = null,
)

@Serializable
data class WebhookHoldExpiredData(
    val claimToken: String,
    val ticketTypeUUID: String,
    val quantity: Int,
    val expiredAt: String,
)

@Serializable
data class WebhookOrderCreatedItem(
    val ticketTypeUUID: String,
    val quantity: Int,
)

@Serializable
data class WebhookOrderCreatedPatron(
    val userUuid: String,
    val emailHash: String? = null,
    val phoneHash: String? = null,
)

@Serializable
data class WebhookOrderCreatedData(
    val orderUUID: String,
    val eventUuid: String,
    val total: String? = null,
    val items: List<WebhookOrderCreatedItem>,
    val patron: WebhookOrderCreatedPatron,
    val occurredAt: String,
)

/**
 * Generic envelope partners receive over the wire. Use [WebhookParser] to
 * decode into a typed [WebhookPayload].
 */
@Serializable
data class WebhookEnvelopeRaw(
    val eventId: String,
    val eventType: String,
    val createdAt: String,
    val data: JsonElement,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@JsonClassDiscriminator("eventType")
@Serializable
sealed class WebhookPayload {
    abstract val eventId: String
    abstract val createdAt: String

    @Serializable
    @SerialName("inventory.delta")
    data class InventoryDelta(
        override val eventId: String,
        override val createdAt: String,
        val data: WebhookInventoryDeltaData,
    ) : WebhookPayload()

    @Serializable
    @SerialName("hold.expired")
    data class HoldExpired(
        override val eventId: String,
        override val createdAt: String,
        val data: WebhookHoldExpiredData,
    ) : WebhookPayload()

    @Serializable
    @SerialName("order.created")
    data class OrderCreated(
        override val eventId: String,
        override val createdAt: String,
        val data: WebhookOrderCreatedData,
    ) : WebhookPayload()
}
