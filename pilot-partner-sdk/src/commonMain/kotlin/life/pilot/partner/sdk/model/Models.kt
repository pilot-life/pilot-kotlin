package life.pilot.partner.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class EventListItem(
    val eventUUID: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val venueName: String? = null,
    /**
     * Primary event image URL. `null` when the event has no primary image
     * configured. Inherited by [EventDetail] via openapi `allOf`.
     */
    val imageUrl: String? = null,
)

@Serializable
data class EventList(
    val events: List<EventListItem>,
    val nextCursor: String? = null,
)

@Serializable
data class EventDetail(
    val eventUUID: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val venueName: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val shortDescription: String? = null,
    /**
     * Request-to-Attend metadata. `null` on older deployments that don't
     * yet emit it. When present and `enabled == true`, partners should
     * render an RTA submission form. See openapi RtaInfo schema.
     */
    val rta: RtaInfo? = null,
)

@Serializable
data class RtaInfo(
    val enabled: Boolean,
    val banner: String? = null,
    val showBanner: Boolean = false,
    val additionalGuestLimit: Int = 0,
    val enableAdditionalGuest: Boolean = false,
    val designers: List<RtaOption> = emptyList(),
    val occupations: List<RtaOption> = emptyList(),
    val socialMedia: List<RtaSocialMediaOption> = emptyList(),
)

@Serializable
data class RtaOption(val id: Int, val name: String)

@Serializable
data class RtaSocialMediaOption(val id: Int, val name: String, val url: String)

@Serializable
data class TicketTypeRow(
    val ticketTypeUUID: String,
    val name: String,
    val description: String? = null,
    val price: String,
    val totalCapacity: Int,
    val remaining: Int,
    val soldOut: Boolean,
    val availableFrom: String,
    val availableTo: String,
)

@Serializable
data class InventoryEvent(
    val eventUUID: String,
    val name: String,
    val startDate: String,
    /** Request-to-Attend metadata; see [RtaInfo]. */
    val rta: RtaInfo? = null,
)

@Serializable
data class InventorySnapshot(
    val event: InventoryEvent,
    val ticketTypes: List<TicketTypeRow>,
    /**
     * Registration ticket types (separate from purchasable [ticketTypes]).
     * Lifecycle differs — partners pass `ticketTypeUUID` to
     * `POST /events/{eventUuid}/registrations`, not `/claims`. Empty
     * on events that don't accept registrations or on older deployments
     * that don't yet emit this field.
     */
    val registrationTicketTypes: List<TicketTypeRow> = emptyList(),
)

@Serializable
data class ClaimItemRequest(
    val ticketTypeUUID: String,
    val quantity: Int,
)

@Serializable
data class ClaimCreateRequest(
    val items: List<ClaimItemRequest>,
)

@Serializable
data class ClaimCreateResponse(
    val claimId: String,
    val claimIds: List<String>,
    val expiresAt: String,
    val items: List<ClaimResponseItem> = emptyList(),
)

@Serializable
data class ClaimResponseItem(
    val ticketTypeUUID: String? = null,
    val quantity: Int? = null,
    val claimId: String? = null,
)

@Serializable
data class ClaimStatus(
    val status: String,
    val expiresAt: String? = null,
    val remainingSeconds: Int? = null,
)

@Serializable
data class CheckoutPatron(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val externalRef: String? = null,
)

@Serializable
data class CheckoutPayment(
    val paymentId: String,
    val claimedAmount: String,
)

@Serializable
data class CheckoutRequest(
    val patron: CheckoutPatron,
    val payment: CheckoutPayment? = null,
    val additionalClaimIds: List<String>? = null,
)

@Serializable
data class CheckoutResponsePatron(
    val userUUID: String,
)

@Serializable
data class CheckoutResponse(
    val orderUUID: String,
    val orderStatus: String,
    val totalAmount: String,
    val patron: CheckoutResponsePatron,
)

@Serializable
data class OrderEvent(
    val eventUUID: String,
    val name: String,
    val startDate: String,
)

@Serializable
data class OrderPayment(
    val partnerPaymentId: String,
    val totalAmount: String,
)

@Serializable
data class OrderTicket(
    val ticketUUID: String? = null,
    val ticketTypeUUID: String? = null,
    val ticketTypeName: String? = null,
    val price: String? = null,
    val qrContent: String? = null,
)

@Serializable
data class OrderDetail(
    val orderUUID: String,
    val orderStatus: String,
    val totalAmount: String,
    val createdAt: String,
    val event: OrderEvent,
    val payment: OrderPayment? = null,
    val tickets: List<OrderTicket> = emptyList(),
)

@Serializable
data class Health(
    val ok: Boolean,
    val version: String,
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: kotlinx.serialization.json.JsonElement? = null,
    val retryAfterSeconds: Int? = null,
    val status: String? = null,
    val ticketTypeUUID: String? = null,
    val reason: String? = null,
    val partnerReason: String? = null,
)
