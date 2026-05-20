package life.pilot.partner.sdk.http

import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json
import life.pilot.partner.sdk.error.PartnerException
import life.pilot.partner.sdk.model.ErrorResponse

internal object ErrorMapping {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun toException(response: HttpResponse, rawBody: String?): PartnerException {
        val status = response.status.value
        val body: ErrorResponse? = rawBody
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { lenientJson.decodeFromString(ErrorResponse.serializer(), it) }.getOrNull() }

        val message = body?.message ?: "HTTP $status"
        return when (status) {
            401 -> when (body?.code) {
                "INVALID_ORGANIZATION" -> PartnerException.InvalidOrganization(message, body)
                else -> PartnerException.InvalidApiKey(message, body)
            }
            402 -> when (body?.code) {
                "AMOUNT_MISMATCH" -> PartnerException.AmountMismatch(message, body)
                else -> PartnerException.PaymentVerificationFailed(
                    message,
                    reason = body?.reason,
                    partnerReason = body?.partnerReason,
                    body = body,
                )
            }
            403 -> PartnerException.Unauthorized(message, body)
            404 -> PartnerException.NotFound(message, body)
            409 -> when (body?.code) {
                "SOLD_OUT" -> PartnerException.SoldOut(message, body.ticketTypeUUID, body)
                "IDEMPOTENCY_IN_PROGRESS" -> PartnerException.IdempotencyInProgress(message, body)
                else -> PartnerException.IdempotencyConflict(message, body)
            }
            410 -> PartnerException.ClaimExpired(
                message,
                status = body?.status,
                ticketTypeUUID = body?.ticketTypeUUID,
                body = body,
            )
            429 -> PartnerException.RateLimited(
                message,
                retryAfterSeconds = body?.retryAfterSeconds
                    ?: response.headers["Retry-After"]?.toIntOrNull(),
                body = body,
            )
            502 -> PartnerException.PartnerHostRejected(message, body?.reason, body)
            503 -> PartnerException.PosVerificationNotConfigured(message, body)
            504 -> PartnerException.PosVerificationTimeout(message, body)
            else -> PartnerException.Server(status, body?.code ?: "UNKNOWN", message, body)
        }
    }
}
