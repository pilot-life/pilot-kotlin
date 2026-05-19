package life.pilot.partner.sdk.error

import life.pilot.partner.sdk.model.ErrorResponse
import java.io.IOException

/**
 * Typed exception for all partner-API failures.
 *
 * `code` mirrors the `ErrorResponse.code` field documented in openapi.yaml.
 * Common values: INVALID_API_KEY, INVALID_ORGANIZATION, NOT_FOUND, SOLD_OUT,
 * RATE_LIMITED, IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS, CLAIM_EXPIRED,
 * PAYMENT_VERIFICATION_FAILED, AMOUNT_MISMATCH, PARTNER_HOST_REJECTED.
 *
 * Use the typed subclasses for the failures that need branching.
 */
sealed class PartnerException(
    val httpStatus: Int,
    val code: String,
    message: String,
    val body: ErrorResponse? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {

    class InvalidApiKey(message: String, body: ErrorResponse?) :
        PartnerException(401, "INVALID_API_KEY", message, body)

    class InvalidOrganization(message: String, body: ErrorResponse?) :
        PartnerException(401, "INVALID_ORGANIZATION", message, body)

    class Unauthorized(message: String, body: ErrorResponse?) :
        PartnerException(403, "INVALID_UNAUTHORIZED_ACCESS", message, body)

    class NotFound(message: String, body: ErrorResponse?) :
        PartnerException(404, "NOT_FOUND", message, body)

    class SoldOut(message: String, val ticketTypeUUID: String?, body: ErrorResponse?) :
        PartnerException(409, "SOLD_OUT", message, body)

    class IdempotencyConflict(message: String, body: ErrorResponse?) :
        PartnerException(409, "IDEMPOTENCY_CONFLICT", message, body)

    class IdempotencyInProgress(message: String, body: ErrorResponse?) :
        PartnerException(409, "IDEMPOTENCY_IN_PROGRESS", message, body)

    class ClaimExpired(
        message: String,
        val status: String?,
        val ticketTypeUUID: String?,
        body: ErrorResponse?,
    ) : PartnerException(410, "CLAIM_EXPIRED", message, body)

    class PaymentVerificationFailed(
        message: String,
        val reason: String?,
        val partnerReason: String?,
        body: ErrorResponse?,
    ) : PartnerException(402, "PAYMENT_VERIFICATION_FAILED", message, body)

    class AmountMismatch(message: String, body: ErrorResponse?) :
        PartnerException(402, "AMOUNT_MISMATCH", message, body)

    class RateLimited(
        message: String,
        val retryAfterSeconds: Int?,
        body: ErrorResponse?,
    ) : PartnerException(429, "RATE_LIMITED", message, body)

    class PartnerHostRejected(message: String, val reason: String?, body: ErrorResponse?) :
        PartnerException(502, "PARTNER_HOST_REJECTED", message, body)

    class PosVerificationNotConfigured(message: String, body: ErrorResponse?) :
        PartnerException(503, "POS_VERIFICATION_NOT_CONFIGURED", message, body)

    class PosVerificationTimeout(message: String, body: ErrorResponse?) :
        PartnerException(504, "POS_VERIFICATION_TIMEOUT", message, body)

    class Server(httpStatus: Int, code: String, message: String, body: ErrorResponse?) :
        PartnerException(httpStatus, code, message, body)

    class Network(message: String, cause: Throwable) :
        PartnerException(0, "NETWORK_ERROR", message, null, cause)
}
